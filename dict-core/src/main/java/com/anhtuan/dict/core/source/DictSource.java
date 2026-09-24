package com.anhtuan.dict.core.source;

/**
 * Mot nguon tu dien da duoc dong goi vao du lieu (PLAN.md F5, M7).
 *
 * @param id       so hieu, chinh la {@code sourceId} ghi trong tung {@code Entry} cua pack.
 *                 KHONG duoc doi sau khi da build, doi la moi entry tro sai nguon.
 * @param name     ten hien cho nguoi dung: "Anh-Viet 109K", "Thuat ngu CNTT"...
 * @param format   ma dinh dang da dung de doc ({@code DictParser#formatId})
 * @param file     ten file nguon goc, chi de nguoi dung nhan ra
 * @param entries  so muc tu lay duoc tu nguon nay
 * @param enabled  dang bat hay tat. Doi duoc ngay luc chay, khong phai build lai.
 * @param priority nho hon = uu tien hon. Quyet dinh nguon nao dua nghia len truoc.
 */
public record DictSource(int id, String name, String format, String file, int entries,
                         boolean enabled, int priority) {

    public DictSource {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("thieu ten nguon");
    }

    public DictSource withEnabled(boolean value) {
        return new DictSource(id, name, format, file, entries, value, priority);
    }

    public DictSource withPriority(int value) {
        return new DictSource(id, name, format, file, entries, enabled, value);
    }
}
