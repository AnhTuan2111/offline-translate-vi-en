package com.anhtuan.dict.core.pack;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Idiom;
import com.anhtuan.dict.core.nlp.TextNormalizer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ghi dict.pack theo dac ta PLAN.md muc 6.
 *
 * <p>Trinh tu:
 * <ol>
 *   <li>sap xep entry theo headwordNorm bang {@link Utf8Compare#COMPARATOR}
 *       (KHONG dung String::compareTo - PLAN.md muc 12 rui ro so 2)</li>
 *   <li>sinh KHOA BI DANH cho cum thanh ngu (xem {@link #collectKeys})</li>
 *   <li>chia entry thanh block 64 cai, nen tung block bang Deflate</li>
 *   <li>ghi HEADER, KEYS, ENTRY_PTRS, BLOCK_DIR, BLOCKS</li>
 * </ol>
 *
 * <p>Tat ca offset duoc tinh TRUOC khi ghi, nen file ghi mot luot tu dau den cuoi
 * khong can seek lai.
 */
public final class PackWriter {

    private PackWriter() {}

    /** Mot khoa trong KEYS, tro ve entry thu {@code ordinal} (sau khi da sap xep). */
    private record KeyPtr(String key, byte[] keyBytes, int ordinal) {}

    public static Stats write(Path target, List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        // Sort on dinh: hai entry cung headwordNorm (dong am) giu nguyen thu tu file nguon.
        sorted.sort(Comparator.comparing(Entry::headwordNorm, Utf8Compare.COMPARATOR));

        List<KeyPtr> keys = collectKeys(sorted);
        keys.sort((a, b) -> Utf8Compare.compare(a.keyBytes(), b.keyBytes()));

        // --- KEYS: cac khoa noi tiep nhau, moi khoa ket thuc bang 0x00 ---
        int keysLength = 0;
        for (KeyPtr k : keys) keysLength += k.keyBytes().length + 1;
        byte[] keysRegion = new byte[keysLength];
        int[] keyOffsets = new int[keys.size()];
        int pos = 0;
        for (int i = 0; i < keys.size(); i++) {
            byte[] kb = keys.get(i).keyBytes();
            keyOffsets[i] = pos;
            System.arraycopy(kb, 0, keysRegion, pos, kb.length);
            pos += kb.length + 1;                   // byte ket thuc 0x00: mang da khoi tao san bang 0
        }

        // --- BLOCKS: nen tung nhom 64 entry ---
        int entryCount = sorted.size();
        int blockCount = (entryCount + PackFormat.ENTRIES_PER_BLOCK - 1) / PackFormat.ENTRIES_PER_BLOCK;
        List<byte[]> compressedBlocks = new ArrayList<>(blockCount);
        int[] rawLengths = new int[blockCount];
        long rawTotal = 0;
        for (int b = 0; b < blockCount; b++) {
            int from = b * PackFormat.ENTRIES_PER_BLOCK;
            int to = Math.min(from + PackFormat.ENTRIES_PER_BLOCK, entryCount);
            ByteArrayOutputStream raw = new ByteArrayOutputStream(16 * 1024);
            for (int i = from; i < to; i++) EntryCodec.writeEntry(raw, sorted.get(i));
            byte[] rawBytes = raw.toByteArray();
            rawLengths[b] = rawBytes.length;
            rawTotal += rawBytes.length;
            compressedBlocks.add(BlockCodec.compress(rawBytes));
        }

        // --- Offset: tinh truoc het, khong seek ---
        long keysOffset = PackFormat.HEADER_SIZE;
        long entryPtrOffset = keysOffset + keysLength;
        long blockDirOffset = entryPtrOffset + (long) keys.size() * PackFormat.ENTRY_PTR_SIZE;
        long dataOffset = blockDirOffset + (long) blockCount * PackFormat.BLOCK_DIR_SIZE;

        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target), 1 << 16)) {
                out.write(header(entryCount, blockCount, keys.size(),
                        keysOffset, keysLength, entryPtrOffset, blockDirOffset, dataOffset));
                out.write(keysRegion);

                ByteBuffer ptrs = le(keys.size() * PackFormat.ENTRY_PTR_SIZE);
                for (int i = 0; i < keys.size(); i++) {
                    int ordinal = keys.get(i).ordinal();
                    ptrs.putInt(keyOffsets[i]);
                    ptrs.putInt(ordinal / PackFormat.ENTRIES_PER_BLOCK);
                    ptrs.putInt(ordinal % PackFormat.ENTRIES_PER_BLOCK);
                }
                out.write(ptrs.array());

                ByteBuffer dir = le(blockCount * PackFormat.BLOCK_DIR_SIZE);
                long fileOffset = dataOffset;
                for (int b = 0; b < blockCount; b++) {
                    dir.putLong(fileOffset);
                    dir.putInt(compressedBlocks.get(b).length);
                    dir.putInt(rawLengths[b]);
                    fileOffset += compressedBlocks.get(b).length;
                }
                out.write(dir.array());

                for (byte[] block : compressedBlocks) out.write(block);
            }
            return new Stats(entryCount, keys.size(), blockCount, Files.size(target), rawTotal);
        } catch (IOException e) {
            throw new UncheckedIOException("khong ghi duoc " + target, e);
        }
    }

    /**
     * Sinh toan bo khoa tra cuu: headword + KHOA BI DANH cua thanh ngu
     * (PLAN.md muc 3, phan "cum dong tu nam o dau").
     *
     * <p>Trong nguon anhviet109K, "give up" KHONG phai headword - no chi ton tai duoi
     * dang dong "!to give up" ben trong entry "@give". Neu chi dua headword vao KEYS
     * thi PhraseProbe truot 7.944 cum. Vi vay moi cum thanh ngu duoc them thanh khoa
     * bi danh tro ve entry cha, kem bien the bo tien to "to ".
     *
     * <p>Uu tien: headword thuc luon thang khoa bi danh. Giua cac bi danh trung nhau,
     * cai cua entry dung truoc theo thu tu alphabet thang.
     */
    private static List<KeyPtr> collectKeys(List<Entry> sorted) {
        Set<String> primary = new HashSet<>(sorted.size() * 2);
        for (Entry e : sorted) primary.add(e.headwordNorm());

        List<KeyPtr> keys = new ArrayList<>(sorted.size() * 2);
        for (int i = 0; i < sorted.size(); i++) {
            String k = sorted.get(i).headwordNorm();
            if (!k.isEmpty()) keys.add(newKey(k, i));
        }

        // Hai luot, va thu tu nay quan trong. Cum "to give up" xuat hien trong CA HAI entry
        // "@gave" va "@give" (nguon lap lai thanh ngu o ca dang qua khu). Neu chi quet mot
        // luot thi "@gave" thang vi dung truoc theo alphabet, va nguoi dung tra "give up"
        // se roi vao muc tu qua khu. Luot 1 chi nhan bi danh cua entry la CHINH TU MO DAU cum.
        Set<String> aliasSeen = new HashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < sorted.size(); i++) {
                Entry e = sorted.get(i);
                for (Idiom idiom : e.idioms()) {
                    String k = TextNormalizer.normalizeHeadword(idiom.phrase());
                    String bare = k.startsWith("to ") ? k.substring(3) : k;
                    if (pass == 0 && !firstWord(bare).equals(e.headwordNorm())) continue;
                    addAlias(keys, primary, aliasSeen, k, i);
                    if (!bare.equals(k)) addAlias(keys, primary, aliasSeen, bare, i);
                }
            }
        }
        return keys;
    }

    private static void addAlias(List<KeyPtr> keys, Set<String> primary, Set<String> seen,
                                 String key, int ordinal) {
        if (key.isEmpty() || primary.contains(key) || !seen.add(key)) return;
        keys.add(newKey(key, ordinal));
    }

    private static String firstWord(String phrase) {
        int sp = phrase.indexOf(' ');
        return sp < 0 ? phrase : phrase.substring(0, sp);
    }

    private static KeyPtr newKey(String key, int ordinal) {
        return new KeyPtr(key, key.getBytes(StandardCharsets.UTF_8), ordinal);
    }

    private static byte[] header(int entryCount, int blockCount, int keyCount,
                                 long keysOffset, long keysLength, long entryPtrOffset,
                                 long blockDirOffset, long dataOffset) {
        ByteBuffer h = le(PackFormat.HEADER_SIZE);
        h.put(PackFormat.MAGIC);
        h.putInt(PackFormat.OFF_FORMAT_VERSION, PackFormat.FORMAT_VERSION);
        h.putInt(PackFormat.OFF_FLAGS, PackFormat.FLAG_COMPRESSED);
        h.putInt(PackFormat.OFF_ENTRY_COUNT, entryCount);
        h.putInt(PackFormat.OFF_BLOCK_COUNT, blockCount);
        h.putInt(PackFormat.OFF_KEY_COUNT, keyCount);
        h.putLong(PackFormat.OFF_KEYS_OFFSET, keysOffset);
        h.putLong(PackFormat.OFF_KEYS_LENGTH, keysLength);
        h.putLong(PackFormat.OFF_ENTRY_PTR_OFFSET, entryPtrOffset);
        h.putLong(PackFormat.OFF_BLOCK_DIR_OFFSET, blockDirOffset);
        h.putLong(PackFormat.OFF_DATA_OFFSET, dataOffset);
        return h.array();
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** So lieu de in ra CLI va doi chieu tieu chi nghiem thu M2. */
    public record Stats(int entryCount, int keyCount, int blockCount,
                        long fileSize, long rawSize) {
        public double compressionRatio() {
            return rawSize == 0 ? 0 : (double) fileSize / rawSize;
        }
    }
}
