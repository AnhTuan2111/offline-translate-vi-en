package com.anhtuan.dict.core.index;

import java.nio.charset.StandardCharsets;

/**
 * Hang so dinh dang file index (PLAN.md 7.1). Dung chung cho ca ba file:
 * {@code vi.idx} (co dau), {@code vi-nodiac.idx} (bo dau), {@code tri.idx} (trigram).
 *
 * <p>HEADER (48 byte, little-endian):
 * <pre>
 *   0  magic         8B   "VIENIDX2"
 *   8  termCount     i32
 *  12  docCount      i32
 *  16  avgDocLen     f32
 *  20  reserved      i32  = 0
 *  24  termsOffset   i64
 *  32  termPtrOffset i64
 *  40  docLenOffset  i64
 * </pre>
 *
 * <pre>
 * TERMS     term da sap xep theo THU TU BYTE UTF-8, moi term ket thuc 0x00
 * TERM_PTRS termCount x 16B { termOffset:i32, docFreq:i32, postingOffset:i64 }
 * DOC_LENS  docCount x u16   do dai tai lieu, can cho mau so BM25 (|D|)
 * POSTINGS  moi term: docFreq cap ( varint (deltaDocId &lt;&lt; 1 | coTf), [varint tf] )
 * </pre>
 *
 * <p>{@code deltaDocId} la HIEU so voi docId truoc do, khong phai so tuyet doi -
 * nho vay varint chi ton 1-2 byte thay vi 4 byte cua int co dinh (PLAN.md 7.1).
 *
 * <p>Bit thap nhat cua so do bao "co byte tf dang sau hay khong". Do thuc te: 85% cap
 * postings co tf = 1, ghi han mot byte 0x01 cho chung la nem di gan 1 MB.
 *
 * <p>Them so voi ban dac ta dau: vung DOC_LENS. BM25 can {@code |D|} cua tung tai lieu
 * ma dac ta cu khong cho cho no o dau ca. u16 la du: tai lieu dai nhat trong nguon
 * co khoang 3.000 token.
 */
public final class IndexFormat {
    private IndexFormat() {}

    public static final byte[] MAGIC = "VIENIDX2".getBytes(StandardCharsets.US_ASCII);
    public static final int HEADER_SIZE = 48;
    public static final int TERM_PTR_SIZE = 16;
    public static final int MAX_DOC_LENGTH = 0xFFFF;

    public static final int OFF_TERM_COUNT = 8;
    public static final int OFF_DOC_COUNT = 12;
    public static final int OFF_AVG_DOC_LEN = 16;
    public static final int OFF_TERMS_OFFSET = 24;
    public static final int OFF_TERM_PTR_OFFSET = 32;
    public static final int OFF_DOC_LEN_OFFSET = 40;

    /** Ten ba file index trong thu muc du lieu. */
    public static final String VI_INDEX = "vi.idx";
    public static final String VI_NODIAC_INDEX = "vi-nodiac.idx";
    public static final String TRIGRAM_INDEX = "tri.idx";
}
