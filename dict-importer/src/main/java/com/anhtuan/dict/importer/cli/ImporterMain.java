package com.anhtuan.dict.importer.cli;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.importer.parser.AnhViet109KParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * CLI build du lieu. Chay LUC BUILD, khong nam trong app cuoi.
 *
 * <pre>
 *   java -jar dict-importer.jar stats  &lt;file.txt&gt;
 *   java -jar dict-importer.jar dump   &lt;file.txt&gt; &lt;headword&gt;
 *   java -jar dict-importer.jar build  &lt;file.txt&gt; &lt;outDir&gt;     (TODO M2/M3)
 * </pre>
 */
public final class ImporterMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            System.exit(2);
        }
        String cmd = args[0];
        Path source = Path.of(args[1]);
        if (!Files.isRegularFile(source)) {
            System.err.println("Khong tim thay file: " + source.toAbsolutePath());
            System.exit(2);
        }

        switch (cmd) {
            case "stats" -> stats(source);
            case "dump" -> {
                if (args.length < 3) { usage(); System.exit(2); }
                dump(source, args[2]);
            }
            case "build" -> throw new UnsupportedOperationException(
                    "TODO(M2/M3): goi PackWriter.write va IndexWriter.build");
            default -> { usage(); System.exit(2); }
        }
    }

    /** Doi chieu voi so lieu da do trong PLAN.md muc 3 - lech la parser sai. */
    private static void stats(Path source) {
        AnhViet109KParser parser = new AnhViet109KParser();
        long entries = 0, senses = 0, glosses = 0, examples = 0, idioms = 0, multiWord = 0;

        long t0 = System.nanoTime();
        try (Stream<Entry> stream = parser.parse(source, 0)) {
            for (Entry e : (Iterable<Entry>) stream::iterator) {
                entries++;
                if (e.isMultiWord()) multiWord++;
                senses += e.senses().size();
                idioms += e.idioms().size();
                for (var s : e.senses()) {
                    glosses += s.glosses().size();
                    examples += s.examples().size();
                }
                for (var i : e.idioms()) {
                    glosses += i.glosses().size();
                    examples += i.examples().size();
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("entry        : %,d%n", entries);
        System.out.printf("  cum nhieu tu: %,d%n", multiWord);
        System.out.printf("sense        : %,d%n", senses);
        System.out.printf("gloss        : %,d%n", glosses);
        System.out.printf("example      : %,d%n", examples);
        System.out.printf("idiom        : %,d%n", idioms);
        System.out.printf("dong loi     : %,d%n", parser.malformedLineCount());
        System.out.printf("thoi gian    : %,d ms%n", ms);
        parser.malformedSamples().forEach(s -> System.out.println("  ! " + s));
    }

    private static void dump(Path source, String headword) {
        AnhViet109KParser parser = new AnhViet109KParser();
        String target = com.anhtuan.dict.core.nlp.TextNormalizer.normalizeHeadword(headword);
        try (Stream<Entry> stream = parser.parse(source, 0)) {
            stream.filter(e -> e.headwordNorm().equals(target))
                  .forEach(ImporterMain::print);
        }
    }

    private static void print(Entry e) {
        System.out.println("@ " + e.headword() + (e.ipa() == null ? "" : "  /" + e.ipa() + "/"));
        for (var s : e.senses()) {
            System.out.println("  * " + (s.pos() == null ? "(khong ro tu loai)" : s.pos()));
            for (String g : s.glosses()) System.out.println("      - " + g);
            for (var ex : s.examples()) {
                System.out.println("      = " + ex.en() + "  ==>  " + (ex.vi() == null ? "(khong co ban dich)" : ex.vi()));
            }
        }
        for (var i : e.idioms()) {
            System.out.println("  ! " + i.phrase());
            for (String g : i.glosses()) System.out.println("      - " + g);
            for (var ex : i.examples()) {
                System.out.println("      = " + ex.en() + "  ==>  " + (ex.vi() == null ? "(khong co ban dich)" : ex.vi()));
            }
        }
        System.out.println();
    }

    private static void usage() {
        System.err.println("""
                Cach dung:
                  stats <file.txt>              in thong ke, doi chieu PLAN.md muc 3
                  dump  <file.txt> <headword>   in mot muc tu
                  build <file.txt> <outDir>     sinh dict.pack + *.idx  (TODO M2/M3)
                """);
    }

    private ImporterMain() {}
}
