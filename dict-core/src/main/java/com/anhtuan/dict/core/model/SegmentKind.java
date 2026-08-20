package com.anhtuan.dict.core.model;

/** Phan loai mot doan trong ket qua dich cau. */
public enum SegmentKind {
    /** Tu don tra duoc trong tu dien. */
    WORD,
    /** Cum nhieu tu khop longest-match ("give up", "about to"). */
    PHRASE,
    /** Dau cau, khoang trang - giu nguyen, khong tra. */
    PUNCT,
    /** Khong tra duoc kem ca sau khi lemmatize - giu nguyen tu goc. */
    UNKNOWN,
    /**
     * Ca cau da duoc dich tron ven boi mot NMT engine.
     * v1 khong sinh ra kind nay; danh san cho OnnxNmtEngine o v2 (PLAN.md 13).
     */
    TRANSLATED
}
