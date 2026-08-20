package com.anhtuan.dict.importer.parser;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Example;
import com.anhtuan.dict.core.model.Idiom;
import com.anhtuan.dict.core.model.Sense;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.spi.DictParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Parser cho dinh dang tu dien kieu Lac Viet / StarDict text (anhviet109K.txt).
 * Dac ta van pham va quy tac ngu nghia: PLAN.md muc 4.
 *
 * <p>Day la mot MAY TRANG THAI doc theo dong, phan nhanh bang ky tu dau dong:
 * <pre>
 *   '@'  headword moi   -> dong entry cu, mo entry moi
 *   '*'  tu loai        -> dong sense cu, mo sense moi, THOAT che do idiom
 *   '-'  nghia          -> vao idiom dang mo NEU dang trong che do idiom, nguoc lai vao sense
 *   '='  vi du          -> gan vao idiom dang mo hoac sense dang mo
 *   '!'  thanh ngu      -> dong idiom cu, mo idiom moi, VAO che do idiom
 * </pre>
 *
 * <p>Cai bay lon nhat la quy tac cua {@code '!'}: sau khi gap '!', cac dong '-'
 * KHONG con thuoc ve sense dang mo nua. Quen dieu nay thi nghia cua thanh ngu
 * se bi tron vao nghia cua tu goc - sai du lieu ma khong he bao loi.
 */
public final class AnhViet109KParser implements DictParser {

    public static final String FORMAT_ID = "anhviet109k";

    /** Dem cac dong khong khop van pham. Kiem tra sau khi parse de biet du lieu co la khong. */
    private final AtomicLong malformedLines = new AtomicLong();
    private final List<String> malformedSamples = new ArrayList<>();

    @Override
    public String formatId() {
        return FORMAT_ID;
    }

