package com.anhtuan.dict.importer.cli;

import com.anhtuan.dict.core.index.IndexFormat;
import com.anhtuan.dict.core.lexicon.LexicalPrior;
import com.anhtuan.dict.core.lexicon.LexiconFormat;
import com.anhtuan.dict.core.index.InvertedIndex;
import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.model.SegmentKind;
import com.anhtuan.dict.core.pack.PackReader;
import com.anhtuan.dict.core.service.DictionaryGlossEngine;
import com.anhtuan.dict.core.service.LookupService;
import com.anhtuan.dict.core.service.ReverseSearchService;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Chay lai TOAN BO tieu chi nghiem thu M2/M3/M4 cua PLAN.md tren du lieu that va in
 * dat / khong dat.
 *
 * <p>Ly do co lenh nay thay vi doc so lieu bang mat: hau het loi cua kien truc nay
 * KHONG crash. Sap xep lech mot chut, chuan hoa lech mot chut - app van chay, chi la
 * thinh thoang tra khong ra tu. Phai co mot lenh khang dinh duoc "du lieu nay dung".
 */
final class VerifyCommand {

    private final Path dataDir;
    private final PrintStream out =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private int passed;
    private int failed;

    VerifyCommand(Path dataDir) {
        this.dataDir = dataDir;
    }

