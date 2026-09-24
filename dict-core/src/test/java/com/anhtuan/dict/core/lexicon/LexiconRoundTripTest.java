package com.anhtuan.dict.core.lexicon;

import com.anhtuan.dict.core.TestEntries;
import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.SegmentKind;
import com.anhtuan.dict.core.pack.PackReader;
import com.anhtuan.dict.core.pack.PackWriter;
import com.anhtuan.dict.core.service.DictionaryGlossEngine;
import com.anhtuan.dict.core.service.LookupService;
import com.anhtuan.dict.core.service.RuleBasedTranslationEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.anhtuan.dict.core.TestEntries.entry;
import static com.anhtuan.dict.core.TestEntries.sense;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bang xac suat dich tu: ghi - doc - va anh huong that len cau dich (PLAN.md 7.4).
 */
class LexiconRoundTripTest {

    @TempDir
    Path tmp;

    private static final List<String> VI_VOCAB = List.of(
            "chính", "phủ", "sự", "cai", "trị", "kế", "hoạch", "sơ", "đồ", "quyết", "định");

    private static Path writeLexicon(Path dir) {
        List<LexiconWriter.EnglishWord> words = List.of(
                new LexiconWriter.EnglishWord("government",
                        List.of("chính", "phủ", "sự", "cai"), List.of(0.41, 0.39, 0.02, 0.01)),
                new LexiconWriter.EnglishWord("plan",
                        List.of("kế", "hoạch", "sơ", "đồ"), List.of(0.41, 0.40, 0.01, 0.01)));
        Path file = dir.resolve(LexiconFormat.FILE_NAME);
        LexiconWriter.write(file, words, VI_VOCAB);
        return file;
    }

    @Test
    @DisplayName("ghi ra rồi đọc lại đúng xác suất")
    void roundTrip() {
        Path file = writeLexicon(tmp);
        try (LexicalPrior prior = LexicalPrior.open(file)) {
            assertTrue(prior.isAvailable());
            assertEquals(2, prior.wordCount());
            // Sai so cua luu 16 bit phai nho hon 1/65535
            assertEquals(0.41, prior.probability("government", "chính"), 0.0001);
            assertEquals(0.39, prior.probability("government", "phủ"), 0.0001);
            assertEquals(0.0, prior.probability("government", "hoạch"), 0.0001);
            assertEquals(0.0, prior.probability("khongcotutunay", "chính"), 0.0001);
        }
    }

    @Test
    @DisplayName("nghĩa hai chữ không bị nghĩa một chữ đè")
    void twoSyllableGlossWins() {
        Path file = writeLexicon(tmp);
        try (LexicalPrior prior = LexicalPrior.open(file)) {
            double chinhPhu = prior.scoreGloss("government", List.of("chính", "phủ"));
            double suCaiTri = prior.scoreGloss("government", List.of("sự", "cai", "trị"));
            double phu = prior.scoreGloss("government", List.of("phủ"));
            assertTrue(chinhPhu > suCaiTri, "chính phủ (" + chinhPhu + ") phai hon sự cai trị (" + suCaiTri + ")");
            assertTrue(chinhPhu > phu, "chia cho can bac hai chu khong chia thang cho so am tiet");
        }
    }

    @Test
    @DisplayName("thiếu file thì trả về bản rỗng, không ném lỗi")
    void missingFileDegradesGracefully() {
        LexicalPrior prior = LexicalPrior.openIfPresent(tmp.resolve("khong-ton-tai.bin"));
        assertFalse(prior.isAvailable());
        assertEquals(0.0, prior.probability("government", "chính"), 0.0);
        prior.close();
    }

    @Test
    @DisplayName("bảng xác suất đổi hẳn nghĩa được chọn trong câu dịch")
    void priorChangesTheChosenSense() {
        // Tu dien xep "sự cai trị" truoc "chính phủ", va "sơ đồ" truoc "kế hoạch" -
        // dung nhu nguon that. Luat ngu phap khong the phan biet, vi ca hai deu la danh tu.
        List<Entry> dict = List.of(
                entry("government", null,
                        List.of(sense("danh từ", "sự cai trị", "chính phủ, nội các")), List.of()),
                entry("plan", null,
                        List.of(sense("danh từ", "sơ đồ, đồ án", "kế hoạch")), List.of()),
                entry("the", null, List.of(sense("mạo từ", "cái, con")), List.of()));

        Path packFile = tmp.resolve("dict.pack");
        PackWriter.write(packFile, dict);
        Path lexFile = writeLexicon(tmp);

        try (PackReader pack = PackReader.open(packFile)) {
            LookupService lookup = new LookupService(pack);
            DictionaryGlossEngine gloss = new DictionaryGlossEngine(lookup, pack.multiWordStarters());

            var without = new RuleBasedTranslationEngine(gloss, lookup);
            assertEquals("Sự cai trị", translate(without, "The government"));

            try (LexicalPrior prior = LexicalPrior.open(lexFile)) {
                var with = new RuleBasedTranslationEngine(gloss, lookup, prior);
                assertEquals("Chính phủ", translate(with, "The government"));
                assertEquals("Kế hoạch", translate(with, "The plan"));
            }
        }
    }

    private static String translate(RuleBasedTranslationEngine engine, String sentence) {
        var segments = engine.translate(sentence);
        assertEquals(SegmentKind.TRANSLATED, segments.getFirst().kind());
        return segments.getFirst().displayGloss();
    }

    @Test
    @DisplayName("TestEntries vẫn dựng được pack để các bộ test khác dùng chung")
    void fixtureStillBuilds() {
        Path packFile = tmp.resolve("mini.pack");
        PackWriter.write(packFile, TestEntries.mini());
        try (PackReader pack = PackReader.open(packFile)) {
            assertEquals(TestEntries.mini().size(), pack.entryCount());
        }
    }
}
