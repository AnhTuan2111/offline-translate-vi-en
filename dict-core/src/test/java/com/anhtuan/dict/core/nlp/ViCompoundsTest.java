package com.anhtuan.dict.core.nlp;

import com.anhtuan.dict.core.index.IndexWriter;
import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Sense;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.anhtuan.dict.core.TestEntries.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Danh sach tu ghep tieng Viet rut ra tu chinh tu dien (PLAN.md 7.5). */
class ViCompoundsTest {

    @TempDir
    Path tmp;

    private static Entry withGlosses(String headword, String... glosses) {
        return entry(headword, null, List.of(new Sense("động từ", List.of(glosses), List.of())),
                List.of());
    }

    /** Muoi muc tu, trong do "chăm sóc" lap lai 3 lan con "linh tinh quá" chi 1 lan. */
    private static List<Entry> dictionary() {
        List<Entry> out = new ArrayList<>();
        out.add(withGlosses("care", "trông nom, chăm sóc"));
        out.add(withGlosses("tend", "chăm sóc, săn sóc"));
        out.add(withGlosses("nurse", "chăm sóc người ốm", "chăm sóc"));
        out.add(withGlosses("blah", "linh tinh quá"));
        return out;
    }

    @Test
    @DisplayName("phương án lặp lại nhiều lần mới được coi là từ ghép")
    void onlyRepeatedAlternativesBecomeWords() {
        Set<String> words = IndexWriter.mineCompounds(dictionary(), 3);
        assertTrue(words.contains("chăm sóc"), "xuat hien 3 lan -> la tu ghep");
        assertFalse(words.contains("linh tinh quá"), "xuat hien 1 lan -> chi la cum ngau nhien");
        assertFalse(words.contains("trông nom"), "xuat hien 1 lan");
    }

    @Test
    @DisplayName("gộp âm tiết thành từ, giữ nguyên cả âm tiết rời")
    void expandKeepsSyllablesAndAddsCompounds() {
        ViCompounds words = ViCompounds.of(Set.of("chăm sóc", "sự chăm sóc"));
        List<String> out = words.expand(List.of("sự", "chăm", "sóc"));
        assertTrue(out.containsAll(List.of("sự", "chăm", "sóc")), "am tiet roi phai con");
        assertTrue(out.contains("chăm_sóc"));
        // Khop TAT CA chu khong chi cum dai nhat: nguoi go "chăm sóc" van phai tim ra
        // muc tu ghi "sự chăm sóc".
        assertTrue(out.contains("sự_chăm_sóc"));
    }

    @Test
    @DisplayName("gõ sai chính tả tiếng Việt thì đoán ra từ đúng")
    void suggestsCorrectionForTypos() {
        ViCompounds words = ViCompounds.of(Set.of("chăm sóc", "ngân hàng", "nghiên cứu"));
        // Truoc khi co ham nay, go "cham sok" tra ve slow / sculp / shock va nguoi dung
        // khong he biet minh go sai o dau.
        assertEquals(List.of("chăm sóc"), words.suggest("cham sok", 3));
        assertEquals(List.of("ngân hàng"), words.suggest("ngan hag", 3));
    }

    @Test
    @DisplayName("gõ đúng thì không gợi ý gì")
    void noSuggestionWhenQueryIsCorrect() {
        ViCompounds words = ViCompounds.of(Set.of("chăm sóc"));
        assertTrue(words.suggest("chăm sóc", 3).isEmpty());
        assertTrue(words.suggest("cham soc", 3).isEmpty(), "go khong dau van la go dung");
        assertTrue(words.suggest("sóc", 3).isEmpty(), "mot am tiet thi khong doan");
    }

    @Test
    @DisplayName("ghi ra rồi đọc lại đúng danh sách")
    void roundTrip() {
        Path file = tmp.resolve(ViCompounds.FILE_NAME);
        ViCompounds.write(file, Set.of("chăm sóc", "ngân hàng"));
        ViCompounds loaded = ViCompounds.loadIfPresent(file);
        assertEquals(2, loaded.size());
        assertTrue(loaded.contains("chăm sóc"));

        ViCompounds missing = ViCompounds.loadIfPresent(tmp.resolve("khong-co.txt"));
        assertFalse(missing.isAvailable());
        assertEquals(List.of("a", "b"), missing.expand(List.of("a", "b")), "ban rong khong doi gi");
    }
}
