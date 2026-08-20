package com.anhtuan.dict.core.model;

/**
 * Mot vi du minh hoa, lay tu dong "=english+ tieng viet" trong file nguon.
 *
 * @param en         cau vi du tieng Anh, khong bao gio null
 * @param vi         ban dich; NULL neu dong nguon khong co dau '+' (PLAN.md 4.2 quy tac 8)
 * @param glossIndex chi so cua gloss ma vi du nay minh hoa (PLAN.md 4.2 quy tac 2);
 *                   -1 neu vi du dung truoc bat ky dong '-' nao
 */
public record Example(String en, String vi, int glossIndex) {
    public Example {
        if (en == null) throw new IllegalArgumentException("en khong duoc null");
    }

    public boolean hasTranslation() {
        return vi != null && !vi.isBlank();
    }
}
