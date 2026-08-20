package com.anhtuan.dict.core.pack;

import java.nio.charset.StandardCharsets;

/** Hang so dinh dang dict.pack. Doi chieu dac ta o PLAN.md muc 6. */
public final class PackFormat {
    private PackFormat() {}

    public static final byte[] MAGIC = "VIENDICT".getBytes(StandardCharsets.US_ASCII);
    public static final int FORMAT_VERSION = 1;
    public static final int HEADER_SIZE = 64;

    /** So entry moi block. Nho -> giai nen nhanh; lon -> nen tot hon. 64 la diem can bang da do. */
    public static final int ENTRIES_PER_BLOCK = 64;

    public static final int ENTRY_PTR_SIZE = 12;   // keyOffset:i32 + blockId:i32 + indexInBlock:i32
    public static final int BLOCK_DIR_SIZE = 16;   // fileOffset:i64 + compressedLen:i32 + rawLen:i32

    public static final int FLAG_COMPRESSED = 1;

    /** So block giu trong LRU cache. 64 x ~9 KB = ~600 KB (PLAN.md 6.3). */
    public static final int BLOCK_CACHE_SIZE = 64;
}
