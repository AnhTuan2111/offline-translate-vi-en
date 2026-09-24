package com.anhtuan.dict.core.lexicon;

import java.nio.charset.StandardCharsets;

/**
 * Hang so dinh dang {@code lex.bin} - bang xac suat dich tu (PLAN.md muc 7.4).
 *
 * <p>Bang nay tra loi dung mot cau hoi: <b>tu tieng Anh {@code e} thuong duoc dich thanh
 * nhung tieng Viet nao, voi xac suat bao nhieu?</b> Vi du sau khi hoc tu 3,5 trieu cap cau:
 * <pre>
 *   government -> chính(0,41) phủ(0,38) chính_quyền...
 *   plan       -> kế(0,22) hoạch(0,21) kế_hoạch...
 * </pre>
 * Nho do {@code RuleBasedTranslationEngine} chon duoc "kế hoạch" thay vi "sơ đồ" - dieu ma
 * luat ngu phap khong bao gio quyet dinh duoc vi ca hai deu la danh tu dung ngu phap.
 *
 * <p>HEADER (56 byte, little-endian):
 * <pre>
 *   0  magic         8B   "VIENLEX1"
 *   8  enCount       i32  so tu tieng Anh co trong bang
 *  12  viCount       i32  so am tiet tieng Viet trong tu vung dich
 *  16  topK          i32  so ban dich giu lai cho moi tu
 *  20  reserved      i32  = 0
 *  24  enKeysOffset  i64
 *  32  enPtrOffset   i64
 *  40  viKeysOffset  i64
 *  48  viPtrOffset   i64
 * </pre>
 *
 * <pre>
 * EN_KEYS   tu tieng Anh da sap xep theo THU TU BYTE UTF-8, ket thuc bang 0x00
 * EN_PTRS   enCount x 16B { keyOffset:i32, n:i32, postingOffset:i64 }
 * VI_KEYS   am tiet tieng Viet da sap xep, ket thuc bang 0x00 - id chinh la thu tu o day
 * VI_PTRS   viCount x 4B { keyOffset:i32 }
 * POSTINGS  voi moi tu tieng Anh: n cap ( varint deltaViId, u16 prob )
 * </pre>
 *
 * <p>{@code prob} luu duoi dang so nguyen 16 bit: {@code round(p * 65535)}. Sai so 1/65535
 * la khong dang ke so voi sai so cua chinh phep uoc luong, ma tiet kiem mot nua so voi float.
 */
public final class LexiconFormat {
    private LexiconFormat() {}

    public static final byte[] MAGIC = "VIENLEX1".getBytes(StandardCharsets.US_ASCII);
    public static final int HEADER_SIZE = 56;
    public static final int EN_PTR_SIZE = 16;
    public static final int VI_PTR_SIZE = 4;

    /** Ten file trong thu muc du lieu. */
    public static final String FILE_NAME = "lex.bin";

    /** Xac suat duoi muc nay thi khong luu: nhieu thong ke, giu lai chi ton cho. */
    public static final double MIN_PROBABILITY = 0.005;

    public static final int OFF_EN_COUNT = 8;
    public static final int OFF_VI_COUNT = 12;
    public static final int OFF_TOP_K = 16;
    public static final int OFF_EN_KEYS = 24;
    public static final int OFF_EN_PTRS = 32;
    public static final int OFF_VI_KEYS = 40;
    public static final int OFF_VI_PTRS = 48;

    public static int quantize(double probability) {
        int q = (int) Math.round(probability * 65535.0);
        return Math.max(0, Math.min(65535, q));
    }

    public static double dequantize(int quantized) {
        return (quantized & 0xFFFF) / 65535.0;
    }
}
