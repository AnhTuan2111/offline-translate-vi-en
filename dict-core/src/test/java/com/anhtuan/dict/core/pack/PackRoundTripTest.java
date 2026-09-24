package com.anhtuan.dict.core.pack;

import com.anhtuan.dict.core.TestEntries;
import com.anhtuan.dict.core.model.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ghi ra roi doc lai dict.pack (PLAN.md muc 6, tieu chi nghiem thu M2).
 *
 * <p>Day la loai loi nguy hiem nhat cua ca kien truc: mot varint lech, mot quy tac sap xep
 * khac nhau giua luc ghi va luc doc - khong crash, khong bao loi, chi la thinh thoang
 * tra khong ra tu. Vi vay test o day so sanh TOAN BO entry, khong chi dem so luong.
 */
class PackRoundTripTest {

    @TempDir
    Path tmp;

    private PackReader writeAndOpen(List<Entry> entries) {
        Path pack = tmp.resolve("dict.pack");
        PackWriter.write(pack, entries);
        return PackReader.open(pack);
    }

    @Test
    @DisplayName("doc lai duoc dung tung entry da ghi")
    void roundTripPreservesEveryField() {
        List<Entry> entries = TestEntries.mini();
        try (PackReader reader = writeAndOpen(entries)) {
            assertEquals(entries.size(), reader.entryCount());

            Entry give = reader.lookup("give").orElseThrow();
            Entry original = entries.stream()
                    .filter(e -> e.headword().equals("give")).findFirst().orElseThrow();
            assertEquals(original, give, "entry doc ra phai bang HET entry goc");
        }
    }

    @Test
    @DisplayName("khoa bi danh: tra \"give up\" ra muc tu \"give\"")
    void aliasKeysPointToParentEntry() {
        try (PackReader reader = writeAndOpen(TestEntries.mini())) {
            // "give up" khong phai headword, no chi la dong "!to give up" trong "@give".
            assertTrue(reader.lookup("give up").isPresent(), "thieu khoa bi danh bo tien to 'to'");
            assertEquals("give", reader.lookup("give up").orElseThrow().headwordNorm());
            assertEquals("give", reader.lookup("to give up").orElseThrow().headwordNorm());
            assertEquals("look", reader.lookup("look after").orElseThrow().headwordNorm());

            assertTrue(reader.keyCount() > reader.entryCount(),
                    "so khoa phai lon hon so entry vi co bi danh");
        }
    }

    @Test
    @DisplayName("muc tu dong am: \"bank\" tra ve ca hai nghia")
    void homographsAreAllReturned() {
        try (PackReader reader = writeAndOpen(TestEntries.mini())) {
            List<Entry> banks = reader.lookupAll("bank");
            assertEquals(2, banks.size());
            assertTrue(banks.stream().anyMatch(
                    e -> e.senses().getFirst().glosses().getFirst().contains("bờ sông")));
            assertTrue(banks.stream().anyMatch(
                    e -> e.senses().getFirst().glosses().getFirst().contains("ngân hàng")));
        }
    }

    @Test
    @DisplayName("khoá trùng: lookup() luôn trả về mục đầu tiên, không phụ thuộc số khoá")
    void lookupIsStableAcrossDuplicateKeys() {
        // Loi that da gap: binary search roi xuong BAT KY muc nao trong nhom khoa trung, va
        // cai nao con tuy tong so khoa trong file. Them mot nguon tu dien lam doi ket qua tra
        // cua mot tu khong lien quan.
        List<Entry> entries = new java.util.ArrayList<>(TestEntries.mini());
        try (PackReader reader = writeAndOpen(entries)) {
            assertEquals("bờ sông, bờ đê",
                    reader.lookup("bank").orElseThrow().senses().getFirst().glosses().getFirst());
        }
        // Them 50 muc tu khong lien quan -> so khoa doi han, ket qua tra "bank" phai y nguyen
        for (int i = 0; i < 50; i++) {
            entries.add(TestEntries.entry("zzz" + i, null,
                    List.of(TestEntries.sense("danh từ", "rác " + i)), List.of()));
        }
        try (PackReader reader = writeAndOpen(entries)) {
            assertEquals("bờ sông, bờ đê",
                    reader.lookup("bank").orElseThrow().senses().getFirst().glosses().getFirst());
        }
    }

    @Test
    @DisplayName("contains() khong can giai nen block")
    void containsWorksOnNormalizedKeys() {
        try (PackReader reader = writeAndOpen(TestEntries.mini())) {
            assertTrue(reader.contains("a la carte"));
            assertFalse(reader.contains("about to"));   // gioi han cua nguon, khong phai bug
        }
    }

    @Test
    @DisplayName("prefixScan tra ve khoa da sap xep dung thu tu byte UTF-8")
    void prefixScanIsOrdered() {
        try (PackReader reader = writeAndOpen(TestEntries.mini())) {
            List<String> keys = reader.prefixScan("g", 10);
            // Thu tu byte UTF-8: dau cach (0x20) nho hon moi chu cai, nen "give in" dung
            // truoc "gives" chu khong phai sau - day chinh la cho String::compareTo se sai.
            assertEquals(List.of("give", "give in", "give up", "go"), keys);
        }
    }

    @Test
    @DisplayName("tu mo dau cum duoc gom du cho PhraseProbe")
    void multiWordStartersCoverAliases() {
        try (PackReader reader = writeAndOpen(TestEntries.mini())) {
            var starters = reader.multiWordStarters();
            assertTrue(starters.contains("give"));
            assertTrue(starters.contains("look"));
            assertTrue(starters.contains("to"));        // tu "to give up"
            assertFalse(starters.contains("run"));      // "run" khong mo dau cum nao
        }
    }

    @Test
    @DisplayName("close() nha file ngay, ghi de duoc tren Windows")
    void closeUnmapsImmediately() throws Exception {
        Path pack = tmp.resolve("dict.pack");
        PackWriter.write(pack, TestEntries.mini());
        PackReader reader = PackReader.open(pack);
        reader.close();

        // Neu van con MappedByteBuffer cho GC don thi dong nay nem AccessDeniedException.
        // Do la ly do PackReader dung Arena thay vi FileChannel.map thong thuong.
        PackWriter.write(pack, TestEntries.mini());
        assertTrue(Files.size(pack) > 0);
    }
}
