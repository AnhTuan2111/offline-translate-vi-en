package com.anhtuan.dict.core.pack;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

/**
 * So sanh chuoi theo THU TU BYTE UTF-8 (khong dau).
 *
 * VI SAO PHAI CO CLASS RIENG (PLAN.md 12, rui ro so 2):
 * PackWriter sap xep KEYS luc build, PackReader binary search luc tra.
 * Neu hai ben dung hai quy tac so sanh khac nhau thi binary search se
 * TRA SAI AM THAM - khong crash, khong bao loi, chi la thinh thoang khong tim thay tu.
 *
 * String#compareTo so sanh theo gia tri char UTF-16, KHAC voi thu tu byte UTF-8
 * o cac ky tu ngoai BMP. Vi vay ca hai ben deu bat buoc goi class nay.
 */
public final class Utf8Compare {
    private Utf8Compare() {}

    /** Comparator dung cho PackWriter khi sap xep khoa. */
    public static final Comparator<String> COMPARATOR =
            (a, b) -> compare(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));

    public static int compare(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int diff = (a[i] & 0xFF) - (b[i] & 0xFF);   // KHONG dau - byte 0x80+ phai lon hon
            if (diff != 0) return diff;
        }
        return a.length - b.length;
    }

    /**
     * So sanh khoa nam trong buffer da mmap (ket thuc bang 0x00) voi mot khoa can tim.
     * Doc truc tiep tren buffer, khong tao String - day la duong nong cua binary search.
     *
     * @param buf       buffer chua vung KEYS
     * @param keyOffset vi tri bat dau khoa trong buf
     * @param target    khoa can tim, da encode UTF-8
     * @return am neu khoa trong buf nho hon target, duong neu lon hon, 0 neu bang
     */
    public static int compareAt(ByteBuffer buf, int keyOffset, byte[] target) {
        for (int i = 0; i < target.length; i++) {
            int c = buf.get(keyOffset + i) & 0xFF;
            if (c == 0x00) return -1;                    // khoa trong buf ngan hon -> nho hon
            int diff = c - (target[i] & 0xFF);
            if (diff != 0) return diff;
        }
        int next = buf.get(keyOffset + target.length) & 0xFF;
        return next == 0x00 ? 0 : 1;                     // khoa trong buf dai hon -> lon hon
    }
}
