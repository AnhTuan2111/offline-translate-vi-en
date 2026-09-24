package com.anhtuan.dict.core.service;

import com.anhtuan.dict.core.TestEntries;
import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.model.SegmentKind;
import com.anhtuan.dict.core.pack.PackReader;
import com.anhtuan.dict.core.pack.PackWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chu giai cau theo cum (PLAN.md 8.1, tieu chi nghiem thu M4). */
class GlossEngineTest {

    @TempDir
    Path tmp;

    private PackReader pack;
    private DictionaryGlossEngine engine;

    @BeforeEach
    void setUp() {
        Path file = tmp.resolve("dict.pack");
        PackWriter.write(file, TestEntries.mini());
        pack = PackReader.open(file);
        engine = new DictionaryGlossEngine(new LookupService(pack), pack.multiWordStarters());
    }

    @AfterEach
    void tearDown() {
        pack.close();
    }

    private Segment segmentOf(List<Segment> segments, String source) {
        return segments.stream()
                .filter(s -> s.sourceText().equalsIgnoreCase(source))
                .findFirst().orElseThrow(() -> new AssertionError("khong co doan \"" + source + "\""));
    }

    @Test
    @DisplayName("\"He gave up his job\" nhan ra cum give up qua bi danh + lemma")
    void recognisesPhrasalVerbInPastTense() {
        List<Segment> segments = engine.translate("He gave up his job.");
        Segment phrase = segmentOf(segments, "gave up");
        assertEquals(SegmentKind.PHRASE, phrase.kind());
        assertEquals("bỏ, từ bỏ", phrase.displayGloss());
        assertTrue(phrase.candidates().size() > 1, "phai co nghia thay the de nguoi dung doi");
    }

    @Test
    @DisplayName("longest-match: \"look after\" khong bi cat thanh look + after")
    void longestMatchWins() {
        List<Segment> segments = engine.translate("She looks after them");
        Segment phrase = segmentOf(segments, "looks after");
        assertEquals(SegmentKind.PHRASE, phrase.kind());
        assertEquals("chăm sóc, trông nom", phrase.displayGloss());
    }

    @Test
    @DisplayName("muc tu chi co tham chieu cheo van phai ra nghia: went -> go")
    void crossReferenceOnlyEntryFallsThroughToLemma() {
        // "@went" ton tai nhung khong co dong nghia nao. Neu dung lai o day thi UI in ra o trong.
        Segment went = segmentOf(engine.translate("She went home"), "went");
        assertEquals(SegmentKind.WORD, went.kind());
        assertNotNull(went.displayGloss());
        assertEquals("go", went.candidates().getFirst().headword());
    }

    @Test
    @DisplayName("\"about to\" khong co trong nguon -> tra rieng tung tu, khong loi")
    void missingPhraseDegradesGracefully() {
        List<Segment> segments = engine.translate("He is about to leave");
        assertTrue(segments.stream().noneMatch(s -> s.kind() == SegmentKind.PHRASE));
        assertEquals(SegmentKind.UNKNOWN, segmentOf(segments, "about").kind());
    }

    @Test
    @DisplayName("BAT BIEN: cac doan phu kin cau goc theo dung offset")
    void segmentsTileTheSourceExactly() {
        String sentence = "He gave up his job, then went home.";
        StringBuilder sb = new StringBuilder();
        int expectedStart = 0;
        for (Segment s : engine.translate(sentence)) {
            assertEquals(expectedStart, s.startOffset());
            assertEquals(s.sourceText(), sentence.substring(s.startOffset(), s.endOffset()));
            expectedStart = s.endOffset();
            sb.append(s.sourceText());
        }
        assertEquals(sentence, sb.toString());
    }

    @Test
    @DisplayName("tu khong co trong tu dien giu nguyen, khong nem exception")
    void unknownWordsAreKept() {
        Segment seg = segmentOf(engine.translate("zzzblah"), "zzzblah");
        assertEquals(SegmentKind.UNKNOWN, seg.kind());
        assertTrue(seg.candidates().isEmpty());
        assertEquals("zzzblah", DictionaryGlossEngine.flatten(engine.translate("zzzblah")));
    }
}
