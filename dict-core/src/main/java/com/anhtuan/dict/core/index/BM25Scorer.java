package com.anhtuan.dict.core.index;

/**
 * Xep hang do lien quan bang BM25 (PLAN.md 7.2).
 *
 * Day la toan bo "search theo muc do lien quan" ma Lucene cung dung -
 * chi khac la o day no gon trong mot file thay vi 150 MB index.
 */
public final class BM25Scorer {

    /** Do bao hoa tan suat tu. 1.2 la gia tri chuan cua Lucene. */
    public static final double K1 = 1.2;
    /** Muc do phat van ban dai. 0.75 la gia tri chuan. */
    public static final double B = 0.75;

    private final int docCount;
    private final double avgDocLength;

    public BM25Scorer(int docCount, double avgDocLength) {
        if (docCount <= 0) throw new IllegalArgumentException("docCount phai > 0");
        if (avgDocLength <= 0) throw new IllegalArgumentException("avgDocLength phai > 0");
        this.docCount = docCount;
        this.avgDocLength = avgDocLength;
    }

    /**
     * idf(t) = ln( 1 + (N - df + 0.5) / (df + 0.5) )
     * Term hiem -> idf cao. Cong thuc nay luon duong nen khong bi diem am.
     */
    public double idf(int docFreq) {
        return Math.log(1.0 + (docCount - docFreq + 0.5) / (docFreq + 0.5));
    }

    /** Diem cua mot term trong mot document. Cong don qua cac term de ra diem cuoi. */
    public double score(int docFreq, int termFreq, int docLength) {
        double idf = idf(docFreq);
        double norm = K1 * (1 - B + B * docLength / avgDocLength);
        return idf * (termFreq * (K1 + 1)) / (termFreq + norm);
    }

    /**
     * He so boost (PLAN.md 7.2). Khong co cai nay thi go "di" se ra mot dong
     * cum tu hiem truoc ca tu "go".
     */
    public static double boost(boolean exactFullGloss, boolean inPrimaryGloss,
                               boolean singleWord, int headwordLength) {
        double f = 1.0;
        if (exactFullGloss) f *= 3.0;
        if (inPrimaryGloss) f *= 1.8;
        if (singleWord)     f *= 1.4;
        if (headwordLength <= 6) f *= 1.2;
        return f;
    }
}