    int run() throws Exception {
        Path pack = dataDir.resolve("dict.pack");
        if (!Files.isRegularFile(pack)) {
            out.println("Khong thay " + pack.toAbsolutePath() + " - chay lenh build truoc.");
            return 1;
        }

        long heapBefore = usedHeap();
        try (PackReader reader = PackReader.open(pack);
             InvertedIndex vi = InvertedIndex.open(dataDir.resolve(IndexFormat.VI_INDEX));
             InvertedIndex viNo = InvertedIndex.open(dataDir.resolve(IndexFormat.VI_NODIAC_INDEX));
             InvertedIndex tri = InvertedIndex.open(dataDir.resolve(IndexFormat.TRIGRAM_INDEX))) {

            long heapAfter = usedHeap();
            LookupService lookup = new LookupService(reader);
            Set<String> starters = reader.multiWordStarters();
            DictionaryGlossEngine gloss = new DictionaryGlossEngine(lookup, starters);
            ReverseSearchService search = new ReverseSearchService(reader, vi, viNo, tri);

            section("M2 - dict.pack");
            // Ngan sach da SUA theo so do that, xem PLAN.md muc 3. Muc tieu cu 5,2 MB duoc
            // tinh tren gia thiet nen ca file mot luot (gzip -9 ca file nguon = 3,98 MB).
            // Nen THEO BLOCK de tra cuu ngau nhien duoc thi phai tra gia ~20 diem ty le nen.
            check("dict.pack <= 8,0 MB", Files.size(pack) <= 8.0 * 1024 * 1024,
                    ImporterMain.mb(Files.size(pack)));
            check("108.854 entry", reader.entryCount() == 108_854,
                    String.format("%,d", reader.entryCount()));
            check("KEYS >= 120.000 khoa (co khoa bi danh)", reader.keyCount() >= 120_000,
                    String.format("%,d khoa cho %,d entry", reader.keyCount(), reader.entryCount()));
            check("heap sau khi mo pack < 5 MB", heapAfter - heapBefore < 5 * 1024 * 1024,
                    ImporterMain.mb(Math.max(0, heapAfter - heapBefore)));
            check("phraseStarters ~5.949 tu", starters.size() > 4_000,
                    String.format("%,d tu", starters.size()));

            Entry give = reader.lookup("give up").orElse(null);
            check("lookup(\"give up\") -> entry @give  [khoa bi danh]",
                    give != null && give.headwordNorm().equals("give"),
                    give == null ? "khong tim thay" : "@" + give.headword());
            check("lookup(\"look after\") -> entry @look",
                    reader.lookup("look after").map(e -> e.headwordNorm().equals("look")).orElse(false),
                    reader.lookup("look after").map(Entry::headword).orElse("khong tim thay"));
            check("lookup(\"about to\") KHONG ton tai (gioi han cua nguon)",
                    reader.lookup("about to").isEmpty(), "dung nhu PLAN.md muc 3 da doi chinh");

            Entry about = reader.lookup("about").orElse(null);
            check("entry @about co ipa + >= 2 sense + thanh ngu",
                    about != null && about.ipa() != null && about.senses().size() >= 2
                            && !about.idioms().isEmpty(),
                    about == null ? "khong tim thay"
                            : "/" + about.ipa() + "/, " + about.senses().size() + " sense, "
                              + about.idioms().size() + " idiom");

            double avgUs = benchLookup(reader);
            check("tra 1 tu < 1 ms (10.000 luot ngau nhien)", avgUs < 1000,
                    String.format(Locale.ROOT, "trung binh %.1f us", avgUs));

            section("M3 - index + Viet->Anh");
            long viSize = Files.size(dataDir.resolve(IndexFormat.VI_INDEX));
            long triSize = Files.size(dataDir.resolve(IndexFormat.TRIGRAM_INDEX));
            check("vi.idx <= 2,4 MB", viSize <= 2.4 * 1024 * 1024, ImporterMain.mb(viSize));
            check("tri.idx <= 2,2 MB", triSize <= 2.2 * 1024 * 1024, ImporterMain.mb(triSize));
            long totalData = Files.size(pack) + viSize + triSize
                    + Files.size(dataDir.resolve(IndexFormat.VI_NODIAC_INDEX));
            check("tong du lieu <= 14,5 MB", totalData <= 14.5 * 1024 * 1024,
                    ImporterMain.mb(totalData));

            checkSearch(search, "chăm sóc", List.of("care", "look after", "nurse"));
            checkSearch(search, "cham soc", List.of("care", "look after", "nurse"));
            checkSearch(search, "ngân hàng", List.of("bank"));

            long t0 = System.nanoTime();
            search.searchVietnamese("chăm sóc", 20);
            long searchMs = (System.nanoTime() - t0) / 1_000_000;
            check("search < 30 ms", searchMs < 30, searchMs + " ms");

            List<ReverseSearchService.Hit> fuzzy = search.fuzzyEnglish("aboout", 5);
            check("go sai \"aboout\" -> \"about\" o vi tri so 1",
                    !fuzzy.isEmpty() && fuzzy.getFirst().entry().headwordNorm().equals("about"),
                    fuzzy.isEmpty() ? "khong co ket qua"
                            : fuzzy.stream().limit(3).map(h -> h.entry().headword()).toList().toString());

            section("M4 - dich cau");
            checkPhrase(gloss, "He gave up his job.", "give up", "gave up");
            checkPhrase(gloss, "She looks after them.", "look after", "looks after");
            checkLemma(gloss, "She went running yesterday.", "went", "running");
            checkAboutTo(gloss);
            checkOffsets(gloss, "He gave up his job.");

            section("Dich ca cau bang luat");
            LexicalPrior prior =
                    LexicalPrior.openIfPresent(dataDir.resolve(LexiconFormat.FILE_NAME));
            check("co bang xac suat dich tu (lex.bin)", prior.isAvailable(),
                    prior.isAvailable() ? String.format("%,d tu tieng Anh", prior.wordCount())
                            : "thieu - chay lenh lexicon de sinh");
            var sentenceEngine = new com.anhtuan.dict.core.service.RuleBasedTranslationEngine(
                    gloss, lookup, prior);
            checkSentence(sentenceEngine, "She went to the market yesterday",
                    new String[] {"Cô ấy", "đã", "chợ"});
            checkSentence(sentenceEngine, "The weather is very cold today",
                    new String[] {"Thời tiết", "rất"});
            checkSentence(sentenceEngine, "We will not go to school tomorrow",
                    new String[] {"sẽ không", "trường học"});
            checkSentence(sentenceEngine, "This system does not work well",
                    new String[] {"Hệ thống này", "không"});
            checkSentence(sentenceEngine, "The teacher gave me a very good book",
                    new String[] {"Giáo viên", "cho tôi", "rất tốt"});

            section("M4 - hieu nang dich cau");
            String twentyWords = "The government decided to carry out a new plan because the old "
                    + "system could not keep up with the growing number of users";
            long t1 = System.nanoTime();
            List<Segment> segs = gloss.translate(twentyWords);
            long transMs = (System.nanoTime() - t1) / 1_000_000;
            long resolved = segs.stream()
                    .filter(s -> s.kind() == SegmentKind.WORD || s.kind() == SegmentKind.PHRASE)
                    .count();
            long words = segs.stream().filter(s -> s.kind() != SegmentKind.PUNCT).count();
            check("dich cau 24 tu < 50 ms", transMs < 50, transMs + " ms");
            check("ty le tra ra nghia >= 90%", resolved * 100 >= words * 90,
                    resolved + "/" + words + " doan");
            out.println();
            out.println("  Cau vao : " + twentyWords);
            out.println("  Dich ra : "
                    + sentenceEngine.translate(twentyWords).getFirst().displayGloss());

            out.println();
            out.printf("=== %d dat / %d khong dat ===%n", passed, failed);
        }
        return failed;
    }

