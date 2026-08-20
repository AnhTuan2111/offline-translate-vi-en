package com.anhtuan.dict.core.nlp;

/**
 * TODO(M4) - Dua tu ve dang nguyen the (PLAN.md 8.2).
 *
 * TUYET DOI KHONG dung Porter stemmer: no cho ra "runn", tra tu dien truot sach.
 * Can LEMMA THAT, theo 3 tang:
 *   1. bang bat quy tac (~200 muc): went-&gt;go, better-&gt;good, mice-&gt;mouse
 *   2. tu dien lemma-en.txt (~600 KB): running-&gt;run, studies-&gt;study
 *   3. luat hau to: -s/-es/-ed/-ing/-ly + hoan nguyen phu am doi (stopped-&gt;stop)
 *
 * Nghiem thu: "She went running yesterday" -&gt; went-&gt;go, running-&gt;run.
 */
public final class Lemmatizer {
    private Lemmatizer() {}

    public static String lemma(String word) {
        throw new UnsupportedOperationException("TODO(M4): xem PLAN.md muc 8.2");
    }
}
