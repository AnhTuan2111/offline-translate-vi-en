package com.anhtuan.dict.core.nlp;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chuan hoa van ban. Dung chung cho ca luc BUILD index lan luc TRA CUU -
 * hai ben lech nhau la binary search truot (PLAN.md 4.3, 8.3).
 */
public final class TextNormalizer {
    private TextNormalizer() {}

    private static final char BOM = '\uFEFF';

    /**
     * Sinh headwordNorm - khoa tra cuu chinh (PLAN.md 4.3).
     * Thu tu cac buoc la BAT BUOC, doi thu tu se ra ket qua khac.
     */
    public static String normalizeHeadword(String raw) {
        if (raw == null) return "";
        String s = stripBom(raw).trim();
        s = s.replace('_', ' ');                       // a_la_carte -> a la carte
        s = collapseSpaces(s);
        s = s.toLowerCase(Locale.ROOT);                // ROOT: tranh bay Locale tieng Tho (I -> i khong dau cham)
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    /**
     * Bo dau tieng Viet cho search khong dau (PLAN.md 8.3).
     * "cham soc" phai tim ra "cham soc" - thieu cai nay app "cam giac ngu" ngay.
     */
    public static String removeDiacritics(String s) {
        if (s == null) return "";
        // 'd'/'D' KHONG phai la d + dau phu trong Unicode - NFD khong tach duoc, phai thay tay TRUOC.
        String t = s.replace('\u0111', 'd').replace('\u0110', 'D');
        t = Normalizer.normalize(t, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) sb.append(c);
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC);
    }

    /** Chuan hoa truy van tieng Viet: bo dau + lowercase. */
    public static String normalizeVietnamese(String raw) {
        return removeDiacritics(stripBom(raw).trim().toLowerCase(Locale.ROOT));
    }

    public static String stripBom(String s) {
        return (s != null && !s.isEmpty() && s.charAt(0) == BOM) ? s.substring(1) : s;
    }

    public static String collapseSpaces(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean prevSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean isSpace = Character.isWhitespace(c);
            if (isSpace) {
                if (!prevSpace && sb.length() > 0) sb.append(' ');
            } else {
                sb.append(c);
            }
            prevSpace = isSpace;
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == ' ') end--;
        return sb.substring(0, end);
    }
}
