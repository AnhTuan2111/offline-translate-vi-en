package com.anhtuan.dict.core.pack;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.nlp.TextNormalizer;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Doc dict.pack bang mmap, tra cuu bang binary search (PLAN.md 6.2).
 *
 * <p>Ba diem mau chot ve hieu nang:
 * <ul>
 *   <li>mmap toan file: OS lo page cache, heap gan nhu bang 0 (5 MB file khong nam trong heap)</li>
 *   <li>binary search TREN BYTE bang {@link Utf8Compare#compareAt} - khong tao String
 *       trong vong lap 17 buoc</li>
 *   <li>LRU cache {@value PackFormat#BLOCK_CACHE_SIZE} block da giai nen, luu duoi dang
 *       byte[] tho (~600 KB) chu khong phai Entry[] - cache Entry[] se ngon vai MB heap</li>
 * </ul>
 *
 * <p>Vi sao mmap qua {@link Arena} chu khong qua {@code FileChannel.map(...)} tra
 * MappedByteBuffer: MappedByteBuffer chi duoc giai phong khi GC don, tren Windows
 * dieu do KHOA FILE - build lai dict.pack trong test se that bai. Arena cho phep
 * unmap ngay tai {@link #close()}.
 *
 * <p>Thread-safe: moi phep doc dung absolute get hoac {@code duplicate()} rieng,
 * cache duoc bao bang synchronized. UI thread va ClipboardWatcher goi dong thoi duoc.
 */
public final class PackReader implements Closeable {

    private final Arena arena;
    private final ByteBuffer buf;

    private final int entryCount;
    private final int keyCount;
    private final int blockCount;
    private final int keysBase;
    private final int entryPtrBase;
    private final int blockDirBase;

    /** LRU block da giai nen. Khoa rieng, khong dung chung voi gi khac. */
    private final Map<Integer, byte[]> blockCache;

    private PackReader(Arena arena, ByteBuffer buf) {
        this.arena = arena;
        this.buf = buf;

        byte[] magic = new byte[PackFormat.MAGIC.length];
        for (int i = 0; i < magic.length; i++) magic[i] = buf.get(i);
        if (!Arrays.equals(magic, PackFormat.MAGIC)) {
            throw new IllegalStateException("khong phai file dict.pack (magic sai)");
        }
        int version = buf.getInt(PackFormat.OFF_FORMAT_VERSION);
        if (version != PackFormat.FORMAT_VERSION) {
            throw new IllegalStateException("formatVersion " + version + " khong doc duoc, can "
                    + PackFormat.FORMAT_VERSION);
        }
        this.entryCount = buf.getInt(PackFormat.OFF_ENTRY_COUNT);
        this.blockCount = buf.getInt(PackFormat.OFF_BLOCK_COUNT);
        this.keyCount = buf.getInt(PackFormat.OFF_KEY_COUNT);
        this.keysBase = (int) buf.getLong(PackFormat.OFF_KEYS_OFFSET);
        this.entryPtrBase = (int) buf.getLong(PackFormat.OFF_ENTRY_PTR_OFFSET);
        this.blockDirBase = (int) buf.getLong(PackFormat.OFF_BLOCK_DIR_OFFSET);

        this.blockCache = Collections.synchronizedMap(
                new LinkedHashMap<>(PackFormat.BLOCK_CACHE_SIZE * 2, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
                        return size() > PackFormat.BLOCK_CACHE_SIZE;
                    }
                });
    }

    public static PackReader open(Path packFile) {
        Arena arena = Arena.ofShared();
        try (FileChannel ch = FileChannel.open(packFile, StandardOpenOption.READ)) {
            MemorySegment seg = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size(), arena);
            ByteBuffer buf = seg.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
            return new PackReader(arena, buf);
        } catch (IOException e) {
            arena.close();
            throw new UncheckedIOException("khong mo duoc " + packFile, e);
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    // ------------------------------------------------------------------ tra cuu

    /** Tra cuu chinh xac. Chuoi dau vao duoc chuan hoa san nen goi truc tiep tu UI cung duoc. */
    public Optional<Entry> lookup(String headword) {
        int i = indexOfKey(TextNormalizer.normalizeHeadword(headword));
        return i < 0 ? Optional.empty() : Optional.of(entryOfKey(i));
    }

    /**
     * Tra cuu tra ve TAT CA entry dong am (PLAN.md 4.3: khoa co the trung sau chuan hoa).
     * Vi du "bank" (bo song) va "bank" (ngan hang) la hai entry rieng trong nguon.
     */
    public List<Entry> lookupAll(String headword) {
        String key = TextNormalizer.normalizeHeadword(headword);
        int i = indexOfKey(key);
        if (i < 0) return List.of();
        int lo = i;
        while (lo > 0 && keyAt(lo - 1).equals(key)) lo--;
        int hi = i;
        while (hi + 1 < keyCount && keyAt(hi + 1).equals(key)) hi++;
        List<Entry> out = new ArrayList<>(hi - lo + 1);
        for (int k = lo; k <= hi; k++) out.add(entryOfKey(k));
        return out;
    }

    /**
     * Kiem tra ton tai ma KHONG giai nen block - duong nong cua PhraseProbe (PLAN.md 8.1).
     * Chuoi dau vao phai la khoa DA chuan hoa.
     */
    public boolean contains(String headwordNorm) {
        return indexOfKey(headwordNorm) >= 0;
    }

    /** Cac khoa bat dau bang tien to, dung cho goi y khi dang go. */
    public List<String> prefixScan(String prefix, int limit) {
        String p = TextNormalizer.normalizeHeadword(prefix);
        if (p.isEmpty() || limit <= 0) return List.of();
        int i = indexOfKey(p);
        int start = i >= 0 ? i : -(i + 1);
        List<String> out = new ArrayList<>(Math.min(limit, 32));
        for (int k = start; k < keyCount && out.size() < limit; k++) {
            String key = keyAt(k);
            if (!key.startsWith(p)) break;
            out.add(key);
        }
        return out;
    }

    /** Entry thu {@code ordinal} theo thu tu da sap xep. Dung lam docId cua index (PLAN.md 7.1). */
    public Entry entryAt(int ordinal) {
        if (ordinal < 0 || ordinal >= entryCount) {
            throw new IndexOutOfBoundsException("ordinal " + ordinal + " / " + entryCount);
        }
        return decode(ordinal / PackFormat.ENTRIES_PER_BLOCK, ordinal % PackFormat.ENTRIES_PER_BLOCK);
    }

    /**
     * Cac tu MO DAU cua moi khoa nhieu tu - HashSet toi uu cua PhraseProbe (AD-5).
     *
     * <p>Quet ca vung KEYS mot lan (~1,1 MB, vai chuc ms) thay vi phai sinh them mot
     * file rieng. Tu nao khong nam trong set nay thi khong the mo dau mot cum, bo qua
     * probe hoan toan - cat duoc khoang 90% so lan binary search.
     */
    public Set<String> multiWordStarters() {
        Set<String> starters = new HashSet<>(8192);
        for (int i = 0; i < keyCount; i++) {
            String key = keyAt(i);
            int sp = key.indexOf(' ');
            if (sp > 0) starters.add(key.substring(0, sp));
        }
        return starters;
    }

    public int entryCount() {
        return entryCount;
    }

    public int keyCount() {
        return keyCount;
    }

    public int blockCount() {
        return blockCount;
    }

    // ------------------------------------------------------------------ noi bo

    /** Binary search tren ENTRY_PTRS. Tra ve chi so, hoac -(diem chen)-1 nhu Arrays#binarySearch. */
    private int indexOfKey(String normalizedKey) {
        if (normalizedKey == null || normalizedKey.isEmpty()) return -1;
        byte[] target = normalizedKey.getBytes(StandardCharsets.UTF_8);
        int lo = 0, hi = keyCount - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = Utf8Compare.compareAt(buf, keysBase + keyOffsetOf(mid), target);
            if (cmp < 0) lo = mid + 1;
            else if (cmp > 0) hi = mid - 1;
            else return mid;
        }
        return -(lo + 1);
    }

    private int keyOffsetOf(int keyIndex) {
        return buf.getInt(entryPtrBase + keyIndex * PackFormat.ENTRY_PTR_SIZE);
    }

    private String keyAt(int keyIndex) {
        int at = keysBase + keyOffsetOf(keyIndex);
        int len = 0;
        while (buf.get(at + len) != 0) len++;
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) b[i] = buf.get(at + i);
        return new String(b, StandardCharsets.UTF_8);
    }

    private Entry entryOfKey(int keyIndex) {
        int base = entryPtrBase + keyIndex * PackFormat.ENTRY_PTR_SIZE;
        return decode(buf.getInt(base + 4), buf.getInt(base + 8));
    }

    private Entry decode(int blockId, int indexInBlock) {
        byte[] raw = block(blockId);
        ByteBuffer bb = ByteBuffer.wrap(raw);
        for (int i = 0; i < indexInBlock; i++) EntryCodec.skipEntry(bb);
        return EntryCodec.readEntry(bb);
    }

    private byte[] block(int blockId) {
        byte[] cached = blockCache.get(blockId);
        if (cached != null) return cached;

        int dir = blockDirBase + blockId * PackFormat.BLOCK_DIR_SIZE;
        long fileOffset = buf.getLong(dir);
        int compressedLen = buf.getInt(dir + 8);
        int rawLen = buf.getInt(dir + 12);

        byte[] compressed = new byte[compressedLen];
        ByteBuffer dup = buf.duplicate();               // rieng cho luot doc nay -> thread-safe
        dup.position((int) fileOffset);
        dup.get(compressed);

        byte[] raw = BlockCodec.decompress(compressed, rawLen);
        blockCache.put(blockId, raw);
        return raw;
    }

    @Override
    public void close() {
        blockCache.clear();
        arena.close();                                  // unmap ngay, khong cho GC
    }
}
