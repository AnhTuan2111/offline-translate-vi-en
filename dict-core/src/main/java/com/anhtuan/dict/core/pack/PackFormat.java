package com.anhtuan.dict.core.pack;

import java.nio.charset.StandardCharsets;

/**
 * Hang so dinh dang dict.pack. Doi chieu dac ta o PLAN.md muc 6.
 *
 * <p>HEADER (72 byte, little-endian toan bo):
 * <pre>
 *   0  magic          8B    "VIENDICT" (ASCII)
 *   8  formatVersion  i32   = 1
 *  12  flags          i32   bit0 = compressed
 *  16  entryCount     i32   so entry THUC nam trong BLOCKS
 *  20  blockCount     i32
 *  24  keyCount       i32   so khoa trong KEYS / ENTRY_PTRS  (>= entryCount)
 *  28  reserved       i32   = 0
 *  32  keysOffset     i64
 *  40  keysLength     i64
 *  48  entryPtrOffset i64
 *  56  blockDirOffset i64
 *  64  dataOffset     i64
 * </pre>
 *
 * <p>Vi sao can CA HAI con so {@code entryCount} va {@code keyCount} (ban dac ta dau
 * chi co mot, va nhu vay thi khong du): KEYS chua ca KHOA BI DANH cua cum thanh ngu -
 * "give up" tro ve entry "@give". Do do so khoa (~121.000) lon hon so entry (108.854).
 * Ban dau HEADER dinh 64 byte nhung het cho, nen noi dai thanh 72; PLAN.md muc 6 da sua theo.
 */
public final class PackFormat {
    private PackFormat() {}

    public static final byte[] MAGIC = "VIENDICT".getBytes(StandardCharsets.US_ASCII);
    public static final int FORMAT_VERSION = 1;
    public static final int HEADER_SIZE = 72;

    /** So entry moi block. Nho -> giai nen nhanh; lon -> nen tot hon. 64 la diem can bang da do. */
    public static final int ENTRIES_PER_BLOCK = 256;

    public static final int ENTRY_PTR_SIZE = 12;   // keyOffset:i32 + blockId:i32 + indexInBlock:i32
    public static final int BLOCK_DIR_SIZE = 16;   // fileOffset:i64 + compressedLen:i32 + rawLen:i32

    public static final int FLAG_COMPRESSED = 1;

    /** So block giu trong LRU cache. 64 x ~9 KB = ~600 KB (PLAN.md 6.3). */
    public static final int BLOCK_CACHE_SIZE = 64;

    // Vi tri cac truong trong HEADER - dung chung cho writer va reader.
    public static final int OFF_FORMAT_VERSION = 8;
    public static final int OFF_FLAGS = 12;
    public static final int OFF_ENTRY_COUNT = 16;
    public static final int OFF_BLOCK_COUNT = 20;
    public static final int OFF_KEY_COUNT = 24;
    public static final int OFF_KEYS_OFFSET = 32;
    public static final int OFF_KEYS_LENGTH = 40;
    public static final int OFF_ENTRY_PTR_OFFSET = 48;
    public static final int OFF_BLOCK_DIR_OFFSET = 56;
    public static final int OFF_DATA_OFFSET = 64;
}
