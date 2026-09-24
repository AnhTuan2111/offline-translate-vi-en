package com.anhtuan.dict.core.index;

import com.anhtuan.dict.core.pack.Utf8Compare;
import com.anhtuan.dict.core.pack.VarInt;

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
import java.util.List;

/**
 * Doc file index bang mmap, giai varint postings (PLAN.md 7.1).
 *
 * <p>Dung chung cho ca ba file {@code vi.idx}, {@code vi-nodiac.idx}, {@code tri.idx} -
 * chung cung mot dinh dang, chi khac cach sinh term.
 *
 * <p>Cung ly do dung {@link Arena} nhu PackReader: unmap ngay khi close, khong de
 * MappedByteBuffer khoa file tren Windows.
 */
public final class InvertedIndex implements Closeable {

    private final Arena arena;
    private final ByteBuffer buf;

    private final int termCount;
    private final int docCount;
    private final float avgDocLen;
    private final int termsBase;
    private final int termPtrBase;
    private final int docLenBase;

    private InvertedIndex(Arena arena, ByteBuffer buf) {
        this.arena = arena;
        this.buf = buf;

        byte[] magic = new byte[IndexFormat.MAGIC.length];
        for (int i = 0; i < magic.length; i++) magic[i] = buf.get(i);
        if (!Arrays.equals(magic, IndexFormat.MAGIC)) {
            throw new IllegalStateException("khong phai file index (magic sai)");
        }
        this.termCount = buf.getInt(IndexFormat.OFF_TERM_COUNT);
        this.docCount = buf.getInt(IndexFormat.OFF_DOC_COUNT);
        this.avgDocLen = buf.getFloat(IndexFormat.OFF_AVG_DOC_LEN);
        this.termsBase = (int) buf.getLong(IndexFormat.OFF_TERMS_OFFSET);
        this.termPtrBase = (int) buf.getLong(IndexFormat.OFF_TERM_PTR_OFFSET);
        this.docLenBase = (int) buf.getLong(IndexFormat.OFF_DOC_LEN_OFFSET);
    }

    public static InvertedIndex open(Path idxFile) {
        Arena arena = Arena.ofShared();
        try (FileChannel ch = FileChannel.open(idxFile, StandardOpenOption.READ)) {
            MemorySegment seg = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size(), arena);
            return new InvertedIndex(arena, seg.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN));
        } catch (IOException e) {
            arena.close();
            throw new UncheckedIOException("khong mo duoc " + idxFile, e);
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /** Danh sach cap {docId, tf} cua mot term. Rong neu term khong ton tai. */
    public List<int[]> postings(String term) {
        int i = indexOfTerm(term);
        if (i < 0) return List.of();

        int base = termPtrBase + i * IndexFormat.TERM_PTR_SIZE;
        int docFreq = buf.getInt(base + 4);
        long postingOffset = buf.getLong(base + 8);

        ByteBuffer dup = buf.duplicate();               // rieng cho luot doc nay -> thread-safe
        dup.position((int) postingOffset);
        List<int[]> out = new ArrayList<>(docFreq);
        int docId = 0;
        for (int k = 0; k < docFreq; k++) {
            int packed = VarInt.read(dup);
            docId += packed >>> 1;                      // delta -> tuyet doi
            int tf = (packed & 1) == 0 ? 1 : VarInt.read(dup);
            out.add(new int[] {docId, tf});
        }
        return out;
    }

    public int docFreq(String term) {
        int i = indexOfTerm(term);
        return i < 0 ? 0 : buf.getInt(termPtrBase + i * IndexFormat.TERM_PTR_SIZE + 4);
    }

    /** Do dai tai lieu {@code docId}, tinh bang so token. Mau so cua BM25. */
    public int docLength(int docId) {
        if (docId < 0 || docId >= docCount) return 0;
        return buf.getShort(docLenBase + docId * 2) & 0xFFFF;
    }

    public int termCount() {
        return termCount;
    }

    public int docCount() {
        return docCount;
    }

    public double avgDocLength() {
        return avgDocLen;
    }

    private int indexOfTerm(String term) {
        if (term == null || term.isEmpty()) return -1;
        byte[] target = term.getBytes(StandardCharsets.UTF_8);
        int lo = 0, hi = termCount - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int termOffset = buf.getInt(termPtrBase + mid * IndexFormat.TERM_PTR_SIZE);
            int cmp = Utf8Compare.compareAt(buf, termsBase + termOffset, target);
            if (cmp < 0) lo = mid + 1;
            else if (cmp > 0) hi = mid - 1;
            else return mid;
        }
        return -(lo + 1);
    }

    @Override
    public void close() {
        arena.close();
    }
}
