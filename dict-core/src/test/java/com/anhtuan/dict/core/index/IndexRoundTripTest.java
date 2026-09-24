package com.anhtuan.dict.core.index;

import com.anhtuan.dict.core.TestEntries;
import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.pack.PackReader;
import com.anhtuan.dict.core.pack.PackWriter;
import com.anhtuan.dict.core.service.ReverseSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inverted index + tim Viet-&gt;Anh (PLAN.md muc 7, tieu chi nghiem thu M3). */
class IndexRoundTripTest {

    @TempDir
    Path tmp;

    private record Fixture(PackReader pack, InvertedIndex vi, InvertedIndex viNoDiac,
                           InvertedIndex tri, ReverseSearchService search) implements AutoCloseable {
        @Override
        public void close() {
            tri.close(); viNoDiac.close(); vi.close(); pack.close();
        }
    }

    private Fixture build() {
        List<Entry> entries = TestEntries.mini();
        PackWriter.write(tmp.resolve("dict.pack"), entries);
        IndexWriter.build(tmp, entries);

        PackReader pack = PackReader.open(tmp.resolve("dict.pack"));
        InvertedIndex vi = InvertedIndex.open(tmp.resolve(IndexFormat.VI_INDEX));
        InvertedIndex viNo = InvertedIndex.open(tmp.resolve(IndexFormat.VI_NODIAC_INDEX));
        InvertedIndex tri = InvertedIndex.open(tmp.resolve(IndexFormat.TRIGRAM_INDEX));
        return new Fixture(pack, vi, viNo, tri, new ReverseSearchService(pack, vi, viNo, tri));
    }

    @Test
    @DisplayName("postings doc lai dung docId va tan suat")
    void postingsRoundTrip() {
        try (Fixture f = build()) {
            List<int[]> postings = f.vi().postings("chăm");
            assertFalse(postings.isEmpty(), "term 'chăm' phai co trong index");
            for (int[] p : postings) {
                assertTrue(p[0] >= 0 && p[0] < f.pack().entryCount(), "docId nam ngoai pack");
                assertTrue(p[1] >= 1, "tan suat phai >= 1");
            }
            assertEquals(postings.size(), f.vi().docFreq("chăm"));
        }
    }

    @Test
    @DisplayName("docId cua index tro dung entry trong pack")
    void docIdsMatchPackOrdinals() {
        try (Fixture f = build()) {
            int docId = f.vi().postings("ngân").getFirst()[0];
            Entry entry = f.pack().entryAt(docId);
            assertEquals("bank", entry.headwordNorm());
        }
    }

    @Test
    @DisplayName("tim tieng Viet co dau ra dung tu tieng Anh")
    void searchWithDiacritics() {
        try (Fixture f = build()) {
            List<ReverseSearchService.Hit> hits = f.search().searchVietnamese("chăm sóc", 5);
            assertFalse(hits.isEmpty());
            assertEquals("look", hits.getFirst().entry().headwordNorm());
            // Nghia khop nam o dong thanh ngu nen hien cum, khong hien moi chu "look"
            assertEquals("to look after", hits.getFirst().display());
        }
    }

    @Test
    @DisplayName("go KHONG dau ra cung ket qua")
    void searchWithoutDiacritics() {
        try (Fixture f = build()) {
            List<String> withMarks = f.search().searchVietnamese("chăm sóc", 5)
                    .stream().map(h -> h.entry().headwordNorm()).toList();
            List<String> without = f.search().searchVietnamese("cham soc", 5)
                    .stream().map(h -> h.entry().headwordNorm()).toList();
            assertEquals(withMarks, without, "hai duong tim phai cho cung ket qua");
        }
    }

    @Test
    @DisplayName("go sai chinh ta van doan ra tu dung")
    void fuzzyEnglishFindsTypos() {
        try (Fixture f = build()) {
            List<ReverseSearchService.Hit> hits = f.search().fuzzyEnglish("bannk", 3);
            assertFalse(hits.isEmpty());
            assertEquals("bank", hits.getFirst().entry().headwordNorm());
        }
    }

    @Test
    @DisplayName("trigram co dem hai dau de phan biet dau/cuoi tu")
    void trigramsArePadded() {
        assertEquals(List.of("$$g", "$go", "go$", "o$$"), TrigramIndex.trigrams("go"));
    }

    @Test
    @DisplayName("BM25: term hiem duoc cham diem cao hon term pho bien")
    void rareTermsScoreHigher() {
        BM25Scorer scorer = new BM25Scorer(1000, 10);
        assertTrue(scorer.idf(1) > scorer.idf(500));
        assertTrue(scorer.score(1, 3, 10) > scorer.score(1, 1, 10), "tan suat cao hon -> diem cao hon");
    }
}
