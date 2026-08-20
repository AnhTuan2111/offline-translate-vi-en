package com.anhtuan.dict.core.nlp;

import java.util.List;

/**
 * TODO(M4) - Tach cau thanh token, GIU OFFSET GOC (PLAN.md 8.1 buoc 1).
 *
 * Offset la bat buoc: UI phai highlight dung vi tri tu trong cau nguoi dung nhap.
 * Bat bien can test: noi text cua tat ca token lai phai ra dung chuoi dau vao.
 */
public final class Tokenizer {
    private Tokenizer() {}

    public static List<Token> tokenize(String text) {
        throw new UnsupportedOperationException("TODO(M4): xem PLAN.md muc 8.1");
    }

    /** @param start vi tri bat dau (inclusive) @param end vi tri ket thuc (exclusive) */
    public record Token(String text, int start, int end, boolean isWord) {}
}
