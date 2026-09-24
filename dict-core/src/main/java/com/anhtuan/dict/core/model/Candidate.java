package com.anhtuan.dict.core.model;

/**
 * Mot lua chon nghia cho mot Segment.
 * UI hien candidate dau tien, nguoi dung bam vao de doi sang cac candidate khac.
 *
 * @param score diem xep hang; gloss engine dung thu tu sense, BM25 dung diem thuc
 */
public record Candidate(String headword, String gloss, String pos, double score, int sourceId) {

    /** Nguon khong xac dinh - dung cho cac candidate tu sinh (tu chuc nang, cau da dich). */
    public static final int NO_SOURCE = -1;

    public Candidate(String headword, String gloss, String pos, double score) {
        this(headword, gloss, pos, score, NO_SOURCE);
    }
}
