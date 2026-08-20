package com.anhtuan.dict.core.model;

import java.util.List;

/**
 * Mot doan trong ket qua dich cau, co anh xa nguoc ve vi tri trong cau goc.
 *
 * VI SAO translate() TRA List<Segment> CHU KHONG TRA String (PLAN.md 5):
 * de UI dung chung cho ca hai che do ma khong phai sua.
 *   - Gloss engine  -> nhieu Segment, moi cai co nhieu candidates de nguoi dung doi nghia
 *   - NMT engine v2 -> dung MOT Segment kind=TRANSLATED chua ca cau da dich
 *
 * @param startOffset vi tri bat dau trong chuoi goc (inclusive)
 * @param endOffset   vi tri ket thuc trong chuoi goc (exclusive)
 */
public record Segment(String sourceText, int startOffset, int endOffset,
                      SegmentKind kind, List<Candidate> candidates) {
    public Segment {
        if (sourceText == null) throw new IllegalArgumentException("sourceText khong duoc null");
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("offset khong hop le: " + startOffset + ".." + endOffset);
        }
        candidates = List.copyOf(candidates);
    }

    /** Nghia hien thi mac dinh, null neu khong tra duoc. */
    public String displayGloss() {
        return candidates.isEmpty() ? null : candidates.getFirst().gloss();
    }
}
