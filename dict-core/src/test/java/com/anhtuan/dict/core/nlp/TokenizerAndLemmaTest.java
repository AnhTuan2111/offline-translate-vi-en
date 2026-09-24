package com.anhtuan.dict.core.nlp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tach tu va dua ve nguyen the (PLAN.md 8.1 buoc 1, 8.2). */
class TokenizerAndLemmaTest {

    @Test
    @DisplayName("BAT BIEN: noi cac token lai phai ra dung cau goc")
    void tokensCoverTheWholeInput() {
        String text = "He gave up his job, didn't he?  Yes - the acid-proof one.";
        StringBuilder sb = new StringBuilder();
        int expectedStart = 0;
        for (Tokenizer.Token t : Tokenizer.tokenize(text)) {
            assertEquals(expectedStart, t.start(), "token phai noi tiep nhau, khong duoc ho");
            assertEquals(t.text(), text.substring(t.start(), t.end()));
            expectedStart = t.end();
            sb.append(t.text());
        }
        assertEquals(text, sb.toString());
        assertEquals(text.length(), expectedStart);
    }

    @Test
    @DisplayName("dau nhay va gach ngang nam TRONG tu, dau cau thi khong")
    void apostropheAndHyphenStayInsideWords() {
        List<String> words = Tokenizer.tokenize("didn't acid-proof end.").stream()
                .filter(Tokenizer.Token::isWord)
                .map(Tokenizer.Token::text)
                .toList();
        assertEquals(List.of("didn't", "acid-proof", "end"), words);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "went, go",          // bat quy tac
            "gave, give",
            "better, good",
            "mice, mouse",
            "children, child",
            "worst, bad"
    })
    @DisplayName("bang bat quy tac cho ra lemma that, khong phai than tu cat cut")
    void irregularFormsResolve(String word, String expected) {
        assertEquals(expected, Lemmatizer.lemma(word));
    }

    @ParameterizedTest(name = "{0} phai co ung vien {1}")
    @CsvSource({
            "running, run",      // hoan nguyen phu am doi
            "stopped, stop",
            "studies, study",    // -ies -> y
            "studied, study",
            "making, make",      // them lai 'e' bi rung
            "moved, move",
            "quickly, quick",
            "boxes, box"
    })
    @DisplayName("luat hau to SINH UNG VIEN, viec chon do tu dien quyet dinh")
    void suffixRulesProduceTheRightCandidate(String word, String expected) {
        List<String> candidates = Lemmatizer.candidates(word);
        assertTrue(candidates.contains(expected),
                word + " -> " + candidates + " thieu " + expected);
    }

    @Test
    @DisplayName("KHONG duoc hanh xu nhu Porter stemmer")
    void neverProducesNonWords() {
        // Porter stemmer cho ra "runn" roi tra tu dien truot sach. Ung vien dung phai
        // dung dau danh sach de LookupService thu no truoc.
        assertEquals("run", Lemmatizer.candidates("running").getFirst());
        assertFalse(Lemmatizer.candidates("running").isEmpty());
    }

    @Test
    @DisplayName("tach token tieng Viet giu nguyen dau, dung chung cho build va truy van")
    void vietnameseTokensKeepDiacritics() {
        assertEquals(List.of("sự", "chăm", "sóc"), TextNormalizer.splitTokens("sự chăm sóc;"));
        assertEquals(List.of("su", "cham", "soc"),
                TextNormalizer.splitTokens(TextNormalizer.removeDiacritics("sự chăm sóc")));
    }
}
