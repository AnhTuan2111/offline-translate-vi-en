package com.anhtuan.dict.core.nlp;

import java.util.ArrayList;
import java.util.List;

/**
 * Tach cau thanh token, GIU OFFSET GOC (PLAN.md 8.1 buoc 1).
 *
 * <p>Offset la bat buoc: UI phai to mau dung vi tri tu trong cau nguoi dung nhap,
 * va bam vao dung tu do de doi nghia.
 *
 * <p>BAT BIEN duoc test: noi {@code text()} cua tat ca token lai phai ra DUNG chuoi
 * dau vao, khong thieu mot dau cach nao. Nho vay UI chi viec ve lan luot cac segment
 * la du, khong phai tu chen lai khoang trang.
 */
public final class Tokenizer {
    private Tokenizer() {}

    /**
     * @param start vi tri bat dau (inclusive)
     * @param end   vi tri ket thuc (exclusive)
     */
    public record Token(String text, int start, int end, boolean isWord) {}

    public static List<Token> tokenize(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<Token> out = new ArrayList<>(text.length() / 4 + 4);
        int i = 0;
        int n = text.length();
        while (i < n) {
            if (isWordStart(text.charAt(i))) {
                int j = i + 1;
                while (j < n && isWordPart(text, j)) j++;
                out.add(new Token(text.substring(i, j), i, j, true));
                i = j;
            } else {
                int j = i + 1;
                while (j < n && !isWordStart(text.charAt(j))) j++;
                out.add(new Token(text.substring(i, j), i, j, false));
                i = j;
            }
        }
        return out;
    }

    private static boolean isWordStart(char c) {
        return Character.isLetterOrDigit(c);
    }

    /**
     * Dau nhay va gach ngang duoc tinh la PHAN CUA TU khi con chu dung sau:
     * "don't" la mot token, "acid-proof" la mot token (va la headword thuc trong nguon),
     * nhung "end." thi dau cham bi tach ra.
     */
    private static boolean isWordPart(String s, int i) {
        char c = s.charAt(i);
        if (Character.isLetterOrDigit(c)) return true;
        if (c != '\'' && c != '-' && c != '’') return false;
        return i + 1 < s.length() && Character.isLetterOrDigit(s.charAt(i + 1));
    }
}
