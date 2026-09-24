package com.anhtuan.dict.core.service;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.SegmentKind;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.pack.PackReader;
import com.anhtuan.dict.core.pack.PackWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.anhtuan.dict.core.TestEntries.entry;
import static com.anhtuan.dict.core.TestEntries.idiom;
import static com.anhtuan.dict.core.TestEntries.sense;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dich ca cau bang luat.
 *
 * <p>Tu dien rieng cho bo test nay, co chu chep lai ba cai bay that cua nguon:
 * {@code @school} co hai nhom danh tu ma nhom "đàn cá" dung truoc, {@code @reading} chi
 * co tu loai danh tu, va nghia nao cung la mot chum dong nghia dai loang ngoang.
 */
class SentenceTranslationTest {

    @TempDir
    Path tmp;

    private PackReader pack;
    private RuleBasedTranslationEngine engine;

    private static List<Entry> dictionary() {
        return List.of(
                entry("go", "gou", List.of(sense("động từ", "đi, đi đến, đi tới")), List.of()),
                entry("market", null, List.of(sense("danh từ", "chợ, thị trường")), List.of()),
                entry("system", null, List.of(sense("danh từ", "hệ thống, mạng lưới")), List.of()),
                entry("old", null, List.of(sense("tính từ", "già, cũ, xưa")), List.of()),
                entry("book", null,
                        List.of(sense("danh từ", "sách, quyển sách"),
                                sense("động từ", "đặt trước, giữ chỗ")), List.of()),
                entry("good", null, List.of(sense("tính từ", "tốt, hay, lành")), List.of()),
                entry("job", null, List.of(sense("danh từ", "việc, việc làm, công việc")), List.of()),
                entry("give", "giv",
                        List.of(sense("động từ", "cho, biếu, tặng")),
                        List.of(idiom("to give up", "bỏ, từ bỏ"))),
                entry("work", null,
                        List.of(sense("danh từ", "sự làm việc"),
                                sense("động từ", "làm việc, hoạt động")), List.of()),
                // Bay 1: nhom nghia it duoc viet ky dung TRUOC nhom hay dung
                entry("school", null,
                        List.of(sense("danh từ", "đàn cá", "bầy cá"),
                                sense("danh từ", "trường học", "học đường", "trường sở", "buổi học")),
                        List.of()),
                // Bay 2: dang chia co muc tu rieng va chi mang tu loai danh tu
                entry("reading", null, List.of(sense("danh từ", "sự đọc, sự đọc sách")), List.of()),
                entry("read", null, List.of(sense("động từ", "đọc, đọc sách")), List.of()),
                // Bay 3: cum nhieu tu la DANH TU, khong phai cum dong tu nhu trong nguon 109K
                entry("use case", null, List.of(sense("danh từ", "ca sử dụng")), List.of()));
    }

    @BeforeEach
    void setUp() {
        Path file = tmp.resolve("dict.pack");
        PackWriter.write(file, dictionary());
        pack = PackReader.open(file);
        LookupService lookup = new LookupService(pack);
        engine = new RuleBasedTranslationEngine(
                new DictionaryGlossEngine(lookup, pack.multiWordStarters()), lookup);
    }

    @AfterEach
    void tearDown() {
        pack.close();
    }

    private String translate(String sentence) {
        var segments = engine.translate(sentence);
        assertEquals(1, segments.size(), "engine dich ca cau phai tra ve DUNG mot doan");
        assertEquals(SegmentKind.TRANSLATED, segments.getFirst().kind());
        return segments.getFirst().displayGloss();
    }

    @Test
    @DisplayName("trật tự danh ngữ đảo ngược: the old system -> hệ thống cũ")
    void nounPhraseIsReordered() {
        // Mao tu bien mat, tinh tu ra sau danh tu - hai khac biet lon nhat giua hai thu tieng.
        assertEquals("Hệ thống già", translate("The old system"));
        assertEquals("Sách này", translate("This book"));
    }

    @Test
    @DisplayName("sở hữu ra sau danh từ: his job -> việc của anh ấy")
    void possessiveMovesAfterNoun() {
        assertTrue(translate("He gave up his job").contains("việc của anh ấy"),
                translate("He gave up his job"));
    }

    @Test
    @DisplayName("thì quá khứ sinh ra chữ \"đã\"")
    void pastTenseAddsMarker() {
        assertEquals("Anh ấy đã đi đến chợ", translate("He went to the market"));
    }

    @Test
    @DisplayName("phủ định gộp với động từ tình thái: could not -> không thể")
    void negationMergesWithModal() {
        assertTrue(translate("The system could not work").startsWith("Hệ thống không thể"),
                translate("The system could not work"));
    }

    @Test
    @DisplayName("chọn nghĩa theo từ loại: work sau \"could not\" là động từ")
    void partOfSpeechDecidesTheMeaning() {
        assertTrue(translate("The system could not work").endsWith("làm việc"),
                "phai lay nghia dong tu \"làm việc\", khong phai danh tu \"sự làm việc\"");
    }

