package com.anhtuan.dict.core.index;

import com.anhtuan.dict.core.model.Entry;
import java.nio.file.Path;
import java.util.List;

/**
 * TODO(M3) - Dung inverted index Viet-&gt;Anh (PLAN.md 7.1).
 *
 * Phai sinh HAI index:
 *   vi.idx        - term co dau
 *   vi-nodiac.idx - term da bo dau (TextNormalizer.removeDiacritics)
 * Thieu index khong dau la app "cam giac ngu" ngay voi nguoi go nhanh.
 *
 * Postings dung delta + varint: luu HIEU so voi docId truoc, khong luu so tuyet doi.
 * Da do: 1.233.486 luot token -&gt; ~1,5 MB.
 */
public final class IndexWriter {
    private IndexWriter() {}

    public static void build(Path targetDir, List<Entry> entries) {
        throw new UnsupportedOperationException("TODO(M3): xem PLAN.md muc 7.1");
    }
}
