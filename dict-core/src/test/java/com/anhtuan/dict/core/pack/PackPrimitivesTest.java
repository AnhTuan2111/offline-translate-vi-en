package com.anhtuan.dict.core.pack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cac nguyen thuy cua pack format. Chung nho nhung sai la HONG TOAN BO FILE,
 * nen phai test ky truoc khi viet PackWriter o M2.
 */
class PackPrimitivesTest {

    @Test
    @DisplayName("VarInt: ghi roi doc lai ra dung so ban dau")
    void varIntRoundTrip() {
        int[] samples = {0, 1, 63, 127, 128, 255, 16_383, 16_384, 1_000_000, Integer.MAX_VALUE};
        for (int v : samples) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            VarInt.write(out, v);
            byte[] bytes = out.toByteArray();
            assertEquals(VarInt.sizeOf(v), bytes.length, "sizeOf sai voi " + v);
            assertEquals(v, VarInt.read(ByteBuffer.wrap(bytes)), "doc lai sai voi " + v);
        }
    }

    @Test
    @DisplayName("VarInt: so nho ton it byte - day la ly do postings chi ~1,3 B/luot")
    void varIntIsCompactForSmallNumbers() {
        assertEquals(1, VarInt.sizeOf(0));
        assertEquals(1, VarInt.sizeOf(127));
        assertEquals(2, VarInt.sizeOf(128));
        assertEquals(2, VarInt.sizeOf(16_383));
        assertEquals(3, VarInt.sizeOf(16_384));
    }

    @Test
    @DisplayName("VarInt: doc nhieu so lien tiep tu mot buffer")
    void varIntSequential() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] values = {5, 300, 1, 70_000, 0};
        for (int v : values) VarInt.write(out, v);

        ByteBuffer buf = ByteBuffer.wrap(out.toByteArray());
        for (int v : values) assertEquals(v, VarInt.read(buf));
    }

    @Test
    @DisplayName("Utf8Compare: byte 0x80+ phai duoc coi la KHONG DAU")
    void utf8CompareTreatsBytesAsUnsigned() {
        byte[] ascii = "z".getBytes(StandardCharsets.UTF_8);       // 0x7A
        byte[] vietnamese = "ă".getBytes(StandardCharsets.UTF_8);  // 0xC4 0x83
        // Neu so sanh byte co dau, 0xC4 thanh -60 va "ă" se bi coi la NHO hon "z" -> sai thu tu
        assertTrue(Utf8Compare.compare(ascii, vietnamese) < 0,
                "byte phai duoc so sanh khong dau, neu khong binary search se truot");
    }

    @Test
    @DisplayName("Utf8Compare: tien to ngan hon thi nho hon")
    void utf8ComparePrefixOrdering() {
        assertTrue(Utf8Compare.COMPARATOR.compare("about", "about to") < 0);
        assertTrue(Utf8Compare.COMPARATOR.compare("about to", "about") > 0);
        assertEquals(0, Utf8Compare.COMPARATOR.compare("about", "about"));
    }

    @Test
    @DisplayName("BAT BIEN SONG CON: compareAt tren buffer khop 100% voi COMPARATOR luc sap xep")
    void compareAtMatchesComparatorUsedForSorting() {
        // Day la rui ro so 2 trong PLAN.md muc 12: neu PackWriter sap xep bang mot quy tac
        // va PackReader binary search bang quy tac khac thi ket qua SAI AM THAM.
        List<String> keys = new ArrayList<>(List.of(
                "a", "about", "about to", "ăn", "zebra", "give up", "a la carte",
                "Đông", "đông", "test", "tết", "cafe", "café"));
        keys.sort(Utf8Compare.COMPARATOR);

        // Dung KEYS y het cach PackWriter se lam: noi lien, ngan bang 0x00
        ByteArrayOutputStream keyBlob = new ByteArrayOutputStream();
        int[] offsets = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            offsets[i] = keyBlob.size();
            keyBlob.writeBytes(keys.get(i).getBytes(StandardCharsets.UTF_8));
            keyBlob.write(0);
        }
        ByteBuffer buf = ByteBuffer.wrap(keyBlob.toByteArray());

        // Moi khoa phai tim thay chinh no bang binary search
        for (String key : keys) {
            assertEquals(key, binarySearch(buf, offsets, keys, key), "binary search truot khoa: " + key);
        }
        // Khoa khong ton tai phai tra null, khong duoc tra bua
        assertNull(binarySearch(buf, offsets, keys, "khongtontai"));
        assertNull(binarySearch(buf, offsets, keys, "abou"));
    }

    @Test
    @DisplayName("BlockCodec: nen roi giai nen ra dung du lieu goc")
    void blockCodecRoundTrip() {
        Random rnd = new Random(42);
        for (int size : new int[]{1, 100, 8192, 50_000}) {
            byte[] raw = new byte[size];
            rnd.nextBytes(raw);
            byte[] compressed = BlockCodec.compress(raw);
            assertArrayEquals(raw, BlockCodec.decompress(compressed, raw.length),
                    "round-trip that bai voi size=" + size);
        }
    }

    @Test
    @DisplayName("BlockCodec: van ban tu dien nen duoc it nhat 3 lan")
    void blockCodecCompressesDictionaryTextWell() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            sb.append("@word").append(i).append(" /wə:d/\n")
              .append("*  danh từ\n- nghĩa thứ nhất của từ này\n")
              .append("=this is an example+ đây là một ví dụ\n");
        }
        byte[] raw = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] compressed = BlockCodec.compress(raw);

        double ratio = (double) raw.length / compressed.length;
        assertTrue(ratio > 3.0, "ti le nen chi dat " + String.format("%.1f", ratio) + "x, mong doi > 3x");
        assertArrayEquals(raw, BlockCodec.decompress(compressed, raw.length));
    }

    /** Mo phong dung thuat toan PackReader se dung o M2 (PLAN.md 6.2). */
    private static String binarySearch(ByteBuffer buf, int[] offsets, List<String> keys, String target) {
        byte[] needle = target.getBytes(StandardCharsets.UTF_8);
        int lo = 0, hi = offsets.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = Utf8Compare.compareAt(buf, offsets[mid], needle);
            if (cmp == 0) return keys.get(mid);
            if (cmp < 0) lo = mid + 1;
            else hi = mid - 1;
        }
        return null;
    }
}
