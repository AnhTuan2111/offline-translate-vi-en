package com.anhtuan.dict.core.spi;

import com.anhtuan.dict.core.model.Segment;
import java.util.List;

/**
 * CONG MO RONG SO 2 - cho phep cam NMT vao sau nay ma khong dap kien truc (PLAN.md 13).
 *
 * v1: DictionaryGlossEngine  - tra nhieu Segment (chu giai theo cum)
 * v2: OnnxNmtEngine          - tra MOT Segment kind=TRANSLATED (cau dich tu nhien)
 *
 * UI chi lam viec voi interface nay nen doi engine khong phai sua UI.
 */
public interface TranslationEngine {

    /** Ten engine hien cho nguoi dung chon. */
    String engineId();

    /**
     * Dich mot cau tieng Anh sang tieng Viet.
     *
     * @return danh sach segment phu kin chuoi goc theo thu tu offset tang dan.
     *         Noi sourceText cua tat ca segment lai phai ra dung chuoi dau vao.
     */
    List<Segment> translate(String sentence);

    /** Engine da san sang chua (NMT can nap model, co the that bai). */
    default boolean isAvailable() {
        return true;
    }
}