    @Test
    @DisplayName("câu mệnh lệnh: từ đầu câu có nghĩa động từ thì là động từ")
    void sentenceInitialWordIsImperative() {
        // Van phong de bai va tai lieu ky thuat gan nhu toan cau menh lenh. Khong co luat nay
        // thi "Book" lay nghia danh tu "sách".
        assertEquals("Đặt trước chợ", translate("Book the market"));
    }

    @Test
    @DisplayName("liệt kê động từ: vế sau dấu phẩy và \"and\" cũng là động từ")
    void verbListKeepsVerbSense() {
        // "work" sau dau phay phai lay nghia dong tu "làm việc", khong phai "sự làm việc".
        assertEquals("Đọc, làm việc, và đặt trước", translate("Read, work, and book"));
    }

    @Test
    @DisplayName("số đếm đứng trước danh từ và không bị tra từ điển")
    void numeralsStayBeforeTheNoun() {
        // "four" khong co trong bang hu tu thi bi tra nguon 109K va ra nghia co
        // "chứng khoán lãi 4 qịu" - da do that tren tai lieu.
        assertEquals("Bốn sách", translate("The four books"));
    }

    @Test
    @DisplayName("cấp so sánh: older -> già hơn")
    void comparativeAddsMarker() {
        // Lemmatizer cat duoi -er de tra duoc tu dien, nen nghia tra ra mat han y so sanh.
        assertEquals("Hệ thống già hơn", translate("The older system"));
    }

    @Test
    @DisplayName("including là giới từ, không phải tính từ bổ nghĩa")
    void participialPrepositionStaysBeforeItsObject() {
        // Tu dien ghi "including" la tinh tu, de nguyen thi buoc sap lai danh ngu day no ra
        // sau danh tu: "gồm cả việc" thanh "việc gồm cả".
        assertEquals("Sách gồm cả việc", translate("The book including the job"));
    }

    @Test
    @DisplayName("cụm nhiều từ mang từ loại danh từ thì tham gia sắp lại danh ngữ")
    void multiWordNounJoinsNounPhraseReorder() {
        // Truoc day moi cum nhieu tu bi coi la cum dong tu, nen tinh tu khong duoc day ra sau.
        assertEquals("Ca sử dụng tốt", translate("A good use case"));
    }

    @Test
    @DisplayName("nhóm nghĩa được từ điển viết kỹ hơn thắng: school -> trường học")
    void richerSenseWins() {
        // Neu lay theo thu tu file thi ra "đàn cá" - dung nghia tu dien nhung sai y nguoi dung.
        assertTrue(translate("He went to school").contains("trường học"),
                translate("He went to school"));
    }

    @Test
    @DisplayName("is + V-ing -> \"đang\", lùi về nguyên thể để lấy nghĩa động từ")
    void progressiveFallsBackToLemma() {
        // "@reading" chi co tu loai danh tu ("sự đọc") nen phai lui ve "read".
        assertEquals("Anh ấy đang đọc sách", translate("He is reading a book"));
    }

    @Test
    @DisplayName("cụm động từ vẫn được nhận và vẫn nhận dấu hiệu thì")
    void phrasalVerbKeepsWorking() {
        assertTrue(translate("He gave up his job").startsWith("Anh ấy đã bỏ"),
                translate("He gave up his job"));
    }

    @Test
    @DisplayName("một dòng nghĩa được tách thành từng phương án riêng")
    void glossAlternativesAreSplit() {
        // Bang xac suat cham diem TUNG phuong an, nen phai tach ra truoc. Phuong an dau
        // cua tu dien khong phai luc nao cung la phuong an dung.
        assertEquals(List.of("cho", "biếu", "tặng", "ban"),
                RuleBasedTranslationEngine.alternatives("cho, biếu, tặng, ban"));
        assertEquals(List.of("loại a", "hạng nhất"),
                RuleBasedTranslationEngine.alternatives("(thông tục) loại a, hạng nhất"));
        assertTrue(RuleBasedTranslationEngine.alternatives(null).isEmpty());
    }

    @Test
    @DisplayName("nghĩa dài được rút còn phương án đầu")
    void longGlossesAreShortened() {
        assertEquals("sách", RuleBasedTranslationEngine.shorten("sách, quyển sách"));
        assertEquals("giữ vững", RuleBasedTranslationEngine.shorten("giữ vững, giữ không cho đổ"));
        assertEquals("loại a", RuleBasedTranslationEngine.shorten("(thông tục) loại a, hạng nhất"));
    }

    @Test
    @DisplayName("từ chức năng dịch cứng, không lấy nghĩa từ điển")
    void functionWordsBypassTheDictionary() {
        // Chinh xac cai bay o muc tu "@he": nghia duoc viet ky nhat lai la "đàn ông, con đực".
        assertTrue(translate("He went to the market").startsWith("Anh ấy"));
        assertEquals("go", TextNormalizer.normalizeHeadword("Go"));
    }
}