    @Override
    public boolean canParse(Path source) {
        if (source == null || !Files.isRegularFile(source)) return false;
        try (BufferedReader r = newReader(source)) {
            for (int i = 0; i < 20; i++) {
                String line = r.readLine();
                if (line == null) break;
                if (TextNormalizer.stripBom(line).startsWith("@")) return true;
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
            reader = newReader(source);
        } catch (IOException e) {
            throw new UncheckedIOException("khong mo duoc file nguon: " + source, e);
        }
        EntryIterator it = new EntryIterator(reader, sourceId);
        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.NONNULL), false)
                .onClose(() -> {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    public long malformedLineCount() {
        return malformedLines.get();
    }

    /** Toi da 20 dong loi dau tien, de xem nhanh du lieu hong kieu gi. */
    public List<String> malformedSamples() {
        synchronized (malformedSamples) {
            return List.copyOf(malformedSamples);
        }
    }

    private static BufferedReader newReader(Path source) throws IOException {
        // UTF_8 chuan; BOM o dau file duoc TextNormalizer.stripBom xu ly o dong dau tien.
        return Files.newBufferedReader(source, StandardCharsets.UTF_8);
    }

    private void reportMalformed(long lineNo, String line) {
        malformedLines.incrementAndGet();
        synchronized (malformedSamples) {
            if (malformedSamples.size() < 20) {
                malformedSamples.add("dong " + lineNo + ": " + line);
            }
        }
    }

    // ------------------------------------------------------------------
    // May trang thai
    // ------------------------------------------------------------------

    private final class EntryIterator implements Iterator<Entry> {
        private final BufferedReader reader;
        private final int sourceId;

        private String pendingHeadwordLine;   // dong '@' cua entry TIEP THEO, doc truoc roi giu lai
        private Entry next;
        private boolean exhausted;
        private long lineNo;

        EntryIterator(BufferedReader reader, int sourceId) {
            this.reader = reader;
            this.sourceId = sourceId;
        }

        @Override
        public boolean hasNext() {
            if (next == null && !exhausted) next = readEntry();
            return next != null;
        }

        @Override
        public Entry next() {
            if (!hasNext()) throw new java.util.NoSuchElementException();
            Entry e = next;
            next = null;
            return e;
        }

        private Entry readEntry() {
            try {
                String headwordLine = pendingHeadwordLine;
                pendingHeadwordLine = null;

                // Bo qua rac cho toi khi gap dong '@' dau tien
                while (headwordLine == null) {
                    String line = readLine();
                    if (line == null) { exhausted = true; return null; }
                    if (line.startsWith("@")) {
                        headwordLine = line;
                    } else if (!line.isBlank()) {
                        reportMalformed(lineNo, line);
                    }
                }
                return parseEntryBody(headwordLine);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private String readLine() throws IOException {
            String line = reader.readLine();
            if (line == null) return null;
            lineNo++;
            if (lineNo == 1) line = TextNormalizer.stripBom(line);
            return line;
        }

        private Entry parseEntryBody(String headwordLine) throws IOException {
            HeadwordParts hp = splitHeadword(headwordLine.substring(1));

            List<Sense> senses = new ArrayList<>(2);
            List<Idiom> idioms = new ArrayList<>(0);
            List<String> crossRefs = new ArrayList<>(0);

            // Sense dang mo
            String currentPos = null;
            List<String> senseGlosses = new ArrayList<>(4);
            List<Example> senseExamples = new ArrayList<>(0);
            boolean senseOpen = false;

            // Idiom dang mo. idiomOpen == true nghia la dang o "che do idiom":
            // moi dong '-' va '=' deu chay vao idiom chu khong vao sense.
            String idiomPhrase = null;
            List<String> idiomGlosses = new ArrayList<>(2);
            List<Example> idiomExamples = new ArrayList<>(0);
            boolean idiomOpen = false;

            String line;
            boolean endOfEntry = false;
            while (!endOfEntry && (line = readLine()) != null) {
                if (line.isBlank()) continue;

                char marker = line.charAt(0);
                String rest = line.substring(1).trim();

                switch (marker) {
                    case '@' -> {
                        pendingHeadwordLine = line;                     // de danh cho entry sau
                        endOfEntry = true;
                    }
                    case '*' -> {                                       // tu loai moi
                        if (idiomOpen) {                                // quy tac 5: '!' co the nam giua hai khoi '*'
                            idioms.add(new Idiom(idiomPhrase, idiomGlosses, idiomExamples));
                            idiomGlosses = new ArrayList<>(2);
                            idiomExamples = new ArrayList<>(0);
                            idiomOpen = false;
                        }
                        if (senseOpen) {
                            senses.add(new Sense(currentPos, senseGlosses, senseExamples));
                            senseGlosses = new ArrayList<>(4);
                            senseExamples = new ArrayList<>(0);
                        }
                        currentPos = rest.isEmpty() ? null : rest;
                        senseOpen = true;
                    }
                    case '!' -> {                                       // thanh ngu moi
                        if (idiomOpen) {
                            idioms.add(new Idiom(idiomPhrase, idiomGlosses, idiomExamples));
                            idiomGlosses = new ArrayList<>(2);
                            idiomExamples = new ArrayList<>(0);
                        }
                        idiomPhrase = TextNormalizer.collapseSpaces(rest.replace('_', ' '));
                        idiomOpen = true;
                    }
                    case '-' -> {                                       // nghia
                        if (!rest.isEmpty()) {
                            if (idiomOpen) {
                                idiomGlosses.add(rest);                 // quy tac 3 - cai bay
                            } else {
                                senseOpen = true;                       // quy tac 6: entry khong co '*' nao
                                senseGlosses.add(rest);
                            }
                        }
                    }
                    case '=' -> {                                       // vi du
                        Example ex = parseExample(rest, idiomOpen ? idiomGlosses.size() : senseGlosses.size());
                        if (ex != null) {
                            if (idiomOpen) idiomExamples.add(ex);
                            else {
                                senseOpen = true;
                                senseExamples.add(ex);
                            }
                        }
                    }
                    case '+' -> {                                       // tham chieu cheo
                        if (!rest.isEmpty()) crossRefs.add(rest);
                    }
                    default -> reportMalformed(lineNo, line);           // quy tac 9: bo qua, khong throw
                }
            }

            if (idiomOpen) idioms.add(new Idiom(idiomPhrase, idiomGlosses, idiomExamples));
            if (senseOpen) senses.add(new Sense(currentPos, senseGlosses, senseExamples));

            return new Entry(hp.headword(), TextNormalizer.normalizeHeadword(hp.headword()),
                    hp.ipa(), hp.variant(), senses, idioms, crossRefs, sourceId);
        }
    }

    // ------------------------------------------------------------------
    // Tach dong
    // ------------------------------------------------------------------

    record HeadwordParts(String headword, String ipa, String variant) {}

    /**
     * Tach dong headword thanh headword + phien am + dang viet khac.
     *
     * <p>Dong headword co 3 dang trong file nguon:
     * <pre>
     *   @about /ə'baut/                                       -> head, ipa
     *   @and/or                                               -> head (tu no chua '/'), khong ipa
     *   @acid-proof /'æsid'pru:f/ (acid-resisting) /'æsidri.../ -> head, ipa, variant
     * </pre>
     *
     * <p>QUY TAC: headword ket thuc o dau '/' DAU TIEN co khoang trang dung truoc.
     * Khong duoc lay dau '/' cuoi cung: 7.128 dong co dang bien the, lay tu phai sang
     * se nuot ca "(acid-resisting)" vao headword va lam phong so cum nhieu tu
     * tu 11.956 len 18.862.
     */
    static HeadwordParts splitHeadword(String raw) {
        String s = TextNormalizer.stripBom(raw).trim();

        int slash = indexOfSpaceSlash(s);
        if (slash < 0) {
            return new HeadwordParts(normalizeDisplay(s), null, null);   // "and/or", "ajutage"
        }

        String head = s.substring(0, slash).trim();
        if (head.isEmpty()) {
            return new HeadwordParts(normalizeDisplay(s), null, null);
        }

        String tail = s.substring(slash + 1);
        int close = tail.indexOf('/');
        String ipa = (close < 0) ? tail.trim() : tail.substring(0, close).trim();
        String rest = (close < 0) ? "" : tail.substring(close + 1).trim();

        return new HeadwordParts(normalizeDisplay(head),
                ipa.isEmpty() ? null : ipa,
                extractVariant(rest));
    }

    /** Vi tri dau '/' dau tien ma truoc no la khoang trang. -1 neu khong co. */
    private static int indexOfSpaceSlash(String s) {
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) == '/' && Character.isWhitespace(s.charAt(i - 1))) return i;
        }
        return -1;
    }

    /** Lay "acid-resisting" tu phan con lai "(acid-resisting) /'æsidri'zistiɳ/". */
    private static String extractVariant(String rest) {
        if (rest.isEmpty()) return null;
        int open = rest.indexOf('(');
        int close = rest.indexOf(')', open + 1);
        if (open < 0 || close < 0) return null;
        String v = normalizeDisplay(rest.substring(open + 1, close).trim());
        return v.isEmpty() ? null : v;
    }

    /** Dang hien thi: bo dau noi '_' cua dinh dang goc, gop khoang trang. Giu nguyen hoa/thuong. */
    static String normalizeDisplay(String s) {
        return TextNormalizer.collapseSpaces(s.replace('_', ' '));
    }

    /**
     * Tach "he is about+ anh ta quanh day" thanh en + vi.
     * Quy tac 8: khong co '+' thi van giu vi du, vi = null.
     */
    static Example parseExample(String rest, int glossCount) {
        if (rest.isEmpty()) return null;
        int plus = rest.indexOf('+');
        int glossIndex = glossCount - 1;                 // gan vao dong '-' gan nhat phia tren
        if (plus < 0) {
            return new Example(normalizeDisplay(rest), null, glossIndex);
        }
        String en = normalizeDisplay(rest.substring(0, plus).trim());
        String vi = rest.substring(plus + 1).trim();
        if (en.isEmpty()) return null;
        return new Example(en, vi.isEmpty() ? null : vi, glossIndex);
    }
}
