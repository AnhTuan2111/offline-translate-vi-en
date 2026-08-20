package com.anhtuan.dict.core.pack;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Ma hoa so nguyen khong dau kieu LEB128 (PLAN.md 6.1).
 *
 * Moi byte mang 7 bit du lieu, bit cao nhat (0x80) bao "con byte nua".
 * So nho ton it byte: 0..127 -> 1 byte, 128..16383 -> 2 byte...
 *
 * Day la ly do postings chi ton ~1,3 byte moi luot thay vi 4 byte cua int co dinh.
 */
public final class VarInt {
    private VarInt() {}

    /** So byte can de ma hoa {@code value}. */
    public static int sizeOf(int value) {
        int n = 1;
        int v = value >>> 7;
        while (v != 0) { n++; v >>>= 7; }
        return n;
    }

    public static void write(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    /** Doc tu ByteBuffer, con tro buffer tu dong tien len. */
    public static int read(ByteBuffer buf) {
        int result = 0;
        int shift = 0;
        while (true) {
            byte b = buf.get();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift > 35) throw new IllegalStateException("varint qua dai - file hong?");
        }
    }
}
