package com.anhtuan.dict.importer.cli;

import com.anhtuan.dict.core.index.IndexFormat;
import com.anhtuan.dict.core.index.IndexWriter;
import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.pack.PackWriter;
import com.anhtuan.dict.core.source.DictSource;
import com.anhtuan.dict.core.source.SourceCatalog;
import com.anhtuan.dict.core.spi.DictParser;
import com.anhtuan.dict.importer.parser.TsvDictParser;
import com.anhtuan.dict.importer.parser.AnhViet109KParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * CLI build du lieu. Chay LUC BUILD, khong nam trong app cuoi.
 *
 * <pre>
 *   java -jar dict-importer.jar stats  &lt;file.txt&gt;
 *   java -jar dict-importer.jar dump   &lt;file.txt&gt; &lt;headword&gt;
 *   java -jar dict-importer.jar build  &lt;file.txt&gt; &lt;outDir&gt;
 *   java -jar dict-importer.jar verify &lt;outDir&gt;
 * </pre>
 *
 * <p>Lenh {@code verify} chay lai toan bo tieu chi nghiem thu M2/M3/M4 tren du lieu
 * that va in ra dat/khong dat - khong phai doc so lieu bang mat.
 */
public final class ImporterMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            System.exit(2);
        }
        String cmd = args[0];

        if (cmd.equals("verify")) {
            int failed = new VerifyCommand(Path.of(args[1])).run();
            System.exit(failed == 0 ? 0 : 1);
            return;
        }
        if (cmd.equals("lexicon")) {
            if (args.length < 5) { usage(); System.exit(2); }
            int maxSentences = args.length >= 6 ? Integer.parseInt(args[5]) : 1_200_000;
            new LexiconCommand().run(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]),
                    Path.of(args[4]), maxSentences);
            return;
        }
        if (cmd.equals("query")) {
            if (args.length < 4) { usage(); System.exit(2); }
            new SearchCommand(Path.of(args[1])).run(args[2], String.join(" ",
                    List.of(args).subList(3, args.length)));
            return;
        }

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
            case "build" -> {
                if (args.length < 3) { usage(); System.exit(2); }
                List<Path> extra = new ArrayList<>();
                for (int i = 3; i < args.length; i++) extra.add(Path.of(args[i]));
                build(source, Path.of(args[2]), extra);
            }
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
        String target = TextNormalizer.normalizeHeadword(headword);
        try (Stream<Entry> stream = parser.parse(source, 0)) {
            stream.filter(e -> e.headwordNorm().equals(target))
                  .forEach(ImporterMain::print);
        }
    }

    /** Cac dinh dang doc duoc. Them dinh dang moi = them mot dong o day (PLAN.md F5). */
    private static List<DictParser> parsers() {
        return List.of(new AnhViet109KParser(), new TsvDictParser());
    }

    /** Sinh dict.pack + index + sources.tsv vao {@code outDir}, tu mot hoac nhieu nguon. */
    private static void build(Path source, Path outDir, List<Path> extraSources) throws Exception {
        Files.createDirectories(outDir);

        List<Path> allSources = new ArrayList<>();
        allSources.add(source);
        allSources.addAll(extraSources);

        long t0 = System.nanoTime();
        List<Entry> entries = new ArrayList<>(120_000);
        List<DictSource> catalog = new ArrayList<>(allSources.size());

        for (int id = 0; id < allSources.size(); id++) {
            Path file = allSources.get(id);
            if (!Files.isRegularFile(file)) {
                System.err.println("Bo qua, khong thay file: " + file);
                continue;
            }
            DictParser parser = parsers().stream()
                    .filter(pp -> pp.canParse(file))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "khong doc duoc dinh dang cua " + file));

            System.out.println("Doc " + file.getFileName() + "  [" + parser.formatId() + "] ...");
            int before = entries.size();
            try (Stream<Entry> stream = parser.parse(file, id)) {
                stream.forEach(entries::add);
            }
            int count = entries.size() - before;
            long malformed = parser instanceof AnhViet109KParser av ? av.malformedLineCount() : 0;
            System.out.printf("  %,d entry%s%n", count,
                    malformed > 0 ? String.format(", %,d dong loi", malformed) : "");
            // Uu tien: bang thuat ngu truyen them o dong lenh dung TREN tu dien nen (id 0).
            // Truyen mot bang thuat ngu vao la mot quyet dinh co y cua nguoi dung, con tu dien
            // nen la nen chung - de "priority = id" thi nen luon thang va bang thuat ngu thanh
            // vo nghia. Da mat mot lan do lai toan bo tai lieu vi quen sua tay file nay.
            int priority = id == 0 ? allSources.size() - 1 : id - 1;
            catalog.add(new DictSource(id, displayName(file), parser.formatId(),
                    file.getFileName().toString(), count, true, priority));
        }
        System.out.printf("  tong %,d entry tu %d nguon, %,d ms%n",
                entries.size(), catalog.size(), ms(t0));

        System.out.println("Ghi dict.pack ...");
        long t1 = System.nanoTime();
        PackWriter.Stats packStats = PackWriter.write(outDir.resolve("dict.pack"), entries);
        System.out.printf("  %,d entry / %,d khoa / %,d block%n",
                packStats.entryCount(), packStats.keyCount(), packStats.blockCount());
        System.out.printf("  %s (tho %s, ty le nen %.1f%%), %,d ms%n",
                mb(packStats.fileSize()), mb(packStats.rawSize()),
                packStats.compressionRatio() * 100, ms(t1));

        System.out.println("Ghi index ...");
        long t2 = System.nanoTime();
        List<IndexWriter.Stats> idx = IndexWriter.build(outDir, entries);
        for (IndexWriter.Stats s : idx) {
            System.out.printf("  %-14s %,7d term / %,10d cap postings / %s%n",
                    s.fileName(), s.termCount(), s.postingPairs(), mb(s.fileSize()));
        }
        System.out.printf("  %,d ms%n", ms(t2));

        java.nio.file.Path wordList = outDir.resolve(
                com.anhtuan.dict.core.nlp.ViCompounds.FILE_NAME);
        long wordListSize = Files.exists(wordList) ? Files.size(wordList) : 0;
        System.out.printf("  %-14s %,7d tu ghep tieng Viet / %s%n",
                com.anhtuan.dict.core.nlp.ViCompounds.FILE_NAME,
                com.anhtuan.dict.core.nlp.ViCompounds.loadIfPresent(wordList).size(),
                mb(wordListSize));

        SourceCatalog.of(catalog).save(outDir.resolve(SourceCatalog.FILE_NAME));
        System.out.printf("  %-14s %,7d nguon%n", SourceCatalog.FILE_NAME, catalog.size());

        long total = packStats.fileSize() + wordListSize;
        for (IndexWriter.Stats s : idx) total += s.fileSize();
        System.out.printf("TONG DU LIEU : %s  (ngan sach PLAN.md: <= 11 MB)%n", mb(total));
    }

    private static void print(Entry e) {
        System.out.println("@ " + e.headword() + (e.ipa() == null ? "" : "  /" + e.ipa() + "/"));
        for (var s : e.senses()) {
            System.out.println("  * " + (s.pos() == null ? "(khong ro tu loai)" : s.pos()));
            for (String g : s.glosses()) System.out.println("      - " + g);
            for (var ex : s.examples()) {
                System.out.println("      = " + ex.en() + "  ==>  "
                        + (ex.vi() == null ? "(khong co ban dich)" : ex.vi()));
            }
        }
        for (var i : e.idioms()) {
            System.out.println("  ! " + i.phrase());
            for (String g : i.glosses()) System.out.println("      - " + g);
            for (var ex : i.examples()) {
                System.out.println("      = " + ex.en() + "  ==>  "
                        + (ex.vi() == null ? "(khong co ban dich)" : ex.vi()));
            }
        }
        System.out.println();
    }

    /** Ten hien cho nguoi dung: bo duoi file, giu nguyen phan con lai. */
    private static String displayName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    static String mb(long bytes) {
        return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
    }

    private static void usage() {
        System.err.println("""
                Cach dung:
                  stats  <file.txt>              in thong ke, doi chieu PLAN.md muc 3
                  dump   <file.txt> <headword>   in mot muc tu
                  build  <file.txt> <outDir> [nguon2 nguon3 ...]
                                                 sinh dict.pack + """
                + IndexFormat.VI_INDEX + " + " + IndexFormat.VI_NODIAC_INDEX
                + " + " + IndexFormat.TRIGRAM_INDEX + """

                  verify <outDir>                chay tieu chi nghiem thu M2/M3/M4
                  query  <outDir> en|vi|sent <truy van>   soi ket qua tra cuu
                  lexicon <tudien.txt> <kho.en> <kho.vi> <outDir> [soCau]
                                                 hoc bang xac suat dich tu
                """);
    }

    private ImporterMain() {}
}
