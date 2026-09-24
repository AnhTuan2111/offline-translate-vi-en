package com.anhtuan.dict.core.nlp;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bang TU CHUC NANG tieng Anh, dich cung va phan loai san.
 *
 * <h2>Vi sao phai co bang nay thay vi tra tu dien</h2>
 * Tu chuc nang la nhom tu XUAT HIEN NHIEU NHAT trong moi cau, va cung la nhom ma tu dien
 * dich TE NHAT. Do thuc te tren chinh du lieu cua du an:
 * <pre>
 *   he   -> "nó, anh ấy, ông ấy... (chỉ người và động vật giống đực)"
 *   the  -> "cái, con, người..."
 *   could-> "bình, bi đông, ca (đựng nước)"      (muc tu @could cua nguon bi hong)
 * </pre>
 * Nhet nguyen nhung chuoi do vao cau dich thi khong con gi doc duoc. Trong khi do nhom nay
 * la LOP DONG - tieng Anh co khoang 200 tu chuc nang va gan nhu khong bao gio them tu moi -
 * nen dich cung mot lan la xong vinh vien.
 *
 * <p>{@link Category} khong chi de dep: bo luat trat tu tu trong
 * {@code RuleBasedTranslationEngine} dua han vao no de biet cai gi dao ra sau danh tu,
 * cai gi bo di, cai gi ghep voi dong tu.
 */
public final class FunctionWords {

    private FunctionWords() {}

    public enum Category {
        /** Mao tu - tieng Viet khong co, BO HAN: "the old system" -> "hệ thống cũ". */
        ARTICLE,
        /** Luong tu dung truoc danh tu: some, many, all. */
        QUANTIFIER,
        /** Chi dinh tu - dao ra SAU danh tu: "this system" -> "hệ thống này". */
        DEMONSTRATIVE,
        /** Dai tu nhan xung. */
        PRONOUN,
        /** So huu - dao ra SAU danh tu: "his job" -> "công việc của anh ấy". */
        POSSESSIVE,
        /** to be - thuong bo di truoc tinh tu, giu "là" truoc danh tu. */
        BE,
        /** have/has/had - dau hieu thi hoan thanh. */
        HAVE,
        /** do/does/did - tro dong tu rong, bo di tru khi phu dinh. */
        DO,
        /** Dong tu tinh thai: can, must, should... */
        MODAL,
        /** Dau hieu thi tuong lai. */
        FUTURE,
        /** not / n't. */
        NEGATION,
        GIOI_TU,
        LIEN_TU,
        /** Trang tu hay gap. */
        TRANG_TU,
        /** there (is/are) -> "có". */
        EXISTENTIAL,
        /** "to" trong to-infinitive - bo di: "to leave" -> "rời đi". */
        INFINITIVE
    }

    /**
     * @param vi  nghia tieng Viet; CHUOI RONG nghia la bo han tu nay khoi cau dich
     * @param cat nhom, quyet dinh luat trat tu tu se ap dung
     */
    public record Fw(String vi, Category cat) {}

    private static final Map<String, Fw> TABLE = new HashMap<>(256);

    private static void put(String en, String vi, Category cat) {
        TABLE.put(en, new Fw(vi, cat));
    }

