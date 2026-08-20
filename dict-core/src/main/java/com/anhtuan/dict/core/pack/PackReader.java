package com.anhtuan.dict.core.pack;

import com.anhtuan.dict.core.model.Entry;
import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * TODO(M2) - Doc dict.pack bang mmap, tra cuu bang binary search (PLAN.md 6.2).
 *
 * Diem mau chot ve hieu nang:
 *   - FileChannel.map(READ_ONLY): OS lo page cache, heap gan nhu bang 0
 *   - binary search TREN BYTE bang Utf8Compare.compareAt, khong tao String trong vong lap
 *   - LRU cache PackFormat.BLOCK_CACHE_SIZE block da giai nen (~600 KB)
 *   - PHAI thread-safe: UI thread va ClipboardWatcher cung goi
 *
 * Nghiem thu: tra 1 tu &lt; 1 ms, heap sau khi mo pack &lt; 5 MB.
 */
public final class PackReader implements Closeable {

    public static PackReader open(Path packFile) {
        throw new UnsupportedOperationException("TODO(M2): xem PLAN.md muc 6.2");
    }

    /** Tra cuu chinh xac theo khoa da chuan hoa. */
    public Optional<Entry> lookup(String headwordNorm) {
        throw new UnsupportedOperationException("TODO(M2)");
    }

    /** Kiem tra ton tai ma KHONG giai nen block - duong nong cua PhraseProbe (PLAN.md 8.1). */
    public boolean contains(String headwordNorm) {
        throw new UnsupportedOperationException("TODO(M2)");
    }

    /** Cac headword bat dau bang tien to - dung cho goi y khi go. */
    public List<String> prefixScan(String prefix, int limit) {
        throw new UnsupportedOperationException("TODO(M2)");
    }

    public int entryCount() {
        throw new UnsupportedOperationException("TODO(M2)");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("TODO(M2)");
    }
}
