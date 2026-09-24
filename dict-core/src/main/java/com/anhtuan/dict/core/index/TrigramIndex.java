package com.anhtuan.dict.core.index;

import java.util.ArrayList;
import java.util.List;

/**
 * Fuzzy chong go sai bang trigram ky tu (PLAN.md 7.3).
 *
 * <p>"word" -&gt; $$w, $wo, wor, ord, rd$, d$$ - hai ky tu dem '$' o hai dau giup
 * phan biet tu bat dau/ket thuc khac nhau.
 *
 * <p>Xep hang bang he so Jaccard. CHI kich hoat khi tra chinh xac da truot: chay
 * song song vua cham vua sinh nhieu ket qua rac.
 */
public final class TrigramIndex {
    private TrigramIndex() {}

    private static final char PAD = '$';

    public static List<String> trigrams(String word) {
        if (word == null || word.isEmpty()) return List.of();
        String padded = PAD + (PAD + word + PAD) + PAD;
        List<String> out = new ArrayList<>(padded.length());
        for (int i = 0; i + 3 <= padded.length(); i++) {
            out.add(padded.substring(i, i + 3));
        }
        return out;
    }

    /**
     * He so Jaccard giua truy van va mot tai lieu:
     * so trigram khop chia cho tong so trigram cua hai ben (tru phan giao).
     *
     * @param matched    so trigram cua truy van tim thay trong tai lieu
     * @param queryCount tong so trigram cua truy van
     * @param docCount   tong so trigram cua tai lieu (lay tu DOC_LENS)
     */
    public static double jaccard(int matched, int queryCount, int docCount) {
        int union = queryCount + docCount - matched;
        return union <= 0 ? 0 : (double) matched / union;
    }
}
