package com.anhtuan.dict.nmt;

import com.anhtuan.dict.core.model.SegmentKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tach tu kieu SentencePiece Unigram, viet lai bang Java.
 *
 * <p>Phan lon bo test chay tren mot tu vung mini tu dung trong {@code @TempDir} - khong can
 * mo hinh 98 MB. Rieng phep thu dich that su thi bo qua neu chua tai mo hinh.
 */
class MarianTokenizerTest {

    @TempDir
    Path tmp;

    /**
     * Tu vung mini. Diem cao = manh hay gap. Co y de "▁unbelievable" co hai cach cat, de
     * kiem tra Viterbi chon cach TONG DIEM cao nhat chu khong phai cach tham lam dai nhat.
     */
    private Path miniVocab() throws IOException {
        List<String> lines = List.of(
                "</s>\t0.0",              // id 0
                "<unk>\t0.0",             // id 1
                "▁the\t-3.0",             // id 2
                "▁government\t-5.0",      // id 3
                "▁un\t-6.0",              // id 4
                "bel\t-9.0",              // id 5
                "iev\t-9.0",              // id 6
                "able\t-4.0",             // id 7
                "▁unbeliev\t-12.0",       // id 8
                ".\t-2.0");               // id 9
        Path file = tmp.resolve(MarianTokenizer.VOCAB_FILE);
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("mã hoá xong phải có </s> ở cuối, đúng như Marian chờ")
    void encodeAppendsEos() throws IOException {
        MarianTokenizer tok = MarianTokenizer.load(miniVocab());
        long[] ids = tok.encode("the government");
        assertEquals(3, ids.length);
        assertEquals(2, ids[0]);
        assertEquals(3, ids[1]);
        assertEquals(MarianTokenizer.EOS_ID, ids[2], "thieu </s> la model dich sai het");
    }

    @Test
    @DisplayName("Viterbi chọn cách cắt có tổng điểm cao nhất")
    void viterbiPicksHighestTotalScore() throws IOException {
        MarianTokenizer tok = MarianTokenizer.load(miniVocab());
        long[] ids = tok.encode("unbelievable");
        // "▁un|bel|iev|able" = -6-9-9-4 = -28  hon  "▁unbeliev|able" = -12-4 = -16?
        // Khong: -16 > -28 nen cach thu hai thang. Day chinh la cho thuat toan tham lam sai.
        assertEquals(List.of(8L, 7L, 0L),
                List.of(ids[0], ids[1], ids[2]), "phai chon ▁unbeliev + able");
    }

    @Test
    @DisplayName("ký tự lạ không làm hỏng cả câu, chỉ thành <unk>")
    void unknownCharactersBecomeUnk() throws IOException {
        MarianTokenizer tok = MarianTokenizer.load(miniVocab());
        long[] ids = tok.encode("the ☃");
        assertEquals(2, ids[0]);
        assertTrue(ids.length >= 3);
        assertEquals(MarianTokenizer.EOS_ID, ids[ids.length - 1]);
    }

    @Test
    @DisplayName("giải mã bỏ ký tự ▁ và các id đặc biệt")
    void decodeRestoresSpaces() throws IOException {
        MarianTokenizer tok = MarianTokenizer.load(miniVocab());
        assertEquals("the government",
                tok.decode(List.of(2, 3, MarianTokenizer.EOS_ID)));
        assertEquals("", tok.decode(List.of(MarianTokenizer.EOS_ID)));
    }

    @Test
    @DisplayName("chưa tải mô hình thì isInstalled() = false, không ném lỗi")
    void missingModelIsDetected() {
        assertFalse(OnnxNmtEngine.isInstalled(tmp.resolve("khong-co")));
    }

    @Test
    @DisplayName("dịch thật bằng mô hình (bỏ qua nếu chưa tải model)")
    void translatesWithRealModel() {
        Path modelDir = Path.of("..", "data", "build", OnnxNmtEngine.MODEL_DIR);
        assumeTrue(OnnxNmtEngine.isInstalled(modelDir),
                "chua tai mo hinh - chay scripts/tai-model-nmt.ps1 neu muon test nay");

        try (OnnxNmtEngine engine = OnnxNmtEngine.load(modelDir)) {
            var segments = engine.translate("She went to the market yesterday.");
            assertEquals(1, segments.size(), "engine dich ca cau tra ve dung mot doan");
            assertEquals(SegmentKind.TRANSLATED, segments.getFirst().kind());
            String vi = segments.getFirst().displayGloss();
            assertTrue(vi.toLowerCase().contains("chợ"), "ban dich phai co chu 'chợ': " + vi);
        }
    }
}
