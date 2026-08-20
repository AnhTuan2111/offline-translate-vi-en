package com.anhtuan.dict.importer.parser;

import com.anhtuan.dict.core.model.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Nghiem thu M1 tren FILE THAT.
 *
 * Doi chieu voi so lieu da do va ghi trong PLAN.md muc 3. Neu mot con so lech,
 * nghia la parser hieu sai van pham - day la loai loi im lang nguy hiem nhat cua
 * du an nay, nen no phai duoc chan bang test chu khong phai bang mat thuong.
 *
 * Test tu dong BO QUA neu khong tim thay file (may khac, CI), nho vay
 * "mvn test" van xanh o moi noi.
 */
class RealDictionaryFileTest {

    /**
     * So lieu chuan, do lai ngay 2026-08-19 SAU KHI sua quy tac tach headword.
     * Con so ban dau (108.853 / 11.941) sai vi hai ly do:
     *   - grep '^@' bo sot dong dau tien do co BOM
     *   - quy tac tach IPA cu lay dau '/' cuoi cung nen nuot ca phan bien the
     *     "(acid-resisting)" vao headword, lam phong so cum tu 11.956 len 18.862
     */
    private static final int EXPECTED_ENTRIES = 108_854;
    private static final int EXPECTED_MULTIWORD = 11_956;
    private static final int EXPECTED_IDIOMS = 9_791;
    /** Con lai chu yeu la 1.202 dong '/' lac - du lieu nguon hong that, khong sua duoc. */
    private static final int MAX_MALFORMED = 1_400;

    @Test
    @DisplayName("Parse file that: dung 108.853 entry, khong throw")
    void parsesRealFileWithExpectedCounts() {
        Path source = locateDictionary().orElse(null);
        assumeTrue(source != null, "khong tim thay anhviet109K.txt - bo qua test nay");

        AnhViet109KParser parser = new AnhViet109KParser();
        int entries = 0, multiWord = 0, idioms = 0, glosses = 0, examples = 0;

        long t0 = System.nanoTime();
        try (Stream<Entry> stream = parser.parse(source, 0)) {
            for (Entry e : (Iterable<Entry>) stream::iterator) {
                entries++;
                if (e.isMultiWord()) multiWord++;
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

        System.out.printf("""
                === Parse anhviet109K.txt ===
                entry         : %,d
                  cum nhieu tu: %,d
                gloss         : %,d
                example       : %,d
                idiom         : %,d
                dong loi      : %,d
                thoi gian     : %,d ms
                """, entries, multiWord, glosses, examples, idioms, parser.malformedLineCount(), ms);
        parser.malformedSamples().forEach(s -> System.out.println("  ! " + s));

        assertEquals(EXPECTED_ENTRIES, entries, "so entry lech so voi PLAN.md muc 3");
        assertEquals(EXPECTED_MULTIWORD, multiWord, "so cum nhieu tu lech so voi PLAN.md muc 3");
        assertEquals(EXPECTED_IDIOMS, idioms, "so idiom lech so voi PLAN.md muc 3");
        assertTrue(parser.malformedLineCount() < MAX_MALFORMED,
                "qua nhieu dong loi (" + parser.malformedLineCount() + ") - parser co the sai van pham");
    }

    @Test
    @DisplayName("Entry '@about' trong file that duoc parse dung")
    void parsesAboutEntryFromRealFile() {
        Path source = locateDictionary().orElse(null);
        assumeTrue(source != null, "khong tim thay anhviet109K.txt - bo qua test nay");

        Entry about;
        try (Stream<Entry> stream = new AnhViet109KParser().parse(source, 0)) {
            about = stream.filter(e -> e.headwordNorm().equals("about"))
                          .findFirst().orElseThrow();
        }

        assertEquals("ə'baut", about.ipa());
        assertEquals(3, about.senses().size(), "'about' co dung 3 tu loai trong file that");
        assertEquals("phó từ", about.senses().get(0).pos());
        assertEquals("giới từ", about.senses().get(1).pos());
        assertEquals("ngoại động từ", about.senses().get(2).pos());
        assertEquals(5, about.idioms().size(), "'about' co dung 5 thanh ngu trong file that");
        assertFalse(about.isMultiWord());
    }

    @Test
    @DisplayName("Dong headword co bien the duoc tach dung, khong nuot vao headword")
    void parsesVariantHeadword() {
        Path source = locateDictionary().orElse(null);
        assumeTrue(source != null, "khong tim thay anhviet109K.txt - bo qua test nay");

        Entry e;
        try (Stream<Entry> stream = new AnhViet109KParser().parse(source, 0)) {
            e = stream.filter(x -> x.headwordNorm().equals("acid-proof"))
                      .findFirst().orElseThrow();
        }
        assertEquals("acid-proof", e.headword(), "bien the bi nuot vao headword");
        assertEquals("'æsid'pru:f", e.ipa());
        assertEquals("acid-resisting", e.variant());
        assertFalse(e.isMultiWord(), "'acid-proof' la tu don, khong duoc tinh la cum");
    }

    @Test
    @DisplayName("Dong '+' duoc giu lam tham chieu cheo, khong bi coi la rac")
    void capturesCrossReferences() {
        Path source = locateDictionary().orElse(null);
        assumeTrue(source != null, "khong tim thay anhviet109K.txt - bo qua test nay");

        long withRefs;
        try (Stream<Entry> stream = new AnhViet109KParser().parse(source, 0)) {
            withRefs = stream.filter(e -> !e.crossRefs().isEmpty()).count();
        }
        assertTrue(withRefs > 1_000,
                "chi thay " + withRefs + " entry co tham chieu cheo, mong doi hon 1.000");
    }

    /** Tim file tu dien: data/ truoc, roi thu muc goc repo, di nguoc len toi da 4 cap. */
    static Optional<Path> locateDictionary() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent()) {
            for (String candidate : new String[]{"data/anhviet109K.txt", "anhviet109K.txt"}) {
                Path p = dir.resolve(candidate);
                if (Files.isRegularFile(p)) return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
