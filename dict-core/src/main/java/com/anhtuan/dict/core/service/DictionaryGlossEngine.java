package com.anhtuan.dict.core.service;

import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.spi.TranslationEngine;
import java.util.List;

/**
 * TODO(M4) - Engine dich cau cua v1: chu giai theo cum (PLAN.md 8.1).
 *
 * LUU Y VE KY VONG: day la CHU GIAI, khong phai ban dich tu nhien.
 *   "He is about to give up his job"
 *   -&gt; He[anh ay] is[thi/la] about to[sap sua] give up[tu bo] his[cua anh ay] job[cong viec]
 * Muon cau tu nhien thi can NMT - cong TranslationEngine da chua san cho v2.
 */
public final class DictionaryGlossEngine implements TranslationEngine {

    public static final String ENGINE_ID = "dictionary-gloss";

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public List<Segment> translate(String sentence) {
        throw new UnsupportedOperationException("TODO(M4): xem PLAN.md muc 8.1");
    }
}