    static {
        // --- mao tu: bo han ---
        put("a", "", Category.ARTICLE);
        put("an", "", Category.ARTICLE);
        put("the", "", Category.ARTICLE);

        // --- dai tu ---
        put("i", "tôi", Category.PRONOUN);
        put("you", "bạn", Category.PRONOUN);
        put("he", "anh ấy", Category.PRONOUN);
        put("she", "cô ấy", Category.PRONOUN);
        put("it", "nó", Category.PRONOUN);
        put("we", "chúng tôi", Category.PRONOUN);
        put("they", "họ", Category.PRONOUN);
        put("me", "tôi", Category.PRONOUN);
        put("him", "anh ấy", Category.PRONOUN);
        put("us", "chúng tôi", Category.PRONOUN);
        put("them", "họ", Category.PRONOUN);
        put("myself", "chính tôi", Category.PRONOUN);
        put("himself", "chính anh ấy", Category.PRONOUN);
        put("herself", "chính cô ấy", Category.PRONOUN);
        put("themselves", "chính họ", Category.PRONOUN);
        put("itself", "chính nó", Category.PRONOUN);
        put("who", "ai", Category.PRONOUN);
        put("whom", "ai", Category.PRONOUN);
        put("what", "gì", Category.PRONOUN);
        put("everyone", "mọi người", Category.PRONOUN);
        put("everybody", "mọi người", Category.PRONOUN);
        put("everything", "mọi thứ", Category.PRONOUN);
        put("someone", "ai đó", Category.PRONOUN);
        put("somebody", "ai đó", Category.PRONOUN);
        put("something", "cái gì đó", Category.PRONOUN);
        put("nobody", "không ai", Category.PRONOUN);
        put("nothing", "không gì", Category.PRONOUN);

        // --- so huu: dao ra sau danh tu ---
        put("my", "của tôi", Category.POSSESSIVE);
        put("your", "của bạn", Category.POSSESSIVE);
        put("his", "của anh ấy", Category.POSSESSIVE);
        put("her", "của cô ấy", Category.POSSESSIVE);
        put("its", "của nó", Category.POSSESSIVE);
        put("our", "của chúng tôi", Category.POSSESSIVE);
        put("their", "của họ", Category.POSSESSIVE);
        put("whose", "của ai", Category.POSSESSIVE);

        // --- chi dinh tu: dao ra sau danh tu ---
        put("this", "này", Category.DEMONSTRATIVE);
        put("that", "đó", Category.DEMONSTRATIVE);
        put("these", "này", Category.DEMONSTRATIVE);
        put("those", "đó", Category.DEMONSTRATIVE);

        // --- luong tu ---
        put("some", "một số", Category.QUANTIFIER);
        put("any", "bất kỳ", Category.QUANTIFIER);
        put("all", "tất cả", Category.QUANTIFIER);
        put("both", "cả hai", Category.QUANTIFIER);
        put("each", "mỗi", Category.QUANTIFIER);
        put("every", "mọi", Category.QUANTIFIER);
        put("many", "nhiều", Category.QUANTIFIER);
        put("much", "nhiều", Category.QUANTIFIER);
        put("few", "ít", Category.QUANTIFIER);
        put("little", "ít", Category.QUANTIFIER);
        put("several", "vài", Category.QUANTIFIER);
        put("other", "khác", Category.QUANTIFIER);
        put("another", "một cái khác", Category.QUANTIFIER);
        put("such", "như vậy", Category.QUANTIFIER);
        // So dem. Thieu chung thi chung bi tra tu dien nhu danh tu thuong, va nghia dau bang
        // cua "four" trong nguon 109K la "chứng khoán lãi 4 qịu (sử học) bốn xu rượu" - da do
        // that voi "the four stages". Them ca cum truoc danh tu cho dung trat tu tieng Viet.
        put("one", "một", Category.QUANTIFIER);
        put("two", "hai", Category.QUANTIFIER);
        put("three", "ba", Category.QUANTIFIER);
        put("four", "bốn", Category.QUANTIFIER);
        put("five", "năm", Category.QUANTIFIER);
        put("six", "sáu", Category.QUANTIFIER);
        put("seven", "bảy", Category.QUANTIFIER);
        put("eight", "tám", Category.QUANTIFIER);
        put("nine", "chín", Category.QUANTIFIER);
        put("ten", "mười", Category.QUANTIFIER);
        put("eleven", "mười một", Category.QUANTIFIER);
        put("twelve", "mười hai", Category.QUANTIFIER);
        put("twenty", "hai mươi", Category.QUANTIFIER);
        put("thirty", "ba mươi", Category.QUANTIFIER);
        put("forty", "bốn mươi", Category.QUANTIFIER);
        put("fifty", "năm mươi", Category.QUANTIFIER);
        put("hundred", "trăm", Category.QUANTIFIER);
        put("thousand", "nghìn", Category.QUANTIFIER);
        put("million", "triệu", Category.QUANTIFIER);
        put("billion", "tỷ", Category.QUANTIFIER);

        // --- to be ---
        put("am", "là", Category.BE);
        put("is", "là", Category.BE);
        put("are", "là", Category.BE);
        put("was", "là", Category.BE);
        put("were", "là", Category.BE);
        put("be", "là", Category.BE);
        put("been", "là", Category.BE);
        put("being", "là", Category.BE);

        // --- have / do ---
        put("have", "đã", Category.HAVE);
        put("has", "đã", Category.HAVE);
        put("had", "đã", Category.HAVE);
        put("do", "", Category.DO);
        put("does", "", Category.DO);
        put("did", "", Category.DO);

        // --- tinh thai va tuong lai ---
        put("can", "có thể", Category.MODAL);
        put("could", "có thể", Category.MODAL);
        put("may", "có thể", Category.MODAL);
        put("might", "có thể", Category.MODAL);
        put("must", "phải", Category.MODAL);
        put("should", "nên", Category.MODAL);
        put("ought", "nên", Category.MODAL);
        put("need", "cần", Category.MODAL);
        put("will", "sẽ", Category.FUTURE);
        put("shall", "sẽ", Category.FUTURE);
        put("would", "sẽ", Category.FUTURE);

        // --- phu dinh ---
        put("not", "không", Category.NEGATION);
        put("n't", "không", Category.NEGATION);
        put("no", "không", Category.NEGATION);
        put("never", "không bao giờ", Category.NEGATION);
        put("cannot", "không thể", Category.NEGATION);

        // --- gioi tu ---
        put("of", "của", Category.GIOI_TU);
        put("in", "trong", Category.GIOI_TU);
        put("on", "trên", Category.GIOI_TU);
        put("at", "tại", Category.GIOI_TU);
        put("to", "đến", Category.GIOI_TU);
        put("for", "cho", Category.GIOI_TU);
        put("with", "với", Category.GIOI_TU);
        put("from", "từ", Category.GIOI_TU);
        put("by", "bởi", Category.GIOI_TU);
        put("about", "về", Category.GIOI_TU);
        put("into", "vào", Category.GIOI_TU);
        put("onto", "lên", Category.GIOI_TU);
        put("over", "trên", Category.GIOI_TU);
        put("under", "dưới", Category.GIOI_TU);
        put("above", "phía trên", Category.GIOI_TU);
        put("below", "phía dưới", Category.GIOI_TU);
        put("between", "giữa", Category.GIOI_TU);
        put("among", "trong số", Category.GIOI_TU);
        put("through", "qua", Category.GIOI_TU);
        put("during", "trong suốt", Category.GIOI_TU);
        // Phan tu hien tai lam gioi tu. Tu dien ghi chung la tinh tu, nen khong chot o day thi
        // buoc sap lai danh ngu day chung ra sau danh tu: "including preconditions" ra
        // "điều kiện tiên quyết kể cả". Day la tap dong, liet ke duoc het.
        put("including", "gồm cả", Category.GIOI_TU);
        put("excluding", "không tính", Category.GIOI_TU);
        put("regarding", "về", Category.GIOI_TU);
        put("concerning", "về", Category.GIOI_TU);
        put("considering", "xét đến", Category.GIOI_TU);
        put("depending on", "tuỳ theo", Category.GIOI_TU);
        put("without", "không có", Category.GIOI_TU);
        put("against", "chống lại", Category.GIOI_TU);
        put("towards", "về phía", Category.GIOI_TU);
        put("toward", "về phía", Category.GIOI_TU);
        put("upon", "trên", Category.GIOI_TU);
        put("within", "trong vòng", Category.GIOI_TU);
        put("across", "băng qua", Category.GIOI_TU);
        put("around", "quanh", Category.GIOI_TU);
        put("near", "gần", Category.GIOI_TU);
        put("off", "khỏi", Category.GIOI_TU);
        put("out", "ra", Category.GIOI_TU);
        put("up", "lên", Category.GIOI_TU);
        put("down", "xuống", Category.GIOI_TU);

        // --- lien tu ---
        put("and", "và", Category.LIEN_TU);
        put("or", "hoặc", Category.LIEN_TU);
        put("but", "nhưng", Category.LIEN_TU);
        put("because", "bởi vì", Category.LIEN_TU);
        put("if", "nếu", Category.LIEN_TU);
        put("unless", "trừ khi", Category.LIEN_TU);
        put("when", "khi", Category.LIEN_TU);
        put("while", "trong khi", Category.LIEN_TU);
        put("since", "kể từ khi", Category.LIEN_TU);
        put("until", "cho đến khi", Category.LIEN_TU);
        put("although", "mặc dù", Category.LIEN_TU);
        put("though", "mặc dù", Category.LIEN_TU);
        put("however", "tuy nhiên", Category.LIEN_TU);
        put("therefore", "do đó", Category.LIEN_TU);
        put("so", "nên", Category.LIEN_TU);
        put("than", "hơn", Category.LIEN_TU);
        put("as", "như", Category.LIEN_TU);
        put("whether", "liệu", Category.LIEN_TU);
        put("where", "nơi", Category.LIEN_TU);
        put("why", "tại sao", Category.LIEN_TU);
        put("how", "như thế nào", Category.LIEN_TU);
        put("which", "cái nào", Category.LIEN_TU);

        // --- trang tu hay gap ---
        put("very", "rất", Category.TRANG_TU);
        put("too", "quá", Category.TRANG_TU);
        put("also", "cũng", Category.TRANG_TU);
        put("only", "chỉ", Category.TRANG_TU);
        put("just", "chỉ", Category.TRANG_TU);
        put("still", "vẫn", Category.TRANG_TU);
        put("already", "đã", Category.TRANG_TU);
        put("yet", "chưa", Category.TRANG_TU);
        put("now", "bây giờ", Category.TRANG_TU);
        put("then", "sau đó", Category.TRANG_TU);
        put("always", "luôn luôn", Category.TRANG_TU);
        put("often", "thường", Category.TRANG_TU);
        put("sometimes", "đôi khi", Category.TRANG_TU);
        put("usually", "thường", Category.TRANG_TU);
        put("again", "lại", Category.TRANG_TU);
        put("here", "ở đây", Category.TRANG_TU);
        put("more", "hơn", Category.TRANG_TU);
        put("most", "nhất", Category.TRANG_TU);
        put("well", "tốt", Category.TRANG_TU);
        put("soon", "sớm", Category.TRANG_TU);
        put("today", "hôm nay", Category.TRANG_TU);
        put("yesterday", "hôm qua", Category.TRANG_TU);
        put("tomorrow", "ngày mai", Category.TRANG_TU);

        put("there", "có", Category.EXISTENTIAL);
    }

    public static Fw get(String word) {
        return word == null ? null : TABLE.get(word.toLowerCase(Locale.ROOT));
    }

    public static boolean isFunctionWord(String word) {
        return get(word) != null;
    }

    public static int size() {
        return TABLE.size();
    }
}
