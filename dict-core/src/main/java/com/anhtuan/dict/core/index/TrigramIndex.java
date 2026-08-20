package com.anhtuan.dict.core.index;

import java.util.List;

/**
 * TODO(M3) - Fuzzy chong go sai bang trigram ky tu (PLAN.md 7.3).
 *
 * "word" -&gt; $$w, $wo, wor, ord, rd$, d$$
 * Xep hang bang he so Jaccard. CHI kich hoat khi tra chinh xac da truot -
 * chay song song vua cham vua sinh nhieu ket qua rac.
 *
 * Nghiem thu: go "aboout" -&gt; "about" o vi tri so 1.
 */
public final class TrigramIndex {
    private TrigramIndex() {}

    public static List<String> trigrams(String word) {
        throw new UnsupportedOperationException("TODO(M3)");
    }
}
