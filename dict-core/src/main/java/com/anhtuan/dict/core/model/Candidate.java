package com.anhtuan.dict.core.model;

/**
 * Mot lua chon nghia cho mot Segment.
 * UI hien candidate dau tien, nguoi dung bam vao de doi sang cac candidate khac.
 *
 * @param score diem xep hang; gloss engine dung thu tu sense, BM25 dung diem thuc
 */
public record Candidate(String headword, String gloss, String pos, double score) {
}
