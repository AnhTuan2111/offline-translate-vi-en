package com.anhtuan.dict.core.nlp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TextNormalizerTest {

    @Test
    @DisplayName("normalizeHeadword: doi '_' thanh khoang trang, gop khoang trang, lowercase")
    void normalizesHeadword() {
        assertEquals("a la carte", TextNormalizer.normalizeHeadword("a_la_carte"));
        assertEquals("give up", TextNormalizer.normalizeHeadword("  Give   Up  "));
        assertEquals("about to", TextNormalizer.normalizeHeadword("About To"));
    }

    @Test
    @DisplayName("normalizeHeadword dung Locale.ROOT - tranh bay locale tieng Tho")
    void usesRootLocaleNotDefault() {
        Locale original = Locale.getDefault();
        try {
            // Trong locale tieng Tho, "I".toLowerCase() ra "ı" (i khong dau cham), khong phai "i".
            // Neu parser dung locale mac dinh, may nguoi dung Tho se sinh ra khoa khac
            // -> tra tu "IT" khong bao gio ra ket qua.
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("it", TextNormalizer.normalizeHeadword("IT"));
            assertEquals("india", TextNormalizer.normalizeHeadword("INDIA"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("removeDiacritics: bo dau tieng Viet cho search khong dau")
    void removesVietnameseDiacritics() {
        assertEquals("cham soc", TextNormalizer.removeDiacritics("chăm sóc"));
        assertEquals("tieng Viet", TextNormalizer.removeDiacritics("tiếng Việt"));
        assertEquals("hoc sinh gioi", TextNormalizer.removeDiacritics("học sinh giỏi"));
    }

    @Test
    @DisplayName("removeDiacritics xu ly rieng chu 'd' - NFD khong tach duoc no")
    void handlesDStrokeSpecially() {
        // 'd' (U+0111) la mot ky tu doc lap, KHONG phai 'd' + dau phu.
        // Chi dua vao NFD thi "dong" van ra "dong" -> search khong dau hong.
        assertEquals("dong", TextNormalizer.removeDiacritics("đông"));
        assertEquals("Dong", TextNormalizer.removeDiacritics("Đông"));
        assertEquals("do do da", TextNormalizer.removeDiacritics("đỏ đỏ đã"));
    }

    @Test
    @DisplayName("removeDiacritics khong dung toi tieng Anh")
    void leavesAsciiUntouched() {
        assertEquals("about to", TextNormalizer.removeDiacritics("about to"));
        assertEquals("give up", TextNormalizer.removeDiacritics("give up"));
    }

    @Test
    @DisplayName("normalizeVietnamese: bo dau va lowercase cung luc")
    void normalizesVietnameseQuery() {
        assertEquals("cham soc", TextNormalizer.normalizeVietnamese("Chăm Sóc"));
        assertEquals("cham soc", TextNormalizer.normalizeVietnamese("  CHĂM SÓC  "));
    }

    @Test
    @DisplayName("stripBom bo BOM UTF-8 o dau chuoi")
    void stripsBom() {
        assertEquals("@a /ei/", TextNormalizer.stripBom("﻿@a /ei/"));
        assertEquals("khong co bom", TextNormalizer.stripBom("khong co bom"));
        assertEquals("", TextNormalizer.stripBom(""));
    }

    @Test
    @DisplayName("collapseSpaces gop moi loai khoang trang thanh mot dau cach")
    void collapsesAllWhitespace() {
        assertEquals("a b c", TextNormalizer.collapseSpaces("a   b\t\tc"));
        assertEquals("a b", TextNormalizer.collapseSpaces("  a  b  "));
        assertEquals("", TextNormalizer.collapseSpaces("   "));
    }

    @Test
    @DisplayName("Chuan hoa la HAM THUAN TUY - goi hai lan cho ket qua giong nhau")
    void normalizationIsIdempotent() {
        for (String s : new String[]{"a_la_carte", "  Give  Up ", "chăm sóc", "Đông"}) {
            String once = TextNormalizer.normalizeHeadword(s);
            assertEquals(once, TextNormalizer.normalizeHeadword(once),
                    "chuan hoa hai lan ra ket qua khac nhau voi: " + s);
        }
    }
}
