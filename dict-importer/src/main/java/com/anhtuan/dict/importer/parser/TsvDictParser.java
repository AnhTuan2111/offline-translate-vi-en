package com.anhtuan.dict.importer.parser;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Sense;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.spi.DictParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Doc tu dien dang bang: moi dong mot muc tu, cac cot ngan bang TAB (PLAN.md F5, M7).
 *
 * <pre>
 *   tu-tieng-anh  &lt;TAB&gt;  nghia tieng viet          [&lt;TAB&gt; tu loai]
 *   deadline      &lt;TAB&gt;  hạn chót, thời hạn cuối    &lt;TAB&gt; danh từ
 * </pre>
 *
 * <p>Day la dinh dang thu hai cua du an, va chon TSV la co y: no la thu ma nguoi dung
 * <b>tu lam duoc</b>. Muon them mot bang thuat ngu chuyen nganh thi chi can mo Excel, go
 * hai cot, luu sang .tsv - khong phai hoc dinh dang nao ca. Dinh dang phuc tap hon
 * (StarDict, DSL) de sau, va cung chi can viet them mot class implement {@link DictParser},
 * KHONG dung den dict-core (day chinh la ly do co cong SPI nay).
 *
 * <p>Nhieu nghia thi ngan bang dau phay ngay trong mot o - giong het cach nguon 109K viet,
 * nho vay moi thu phia sau (tach phuong an, bang xac suat, rut tu ghep) dung y nguyen.
 */
public final class TsvDictParser implements DictParser {

    public static final String FORMAT_ID = "tsv";

    @Override
    public String formatId() {
        return FORMAT_ID;
    }

    @Override
    public boolean canParse(Path source) {
        if (source == null || !Files.isRegularFile(source)) return false;
        String name = source.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".tsv") && !name.endsWith(".txt")) return false;
        try (BufferedReader r = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 20; i++) {
                String line = r.readLine();
                if (line == null) break;
                line = TextNormalizer.stripBom(line);
                if (line.isBlank() || line.charAt(0) == '#') continue;
                // Nguon kieu 109K bat dau bang '@' - dung nham parser la hong ca du lieu.
                if (line.charAt(0) == '@') return false;
                return line.indexOf('\t') > 0;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    @Override
    public Stream<Entry> parse(Path source, int sourceId) {
        final BufferedReader reader;
        try {
            reader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("khong mo duoc " + source, e);
        }
        return reader.lines()
                .map(TextNormalizer::stripBom)
                .filter(line -> !line.isBlank() && line.charAt(0) != '#')
                .map(line -> toEntry(line, sourceId))
                .filter(java.util.Objects::nonNull)
                .onClose(() -> {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    /** Dong sai dinh dang tra ve null va bi bo qua - khong duoc nem loi (quy tac 9, PLAN.md 4.2). */
    static Entry toEntry(String line, int sourceId) {
        String[] columns = line.split("\t", -1);
        if (columns.length < 2) return null;
        String headword = columns[0].trim();
        String gloss = columns[1].trim();
        if (headword.isEmpty() || gloss.isEmpty()) return null;
        String pos = columns.length >= 3 && !columns[2].isBlank() ? columns[2].trim() : null;

        return new Entry(headword, TextNormalizer.normalizeHeadword(headword), null, null,
                List.of(new Sense(pos, List.of(gloss), List.of())),
                List.of(), List.of(), sourceId);
    }
}
