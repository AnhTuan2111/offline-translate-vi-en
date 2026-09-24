package com.anhtuan.dict.importer.cli;

import com.anhtuan.dict.core.lexicon.LexicalPrior;
import com.anhtuan.dict.core.lexicon.LexiconFormat;
import com.anhtuan.dict.core.lexicon.LexiconWriter;
import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Idiom;
import com.anhtuan.dict.core.model.Sense;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.importer.lexicon.IbmModel1Trainer;
import com.anhtuan.dict.importer.parser.AnhViet109KParser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Hoc bang xac suat dich tu tu kho cau song ngu, ghi ra {@code lex.bin}.
 *
 * <pre>
 *   lexicon &lt;tudien.txt&gt; &lt;kho.en&gt; &lt;kho.vi&gt; &lt;outDir&gt; [soCauToiDa]
 * </pre>
 *
 * <p>Chay LUC BUILD va chi chay mot lan. Kho song ngu (hang tram MB) khong di kem ung dung;
 * san pham duy nhat la file {@code lex.bin} vai MB nam trong thu muc du lieu.
 *
 * <p>Pham vi hoc duoc thu hep bang chinh tu dien: chi hoc nhung tu tieng Anh la MUC TU va
 * nhung am tiet tieng Viet CO TRONG cac dong nghia. Hoc ca kho thi vua ton bo nho vua vo ich,
 * vi phan du ra khong bao gio duoc tra den.
 */
final class LexiconCommand {

    private final PrintStream out =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    void run(Path dictFile, Path corpusEn, Path corpusVi, Path outDir, int maxSentences) {
        out.println("== Doc tu dien de biet can hoc nhung tu nao ==");
        Set<String> allowedEn = new HashSet<>(120_000);
        Set<String> allowedVi = new HashSet<>(40_000);

        AnhViet109KParser parser = new AnhViet109KParser();
        try (Stream<Entry> stream = parser.parse(dictFile, 0)) {
            for (Entry e : (Iterable<Entry>) stream::iterator) {
                collectEnglish(allowedEn, e.headwordNorm());
                for (Idiom idiom : e.idioms()) {
                    // Cum thanh ngu cung dong gop tu don: "to give up" -> give, up
                    for (String w : TextNormalizer.normalizeHeadword(idiom.phrase()).split(" ")) {
                        collectEnglish(allowedEn, w);
                    }
                    for (String g : idiom.glosses()) allowedVi.addAll(TextNormalizer.splitTokens(g));
                }
                for (Sense s : e.senses()) {
                    for (String g : s.glosses()) allowedVi.addAll(TextNormalizer.splitTokens(g));
                }
            }
        }
        out.printf("  %,d tu tieng Anh / %,d am tiet tieng Viet%n", allowedEn.size(), allowedVi.size());

        out.println();
        out.println("== Hoc bang IBM Model 1 ==");
        long t0 = System.nanoTime();
        IbmModel1Trainer.Config cfg = new IbmModel1Trainer.Config(
                maxSentences, 20, 4, 16, 24);
        IbmModel1Trainer.Result result = IbmModel1Trainer.train(
                corpusEn, corpusVi, allowedEn, allowedVi, cfg, s -> out.println("  " + s));
        out.printf("  tong thoi gian hoc: %,d giay%n", (System.nanoTime() - t0) / 1_000_000_000);

        out.println();
        out.println("== Ghi " + LexiconFormat.FILE_NAME + " ==");
        Path target = outDir.resolve(LexiconFormat.FILE_NAME);
        LexiconWriter.Stats stats = LexiconWriter.write(target, result.words(), result.viVocabulary());
        out.printf("  %,d tu / %,d cap / %s%n", stats.enCount(), stats.pairCount(),
                ImporterMain.mb(stats.fileSize()));

        out.println();
        out.println("== Thu vai tu (so trong ngoac la xac suat) ==");
        try (LexicalPrior prior = LexicalPrior.open(target)) {
            for (String w : List.of("government", "plan", "school", "system", "work", "keep",
                    "book", "old", "carry", "decide", "number", "user", "weather")) {
                out.printf("  %-11s -> %s%n", w, String.join("  ", prior.topTranslations(w, 6)));
            }
        }
    }

    /** Chi lay tu don, thuan chu cai - cum tu va ky hieu khong hoc duoc kieu nay. */
    private static void collectEnglish(Set<String> target, String word) {
        if (word == null || word.isEmpty() || word.length() > 30) return;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!(c >= 'a' && c <= 'z') && c != '\'') return;
        }
        target.add(word);
    }
}
