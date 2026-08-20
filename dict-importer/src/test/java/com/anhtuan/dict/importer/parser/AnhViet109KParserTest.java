package com.anhtuan.dict.importer.parser;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Sense;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test M1 cho parser. Chay HOAN TOAN tren du lieu tu tao - khong can file 15 MB,
 * nen chay duoc o may khac va tren CI.
 *
 * Kiem tra doi chieu voi file that nam o {@link RealDictionaryFileTest}.
 */
class AnhViet109KParserTest {

    /** Trich doan that tu anhviet109K.txt - entry '@about'. Day la ca kho nhat trong file. */
    private static final String ABOUT_ENTRY = """
            @about /ə'baut/
            *  phó từ
            - xung quanh, quanh quẩn, đây đó, rải rác
            =he is somewhere about+ anh ta ở quanh quẩn đâu đó
            - đằng sau
            =about turn!+ đằng sau quay
            !about and about
            - (từ Mỹ,nghĩa Mỹ) rất giống nhau
            !to be about
            - bận (làm gì)
            - đã dậy được (sau khi ốm)
            *  giới từ
            - về
            =to know much about Vietnam+ biết nhiều về Việt Nam
            !to be about to
            - sắp, sắp sửa
            =the train is about to start+ xe lửa sắp khởi hành
            """;

    @Test
    @DisplayName("Tach headword va phien am IPA")
    void splitsHeadwordAndIpa() {
        var p = AnhViet109KParser.splitHeadword("about /ə'baut/");
        assertEquals("about", p.headword());
        assertEquals("ə'baut", p.ipa());
    }

    @Test
    @DisplayName("Headword khong co phien am thi ipa = null")
    void headwordWithoutIpa() {
        var p = AnhViet109KParser.splitHeadword("about and about");
        assertEquals("about and about", p.headword());
        assertNull(p.ipa());
    }

    @Test
    @DisplayName("Dau '_' cua dinh dang goc duoc doi thanh khoang trang")
    void underscoreBecomesSpace() {
        var p = AnhViet109KParser.splitHeadword("a_la_carte /'ɑ:lɑ:'kɑ:t/");
        assertEquals("a la carte", p.headword());
    }

    @Test
    @DisplayName("Headword tu no chua dau '/' van tach dung")
    void headwordContainingSlash() {
        var p = AnhViet109KParser.splitHeadword("and/or");
        assertEquals("and/or", p.headword());
        assertNull(p.ipa());
    }

    @Test
    @DisplayName("Dong headword co bien the: '(acid-resisting)' KHONG duoc nuot vao headword")
    void variantIsSeparatedFromHeadword() {
        var p = AnhViet109KParser.splitHeadword("acid-proof /'æsid'pru:f/ (acid-resisting) /'æsidri'zistiɳ/");
        assertEquals("acid-proof", p.headword(), "phai cat o dau '/' DAU TIEN, khong phai cuoi cung");
        assertEquals("'æsid'pru:f", p.ipa());
        assertEquals("acid-resisting", p.variant());
    }

    @Test
    @DisplayName("Dong '+' la tham chieu cheo, khong phai rac")
    void crossReferenceLineIsCaptured(@TempDir Path dir) throws IOException {
        Entry e = parseSingle(dir, """
                @A shares
                - (Econ) Cổ phiếu A.
                + Xem FINANCIAL CAPITAL.
                """);
        assertEquals(1, e.crossRefs().size(), "dong '+' bi coi la rac thay vi tham chieu cheo");
        assertEquals("Xem FINANCIAL CAPITAL.", e.crossRefs().get(0));
        assertEquals(1, e.senses().get(0).glosses().size());
    }

    @Test
    @DisplayName("Vi du khong co dau '+' van duoc giu, vi = null (quy tac 8)")
    void exampleWithoutPlusIsKept() {
        var ex = AnhViet109KParser.parseExample("just an example", 1);
        assertNotNull(ex);
        assertEquals("just an example", ex.en());
        assertNull(ex.vi());
        assertFalse(ex.hasTranslation());
    }

    @Test
    @DisplayName("CAI BAY: dong '-' sau '!' thuoc ve idiom, khong thuoc ve sense")
    void idiomGlossesDoNotLeakIntoSense(@TempDir Path dir) throws IOException {
        Entry about = parseSingle(dir, ABOUT_ENTRY);

        // 2 sense: "pho tu" va "gioi tu"
        assertEquals(2, about.senses().size(), "phai co dung 2 sense");
        assertEquals("phó từ", about.senses().get(0).pos());
        assertEquals("giới từ", about.senses().get(1).pos());

        // Sense "pho tu" chi co 2 nghia cua rieng no.
        // Neu parser sai, 3 nghia cua cac idiom se bi tron vao day thanh 5.
        Sense adverb = about.senses().get(0);
        assertEquals(2, adverb.glosses().size(),
                "nghia cua idiom bi tron vao sense - xem PLAN.md 4.2 quy tac 3");
        assertEquals("xung quanh, quanh quẩn, đây đó, rải rác", adverb.glosses().get(0));

        // Sense "gioi tu" chi co 1 nghia; nghia cua '!to be about to' khong duoc lot vao
        Sense preposition = about.senses().get(1);
        assertEquals(1, preposition.glosses().size());
        assertEquals("về", preposition.glosses().get(0));
    }

