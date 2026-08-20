package com.anhtuan.dict.core.model;

import java.util.List;

/**
 * Mot muc tu hoan chinh trong tu dien.
 *
 * @param headword     dang hien thi, giu nguyen goc ("a la carte")
 * @param headwordNorm dang da chuan hoa dung lam KHOA TRA CUU (PLAN.md 4.3).
 *                     Bat buoc sinh bang TextNormalizer#normalizeHeadword.
 * @param ipa          phien am giua hai dau '/', co the null
 * @param variant      dang viet khac ghi trong ngoac tren cung dong headword,
 *                     vi du "@acid-proof /.../ (acid-resisting) /.../" -> "acid-resisting".
 *                     Co o 7.128 dong trong file nguon. Null neu khong co.
 * @param senses       cac nhom nghia theo tu loai
 * @param idioms       cac thanh ngu
 * @param crossRefs    tham chieu cheo tu cac dong '+', vi du "Xem FINANCIAL CAPITAL."
 *                     Chu yeu xuat hien o phan tu dien kinh te nhung trong file.
 * @param sourceId     nguon tu dien, phuc vu F5 "bo sung tai lieu" (PLAN.md M7)
 */
public record Entry(String headword, String headwordNorm, String ipa, String variant,
                    List<Sense> senses, List<Idiom> idioms, List<String> crossRefs, int sourceId) {
    public Entry {
        if (headword == null) throw new IllegalArgumentException("headword khong duoc null");
        if (headwordNorm == null) throw new IllegalArgumentException("headwordNorm khong duoc null");
        senses = List.copyOf(senses);
        idioms = List.copyOf(idioms);
        crossRefs = List.copyOf(crossRefs);
    }

    /** So tu trong headword - quyet dinh so n-gram can probe (PLAN.md 8.1 buoc 2). */
    public int wordCount() {
        if (headwordNorm.isEmpty()) return 0;
        int n = 1;
        for (int i = 0; i < headwordNorm.length(); i++) {
            if (headwordNorm.charAt(i) == ' ') n++;
        }
        return n;
    }

    public boolean isMultiWord() {
        return wordCount() > 1;
    }
}
