package com.anhtuan.dict.core.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Khop cum tu longest-match trong cau (PLAN.md 8.1 buoc 2, AD-5).
 *
 * <p>VI SAO KHONG DUNG AHO-CORASICK: KEYS trong pack da sap xep san nen binary search
 * lam duoc viec nay ma khong ton them mot byte RAM nao. Aho-Corasick voi 24.585 cum
 * se ngon khoang 80 MB heap.
 *
 * <p>Toi uu bat buoc: HashSet {@code starters} chua tu MO DAU cua moi cum (5.949 tu,
 * ~48 KB). Tu hien tai khong nam trong set thi bo qua probe hoan toan - cat duoc
 * khoang 90% so lan binary search.
 */
public final class PhraseProbe {
    private PhraseProbe() {}

    /** So tu toi da cua mot cum can thu. Da do: 99% cum co 4 tu tro xuong. */
    public static final int MAX_PHRASE_WORDS = 4;

    /**
     * @param wordCount so tu cua cum da khop
     * @param key       khoa thuc su tim thay trong tu dien (co the da lemma hoa tu dau)
     */
    public record Match(int wordCount, String key) {}

    /**
     * Dai tu lam tan ngu / so huu. Cum dong tu KHONG BAO GIO ket thuc bang nhung tu nay:
     * trong "gave me a book" thi "me" la tan ngu, khong phai mot phan cua cum dong tu.
     *
     * <p>Nguon co thuc su chua dong "!give me" (mot thán tu, nghia "tôi thích"), va neu
     * khong chan thi cau tren bi cat thanh "gave me" + "a book" roi dich sai hoan toan.
     */
    private static final Set<String> OBJECT_PRONOUNS = Set.of(
            "me", "you", "him", "her", "us", "them", "it",
            "my", "your", "his", "its", "our", "their");

    /**
     * Tim cum DAI NHAT bat dau tai {@code from}.
     *
     * <p>Thu n = 4, 3, 2 tu roi dung ngay khi khop - dai nhat thang, nho vay
     * "look after" khong bi cat thanh "look" + "after".
     *
     * <p>Ngoai dang nguyen van, con thu dang DA LEMMA HOA cua tu dau tien: cau
     * "He gave up his job" phai khop duoc "give up", vi trong tu dien chi co
     * "!to give up" chu khong co "gave up".
     *
     * @param words   cac tu da chuan hoa (lowercase, khong dau cau)
     * @param from    vi tri bat dau thu
     * @param starters tu mo dau cua moi cum co trong tu dien, xem PackReader#multiWordStarters
     * @param inDict  ham kiem tra mot khoa co trong tu dien hay khong (PackReader::contains)
     * @return cum dai nhat, hoac null neu khong co cum nao
     */

    public static Match longestMatch(List<String> words, int from,
                                     Set<String> starters, Predicate<String> inDict) {
        String first = words.get(from);
        List<String> firstForms = new ArrayList<>(4);
        if (starters.contains(first)) firstForms.add(first);
        for (String lemma : Lemmatizer.candidates(first)) {
            if (starters.contains(lemma)) firstForms.add(lemma);
        }
        if (firstForms.isEmpty()) return null;                  // cat 90% so lan probe

        int maxN = Math.min(MAX_PHRASE_WORDS, words.size() - from);
        for (int n = maxN; n >= 2; n--) {
            if (OBJECT_PRONOUNS.contains(words.get(from + n - 1))) continue;
            for (String head : firstForms) {
                StringBuilder sb = new StringBuilder(32).append(head);
                for (int k = 1; k < n; k++) sb.append(' ').append(words.get(from + k));
                String key = sb.toString();
                if (inDict.test(key)) return new Match(n, key);
            }
        }
        return null;
    }
}
