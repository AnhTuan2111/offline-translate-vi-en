package com.anhtuan.dict.core.nlp;

/**
 * TODO(M4) - Khop cum tu longest-match trong cau (PLAN.md 8.1 buoc 2, AD-5).
 *
 * VI SAO KHONG DUNG AHO-CORASICK:
 * KEYS trong pack da sap xep san nen binary search lam duoc viec nay ma
 * KHONG ton them RAM. Aho-Corasick voi 11.941 cum se ngon ~80 MB heap.
 *
 * Toi uu bat buoc: HashSet phraseStarters (4.670 tu, ~39 KB) chua tu MO DAU
 * cua moi cum. Tu hien tai khong nam trong set thi bo qua probe hoan toan -
 * cat duoc ~90% so lan binary search.
 */
public final class PhraseProbe {
    private PhraseProbe() {}

    /** So tu toi da cua mot cum can thu. Da do: 99% cum co 4 tu tro xuong. */
    public static final int MAX_PHRASE_WORDS = 4;
}
