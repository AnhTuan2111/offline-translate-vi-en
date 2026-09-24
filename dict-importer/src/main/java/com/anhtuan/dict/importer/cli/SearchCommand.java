package com.anhtuan.dict.importer.cli;

import com.anhtuan.dict.core.index.IndexFormat;
import com.anhtuan.dict.core.index.InvertedIndex;
import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.pack.PackReader;
import com.anhtuan.dict.core.service.DictionaryGlossEngine;
import com.anhtuan.dict.core.service.LookupService;
import com.anhtuan.dict.core.service.ReverseSearchService;
import com.anhtuan.dict.core.service.RuleBasedTranslationEngine;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Soi du lieu da build tu dong lenh, khong can mo UI:
 * <pre>
 *   query  &lt;outDir&gt; en   &lt;tu tieng Anh&gt;
 *   query  &lt;outDir&gt; vi   &lt;nghia tieng Viet&gt;
 *   query  &lt;outDir&gt; sent &lt;ca cau&gt;
 * </pre>
 *
 * <p>Cong cu nay sinh ra vi mot ly do rat thuc te: khi xep hang tra ve ket qua la,
 * doan mo doan bang mat khong bao gio ra nguyen nhan. Phai xem duoc DIEM tung ung vien.
 */
final class SearchCommand {

    private final Path dataDir;
    private final PrintStream out =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    SearchCommand(Path dataDir) {
        this.dataDir = dataDir;
    }

    void run(String mode, String query) {
        try (PackReader pack = PackReader.open(dataDir.resolve("dict.pack"));
             InvertedIndex vi = InvertedIndex.open(dataDir.resolve(IndexFormat.VI_INDEX));
             InvertedIndex viNo = InvertedIndex.open(dataDir.resolve(IndexFormat.VI_NODIAC_INDEX));
             InvertedIndex tri = InvertedIndex.open(dataDir.resolve(IndexFormat.TRIGRAM_INDEX))) {

            LookupService lookup = new LookupService(pack);
            switch (mode) {
                case "en" -> {
                    var entries = lookup.lookupAll(query);
                    if (entries.isEmpty()) {
                        out.println("Khong co \"" + query + "\". Goi y gan nhat:");
                        new ReverseSearchService(pack, vi, viNo, tri).fuzzyEnglish(query, 5)
                                .forEach(h -> out.printf("  %-20s %.3f  %s%n",
                                        h.display(), h.score(), h.matchedGloss()));
                    } else {
                        entries.forEach(e -> {
                            out.println("@ " + e.headword()
                                    + (e.ipa() == null ? "" : "  /" + e.ipa() + "/"));
                            e.senses().forEach(s -> {
                                out.println("  * " + (s.pos() == null ? "?" : s.pos()));
                                s.glosses().forEach(g -> out.println("      - " + g));
                            });
                            e.idioms().forEach(i -> out.println("  ! " + i.phrase() + " = "
                                    + (i.glosses().isEmpty() ? "" : i.glosses().getFirst())));
                        });
                    }
                }
                case "vi" -> {
                    List<ReverseSearchService.Hit> hits =
                            new ReverseSearchService(pack, vi, viNo, tri).searchVietnamese(query, 15);
                    if (hits.isEmpty()) out.println("khong co ket qua");
                    for (int i = 0; i < hits.size(); i++) {
                        ReverseSearchService.Hit h = hits.get(i);
                        out.printf(Locale.ROOT, "  %2d. %-24s %8.3f  %s%n",
                                i + 1, h.display(), h.score(), h.matchedGloss());
                    }
                }
                case "sent" -> {
                    var glossEngine = new DictionaryGlossEngine(lookup, pack.multiWordStarters());
                    var ruleEngine = new RuleBasedTranslationEngine(glossEngine, lookup);

                    out.println("  BAN DICH (luat):");
                    out.println("    " + ruleEngine.translate(query).getFirst().displayGloss());
                    out.println();
                    out.println("  CHU GIAI TUNG CUM:");
                    for (Segment s : glossEngine.translate(query)) {
                        if (s.sourceText().isBlank()) continue;
                        out.printf("    %-18s %-9s %s%n", s.sourceText(), s.kind(),
                                s.displayGloss() == null ? "" : s.displayGloss());
                    }
                }
                default -> out.println("mode phai la en | vi | sent");
            }
        }
    }
}
