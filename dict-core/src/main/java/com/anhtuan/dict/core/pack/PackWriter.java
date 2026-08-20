package com.anhtuan.dict.core.pack;

import com.anhtuan.dict.core.model.Entry;
import java.nio.file.Path;
import java.util.List;

/**
 * TODO(M2) - Ghi dict.pack theo dac ta PLAN.md muc 6.
 *
 * Trinh tu bat buoc:
 *   1. sap xep entries theo headwordNorm bang Utf8Compare.COMPARATOR
 *      (KHONG duoc dung String::compareTo - xem PLAN.md muc 12 rui ro so 2)
 *   2. chia thanh block PackFormat.ENTRIES_PER_BLOCK entry
 *   3. serialize moi block theo dac ta 6.1 roi BlockCodec.compress
 *   4. ghi KEYS, ENTRY_PTRS, BLOCK_DIR, BLOCKS
 *   5. quay lai ghi HEADER voi cac offset da biet
 *
 * Nghiem thu: dict.pack &lt;= 5,2 MB va round-trip tra dung 108.853 entry.
 */
public final class PackWriter {

    private PackWriter() {}

    public static void write(Path target, List<Entry> entries) {
        throw new UnsupportedOperationException("TODO(M2): xem PLAN.md muc 6");
    }
}
