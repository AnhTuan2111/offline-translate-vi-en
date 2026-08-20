package com.anhtuan.dict.core.index;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;

/** TODO(M3) - Doc inverted index bang mmap, giai varint postings (PLAN.md 7.1). */
public final class InvertedIndex implements Closeable {

    public static InvertedIndex open(Path idxFile) {
        throw new UnsupportedOperationException("TODO(M3)");
    }

    /** Danh sach cap (docId, tf) cua mot term. Rong neu term khong ton tai. */
    public List<int[]> postings(String term) {
        throw new UnsupportedOperationException("TODO(M3)");
    }

    public int docFreq(String term) {
        throw new UnsupportedOperationException("TODO(M3)");
    }

    public int docCount() {
        throw new UnsupportedOperationException("TODO(M3)");
    }

    public double avgDocLength() {
        throw new UnsupportedOperationException("TODO(M3)");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("TODO(M3)");
    }
}
