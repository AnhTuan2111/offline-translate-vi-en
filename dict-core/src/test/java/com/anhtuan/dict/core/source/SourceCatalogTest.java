package com.anhtuan.dict.core.source;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Sense;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.pack.PackReader;
import com.anhtuan.dict.core.pack.PackWriter;
import com.anhtuan.dict.core.service.LookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quan ly nhieu nguon tu dien: bat / tat / doi uu tien (PLAN.md F5, M7). */
class SourceCatalogTest {

    @TempDir
    Path tmp;

    private static Entry entry(String headword, String gloss, int sourceId) {
        return new Entry(headword, TextNormalizer.normalizeHeadword(headword), null, null,
                List.of(new Sense("danh từ", List.of(gloss), List.of())),
                List.of(), List.of(), sourceId);
    }

    private static SourceCatalog twoSources() {
        return SourceCatalog.of(List.of(
                new DictSource(0, "Anh-Việt 109K", "anhviet109k", "a.txt", 108854, true, 0),
                new DictSource(1, "Thuật ngữ CNTT", "tsv", "cntt.tsv", 8, true, 1)));
    }

    @Test
    @DisplayName("ghi ra rồi đọc lại giữ nguyên bật/tắt và thứ tự")
    void roundTrip() {
        Path file = tmp.resolve(SourceCatalog.FILE_NAME);
        twoSources().setEnabled(1, false).save(file);

        SourceCatalog loaded = SourceCatalog.loadOrDefault(file, "khong dung", 0);
        assertEquals(2, loaded.size());
        assertTrue(loaded.isEnabled(0));
        assertFalse(loaded.isEnabled(1));
        assertEquals("Anh-Việt 109K", loaded.all().getFirst().name());
    }

    @Test
    @DisplayName("chưa có file thì dựng một nguồn mặc định, dữ liệu cũ vẫn chạy")
    void defaultWhenMissing() {
        SourceCatalog catalog = SourceCatalog.loadOrDefault(
                tmp.resolve("khong-co.tsv"), "Mặc định", 1234);
        assertEquals(1, catalog.size());
        assertTrue(catalog.isEnabled(0));
        assertEquals(1234, catalog.all().getFirst().entries());
    }

    @Test
    @DisplayName("đổi thứ tự thì đánh số ưu tiên lại từ đầu")
    void moveRenumbers() {
        SourceCatalog moved = twoSources().move(1, -1);
        assertEquals(1, moved.all().getFirst().id(), "nguon 1 phai len dau");
        assertEquals(0, moved.all().getFirst().priority());
        assertEquals(1, moved.all().get(1).priority());
        // Day len khi da o dau thi khong doi gi, khong nem loi
        assertEquals(moved.all(), moved.move(1, -1).all());
    }

    @Test
    @DisplayName("tắt một nguồn thì mục từ của nó biến khỏi kết quả tra")
    void disabledSourceDisappears() {
        Path pack = tmp.resolve("dict.pack");
        PackWriter.write(pack, List.of(
                entry("plan", "sơ đồ, đồ án", 0),
                entry("plan", "kế hoạch triển khai", 1)));

        try (PackReader reader = PackReader.open(pack)) {
            LookupService lookup = new LookupService(reader, twoSources());
            assertEquals(2, lookup.lookupAll("plan").size());
            // Nguon 0 uu tien hon nen dung truoc
            assertEquals("sơ đồ, đồ án",
                    lookup.lookupAll("plan").getFirst().senses().getFirst().glosses().getFirst());

            lookup.setCatalog(twoSources().setEnabled(0, false));
            List<Entry> only = lookup.lookupAll("plan");
            assertEquals(1, only.size());
            assertEquals("kế hoạch triển khai",
                    only.getFirst().senses().getFirst().glosses().getFirst());
        }
    }

    @Test
    @DisplayName("đổi thứ tự thì nguồn lên trước được ưu tiên khi tra")
    void priorityDecidesOrder() {
        Path pack = tmp.resolve("dict.pack");
        PackWriter.write(pack, List.of(
                entry("plan", "sơ đồ, đồ án", 0),
                entry("plan", "kế hoạch triển khai", 1)));

        try (PackReader reader = PackReader.open(pack)) {
            LookupService lookup = new LookupService(reader, twoSources().move(1, -1));
            assertEquals("kế hoạch triển khai",
                    lookup.lookupAll("plan").getFirst().senses().getFirst().glosses().getFirst());
        }
    }

    @Test
    @DisplayName("tắt hết nguồn thì dùng lại tất cả, không để màn hình trống")
    void allDisabledFallsBackToEverything() {
        SourceCatalog none = twoSources().setEnabled(0, false).setEnabled(1, false);
        assertFalse(none.hasEnabled());

        Path pack = tmp.resolve("dict.pack");
        PackWriter.write(pack, List.of(entry("plan", "sơ đồ", 0)));
        try (PackReader reader = PackReader.open(pack)) {
            LookupService lookup = new LookupService(reader, none);
            assertEquals(1, lookup.lookupAll("plan").size(),
                    "tat het ma tra ve rong thi nguoi dung tuong app hong");
        }
    }
}