    @Test
    @DisplayName("Idiom duoc nhan dien du, ke ca idiom nam giua hai khoi '*' (quy tac 5)")
    void collectsAllIdiomsAcrossPosBlocks(@TempDir Path dir) throws IOException {
        Entry about = parseSingle(dir, ABOUT_ENTRY);

        assertEquals(3, about.idioms().size());
        assertEquals("about and about", about.idioms().get(0).phrase());
        assertEquals("to be about", about.idioms().get(1).phrase());
        assertEquals("to be about to", about.idioms().get(2).phrase());

        // '!to be about' co 2 nghia rieng
        assertEquals(2, about.idioms().get(1).glosses().size());
        // '!to be about to' co vi du rieng - idiom cung mang example duoc
        assertEquals(1, about.idioms().get(2).examples().size());
        assertEquals("the train is about to start", about.idioms().get(2).examples().get(0).en());
    }

    @Test
    @DisplayName("Vi du gan dung vao nghia gan nhat phia tren (quy tac 2)")
    void exampleAttachesToNearestGloss(@TempDir Path dir) throws IOException {
        Entry about = parseSingle(dir, ABOUT_ENTRY);
        Sense adverb = about.senses().get(0);

        assertEquals(2, adverb.examples().size());
        assertEquals(0, adverb.examples().get(0).glossIndex(), "vi du 1 minh hoa nghia thu 0");
        assertEquals(1, adverb.examples().get(1).glossIndex(), "vi du 2 minh hoa nghia thu 1");
    }

    @Test
    @DisplayName("Entry khong co dong '*' nao van tao duoc sense voi pos = null (quy tac 6)")
    void entryWithoutPosLine(@TempDir Path dir) throws IOException {
        Entry e = parseSingle(dir, "@foo /fu:/\n- nghia thu nhat\n- nghia thu hai\n");
        assertEquals(1, e.senses().size());
        assertNull(e.senses().get(0).pos());
        assertEquals(2, e.senses().get(0).glosses().size());
    }

    @Test
    @DisplayName("Dong loi dinh dang bi bo qua, KHONG duoc throw (quy tac 9)")
    void malformedLinesAreSkippedNotThrown(@TempDir Path dir) throws IOException {
        Path f = write(dir, """
                @good /gud/
                *  tính từ
                - tốt
                dòng rác không đúng định dạng
                ??? một dòng rác nữa
                @bad /bæd/
                - xấu
                """);
        AnhViet109KParser parser = new AnhViet109KParser();
        List<Entry> entries;
        try (Stream<Entry> s = parser.parse(f, 0)) {
            entries = s.toList();
        }
        assertEquals(2, entries.size(), "van phai parse duoc ca 2 entry");
        assertEquals(2, parser.malformedLineCount(), "phai dem duoc 2 dong loi");
        assertFalse(parser.malformedSamples().isEmpty());
    }

    @Test
    @DisplayName("BOM UTF-8 o dau file duoc bo, khong dinh vao headword dau tien")
    void stripsUtf8Bom(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("bom.txt");
        Files.writeString(f, "﻿@a /ei/\n- loại a\n", StandardCharsets.UTF_8);
        try (Stream<Entry> s = new AnhViet109KParser().parse(f, 0)) {
            Entry e = s.findFirst().orElseThrow();
            assertEquals("a", e.headword(), "BOM chua duoc bo khoi headword");
            assertEquals("a", e.headwordNorm());
        }
    }

    @Test
    @DisplayName("sourceId duoc gan vao moi entry - phuc vu F5 bo sung tai lieu")
    void sourceIdIsPropagated(@TempDir Path dir) throws IOException {
        Path f = write(dir, "@x /x/\n- test\n");
        try (Stream<Entry> s = new AnhViet109KParser().parse(f, 7)) {
            assertEquals(7, s.findFirst().orElseThrow().sourceId());
        }
    }

    @Test
    @DisplayName("canParse nhan dien dung dinh dang")
    void canParseDetectsFormat(@TempDir Path dir) throws IOException {
        AnhViet109KParser parser = new AnhViet109KParser();
        assertTrue(parser.canParse(write(dir, "@about /x/\n- về\n")));
        assertFalse(parser.canParse(write(dir, "day khong phai tu dien\nchi la van ban thuong\n")));
    }

    // ------------------------------------------------------------------

    private static Entry parseSingle(Path dir, String content) throws IOException {
        Path f = write(dir, content);
        try (Stream<Entry> s = new AnhViet109KParser().parse(f, 0)) {
            return s.findFirst().orElseThrow(() -> new AssertionError("khong parse duoc entry nao"));
        }
    }

    private static Path write(Path dir, String content) throws IOException {
        Path f = Files.createTempFile(dir, "dict", ".txt");
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }
}