    /**
     * Kiem tra cau dich CHUA cac manh bat buoc, khong so sanh nguyen van.
     *
     * <p>So sanh nguyen van se bien bo test thanh cai bay: doi mot nghia trong tu dien la
     * do het. Cai can khang dinh la BO LUAT chay dung - dao trat tu, chen dau hieu thi,
     * chon dung tu loai - chu khong phai tung chu mot.
     */
    private void checkSentence(com.anhtuan.dict.core.service.RuleBasedTranslationEngine engine,
                               String english, String[] mustContain) {
        String vi = engine.translate(english).getFirst().displayGloss();
        boolean ok = true;
        for (String piece : mustContain) ok &= vi != null && vi.contains(piece);
        check("\"" + trim(english) + "\"", ok, vi);
    }

    private static String trim(String s) {
        return s.length() <= 34 ? s : s.substring(0, 31) + "...";
    }

    // ------------------------------------------------------------------ cac phep kiem tra

    private void checkSearch(ReverseSearchService search, String query, List<String> expected) {
        List<ReverseSearchService.Hit> hits = search.searchVietnamese(query, 5);
        List<String> heads = hits.stream().map(h -> h.entry().headwordNorm()).toList();
        boolean ok = expected.stream().anyMatch(heads::contains);
        check("go \"" + query + "\" -> co " + expected + " trong top 5", ok, heads.toString());
    }

    private void checkPhrase(DictionaryGlossEngine gloss, String sentence,
                             String expectedKey, String expectedSource) {
        List<Segment> segs = gloss.translate(sentence);
        Segment phrase = segs.stream()
                .filter(s -> s.kind() == SegmentKind.PHRASE)
                .findFirst().orElse(null);
        boolean ok = phrase != null
                && phrase.sourceText().equalsIgnoreCase(expectedSource)
                && phrase.displayGloss() != null;
        check("\"" + sentence + "\" nhan dung cum " + expectedKey, ok,
                phrase == null ? "khong nhan ra cum nao"
                        : phrase.sourceText() + " = " + phrase.displayGloss());
    }

    private void checkLemma(DictionaryGlossEngine gloss, String sentence, String... words) {
        List<Segment> segs = gloss.translate(sentence);
        StringBuilder detail = new StringBuilder();
        boolean ok = true;
        for (String w : words) {
            Segment seg = segs.stream()
                    .filter(s -> s.sourceText().equalsIgnoreCase(w))
                    .findFirst().orElse(null);
            boolean found = seg != null && seg.kind() == SegmentKind.WORD
                    && !seg.candidates().isEmpty();
            ok &= found;
            detail.append(w).append(" -> ")
                  .append(found ? seg.candidates().getFirst().headword() : "TRUOT")
                  .append("  ");
        }
        check("\"" + sentence + "\" lemma hoa dung", ok, detail.toString().trim());
    }

    private void checkAboutTo(DictionaryGlossEngine gloss) {
        List<Segment> segs = gloss.translate("He is about to leave.");
        boolean aboutAlone = segs.stream().anyMatch(
                s -> s.sourceText().equals("about") && s.kind() == SegmentKind.WORD);
        boolean toAlone = segs.stream().anyMatch(
                s -> s.sourceText().equals("to") && s.kind() == SegmentKind.WORD);
        check("\"He is about to leave.\" tach rieng about + to, khong loi",
                aboutAlone && toAlone,
                segs.stream().filter(s -> s.kind() != SegmentKind.PUNCT)
                        .map(Segment::sourceText).toList().toString());
    }

    /** Bat bien quan trong nhat cua M4: noi cac segment lai phai ra DUNG cau goc. */
    private void checkOffsets(DictionaryGlossEngine gloss, String sentence) {
        List<Segment> segs = gloss.translate(sentence);
        StringBuilder sb = new StringBuilder();
        boolean contiguous = true;
        int expectStart = 0;
        for (Segment s : segs) {
            if (s.startOffset() != expectStart) contiguous = false;
            expectStart = s.endOffset();
            sb.append(sentence, s.startOffset(), s.endOffset());
        }
        check("offset phu kin cau goc, khong ho khong chong lan",
                contiguous && sb.toString().equals(sentence) && expectStart == sentence.length(),
                contiguous ? "khop" : "co lo hong");
    }

    private double benchLookup(PackReader reader) {
        Random rnd = new Random(42);
        List<String> sample = reader.prefixScan("a", 2000);
        if (sample.isEmpty()) return Double.MAX_VALUE;
        for (int i = 0; i < 2000; i++) reader.lookup(sample.get(rnd.nextInt(sample.size())));  // warm-up
        long t0 = System.nanoTime();
        int n = 10_000;
        for (int i = 0; i < n; i++) reader.lookup(sample.get(rnd.nextInt(sample.size())));
        return (System.nanoTime() - t0) / 1000.0 / n;
    }

    // ------------------------------------------------------------------ in ket qua

    private void section(String title) {
        out.println();
        out.println("--- " + title + " ---");
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else failed++;
        out.printf("  [%s] %-52s %s%n", ok ? "DAT" : "TRUOT", name, detail == null ? "" : detail);
    }

    private static long usedHeap() {
        System.gc();
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
