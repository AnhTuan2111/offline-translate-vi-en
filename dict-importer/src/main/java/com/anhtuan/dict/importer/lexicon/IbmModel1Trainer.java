package com.anhtuan.dict.importer.lexicon;

import com.anhtuan.dict.core.lexicon.LexiconFormat;
import com.anhtuan.dict.core.lexicon.LexiconWriter;
import com.anhtuan.dict.core.nlp.TextNormalizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Hoc bang xac suat dich tu bang IBM Model 1 tren kho cau song ngu.
 *
 * <h2>Y tuong, noi bang mot doan</h2>
 * Cho hang trieu cap cau Anh - Viet nhung KHONG biet tu nao ung voi tu nao. Bat dau bang
 * gia dinh ngay tho: trong mot cap cau, moi tu tieng Anh co the sinh ra bat ky tu tieng Viet
 * nao voi xac suat nhu nhau. Roi lap lai:
 * <ol>
 *   <li>dua tren xac suat hien tai, chia "cong trang" cho tung cap tu trong tung cau;</li>
 *   <li>cong don cong trang lai thanh xac suat moi.</li>
 * </ol>
 * Sau vai vong, nhung cap tu thuc su di voi nhau se noi len: {@code government} xuat hien
 * cung "chính" va "phủ" o hang chuc nghin cau khac nhau, con "sự" thi xuat hien cung MOI TU,
 * nen bi chia deu va chim xuong. Do la thuat toan EM, cong bo nam 1993, va no la nen mong
 * cua dich may thong ke suot 20 nam truoc khi mang no-ron xuat hien.
 *
 * <h2>Ba cach thu nho bai toan (quan trong ve bo nho)</h2>
 * <ul>
 *   <li><b>Chi hoc nhung tu ta can.</b> Phia tieng Anh gioi han trong muc tu cua tu dien,
 *       phia tieng Viet gioi han trong tu vung cua cac dong nghia (24.500 am tiet). Bang
 *       day du se la 40.000 x 24.500 = gan mot ty o; gioi han lai con vai chuc trieu cap.</li>
 *   <li><b>Giu mot o "khac" o ca hai phia.</b> Tu ngoai tu vung khong bi vut di ma don vao
 *       id 0. Neu vut di thi mau so cua phep chia sai, va xac suat bi thoi phong.</li>
 *   <li><b>Bang bam dia chi mo bang mang nguyen thuy.</b> {@code HashMap<Long, Float>} voi
 *       20 trieu phan tu se ngon vai GB chi rieng tien boxing.</li>
 * </ul>
 */
public final class IbmModel1Trainer {

    /**
     * @param maxSentences so cap cau toi da dua vao hoc (lay rai deu ca kho, khong lay lien
     *                     mot doan - kho phu de sap xep theo tung bo phim)
     * @param maxLength    bo cau qua dai: cau 60 tu lam nhieu cap sai va ngon bo nho
     * @param iterations   so vong EM; do thuc te: qua vong 4 thi ket qua gan nhu khong doi
     * @param topK         so ban dich giu lai cho moi tu
     * @param tableBits    kich thuoc bang bam = 2^tableBits o
     */
    public record Config(int maxSentences, int maxLength, int iterations, int topK, int tableBits) {
        public static Config defaults() {
            return new Config(1_200_000, 20, 4, 16, 24);
        }
    }

    public record Result(List<LexiconWriter.EnglishWord> words, List<String> viVocabulary,
                         int sentenceUsed, int pairCount, double fillRatio) {}

    private IbmModel1Trainer() {}

    // ------------------------------------------------------------------ mang int tang dan

    private static final class IntSeq {
        private int[] a = new int[1 << 16];
        private int n;

        void add(int v) {
            if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[n++] = v;
        }

        int size() { return n; }
        int get(int i) { return a[i]; }
    }

    // ------------------------------------------------------------------ bang bam cap tu

    /**
     * Bang bam dia chi mo cho cap (tu Anh, am tiet Viet) -> chi so o.
     * Khoa luu duoi dang {@code key + 1} de gia tri 0 co nghia la "o trong".
     */
    private static final class PairTable {
        private final long[] keys;
        private final int mask;
        private int size;

        PairTable(int bits) {
            this.keys = new long[1 << bits];
            this.mask = (1 << bits) - 1;
        }

        private int slotOf(long key) {
            long k = key + 1;
            int i = (int) (mix(key) & mask);
            while (true) {
                long cur = keys[i];
                if (cur == 0) return ~i;                 // trong -> tra ve bu cua vi tri
                if (cur == k) return i;
                i = (i + 1) & mask;
            }
        }

        /** Tra ve chi so o, them moi neu chua co va bang chua day. -1 neu bang da day. */
        int put(long key) {
            int s = slotOf(key);
            if (s >= 0) return s;
            if (size * 10 >= keys.length * 7) return -1;  // he so tai 0,7 -> ngung nhan them
            int i = ~s;
            keys[i] = key + 1;
            size++;
            return i;
        }

        int find(long key) {
            int s = slotOf(key);
            return s >= 0 ? s : -1;
        }

        long keyAt(int slot) { return keys[slot] - 1; }
        boolean used(int slot) { return keys[slot] != 0; }
        int size() { return size; }
        int capacity() { return keys.length; }

        private static long mix(long z) {              // splitmix64, tron bit cho deu
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
    }

    // ------------------------------------------------------------------ hoc

    public static Result train(Path enFile, Path viFile, Set<String> allowedEn,
                               Set<String> allowedVi, Config cfg, Consumer<String> log) {
        // --- 1. danh so tu vung. id 0 o ca hai phia danh cho "tu ngoai tu vung" ---
        List<String> enVocab = new ArrayList<>(new TreeSet<>(allowedEn));
        List<String> viVocab = new ArrayList<>(new TreeSet<>(allowedVi));
        Map<String, Integer> enId = new HashMap<>(enVocab.size() * 2);
        Map<String, Integer> viId = new HashMap<>(viVocab.size() * 2);
        for (int i = 0; i < enVocab.size(); i++) enId.put(enVocab.get(i), i + 1);
        for (int i = 0; i < viVocab.size(); i++) viId.put(viVocab.get(i), i + 1);
        log.accept(String.format("tu vung: %,d tu tieng Anh / %,d am tiet tieng Viet",
                enVocab.size(), viVocab.size()));

        // --- 2. doc kho, ma hoa thanh mang int ---
        long totalLines = countLines(enFile);
        int stride = (int) Math.max(1, totalLines / cfg.maxSentences());
        log.accept(String.format("kho co %,d cap cau, lay moi %d cap mot -> toi da %,d cap",
                totalLines, stride, cfg.maxSentences()));

        IntSeq enTokens = new IntSeq();
        IntSeq viTokens = new IntSeq();
        IntSeq enStart = new IntSeq();
        IntSeq viStart = new IntSeq();
        int used = 0;

        try (BufferedReader en = reader(enFile); BufferedReader vi = reader(viFile)) {
            String le, lv;
            long lineNo = 0;
            while ((le = en.readLine()) != null && (lv = vi.readLine()) != null) {
                if (lineNo++ % stride != 0) continue;
                if (used >= cfg.maxSentences()) break;

                List<String> e = englishTokens(le);
                List<String> v = TextNormalizer.splitTokens(lv);
                if (e.isEmpty() || v.isEmpty()) continue;
                if (e.size() > cfg.maxLength() || v.size() > cfg.maxLength()) continue;

                enStart.add(enTokens.size());
                viStart.add(viTokens.size());
                enTokens.add(0);                          // o NULL, luon co mat trong moi cau
                for (String t : e) enTokens.add(enId.getOrDefault(t, 0));
                for (String t : v) viTokens.add(viId.getOrDefault(t, 0));
                used++;
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("khong doc duoc kho song ngu", ex);
        }
        enStart.add(enTokens.size());
        viStart.add(viTokens.size());
        log.accept(String.format("da nap %,d cap cau (%,d + %,d token)",
                used, enTokens.size(), viTokens.size()));

        // --- 3. ghi nhan moi cap tu cung xuat hien ---
        PairTable table = new PairTable(cfg.tableBits());
        boolean full = false;
        for (int s = 0; s < used; s++) {
            int ea = enStart.get(s), eb = enStart.get(s + 1);
            int va = viStart.get(s), vb = viStart.get(s + 1);
            for (int i = ea; i < eb; i++) {
                long hi = ((long) enTokens.get(i)) << 32;
                for (int j = va; j < vb; j++) {
                    if (table.put(hi | viTokens.get(j)) < 0) { full = true; break; }
                }
                if (full) break;
            }
            if (full) break;
        }
        double fill = (double) table.size() / table.capacity();
        log.accept(String.format("cap tu: %,d (bang day %.0f%%)%s",
                table.size(), fill * 100, full ? "  ** BANG DAY, ket qua bi cat **" : ""));

        // --- 4. EM ---
        float[] t = new float[table.capacity()];
        float[] count = new float[table.capacity()];
        double[] total = new double[enVocab.size() + 1];
        for (int i = 0; i < t.length; i++) if (table.used(i)) t[i] = 1f;

        int[] slots = new int[(cfg.maxLength() + 1) * cfg.maxLength()];
        for (int iter = 1; iter <= cfg.iterations(); iter++) {
            long t0 = System.nanoTime();
            Arrays.fill(count, 0f);
            Arrays.fill(total, 0);

            for (int s = 0; s < used; s++) {
                int ea = enStart.get(s), eb = enStart.get(s + 1);
                int va = viStart.get(s), vb = viStart.get(s + 1);
                int nEn = eb - ea, nVi = vb - va;

                for (int j = 0; j < nVi; j++) {
                    int f = viTokens.get(va + j);
                    double denom = 0;
                    for (int i = 0; i < nEn; i++) {
                        int slot = table.find((((long) enTokens.get(ea + i)) << 32) | f);
                        slots[j * nEn + i] = slot;
                        if (slot >= 0) denom += t[slot];
                    }
                    if (denom <= 0) continue;
                    for (int i = 0; i < nEn; i++) {
                        int slot = slots[j * nEn + i];
                        if (slot < 0) continue;
                        float c = (float) (t[slot] / denom);
                        count[slot] += c;
                        total[enTokens.get(ea + i)] += c;
                    }
                }
            }
            for (int i = 0; i < t.length; i++) {
                if (!table.used(i)) continue;
                int e = (int) (table.keyAt(i) >>> 32);
                double tot = total[e];
                t[i] = tot > 0 ? (float) (count[i] / tot) : 0f;
            }
            log.accept(String.format("  vong EM %d/%d xong (%,d ms)",
                    iter, cfg.iterations(), (System.nanoTime() - t0) / 1_000_000));
        }

        // --- 5. lay topK cho moi tu tieng Anh ---
        int k = cfg.topK();
        int[][] topId = new int[enVocab.size() + 1][];
        float[][] topP = new float[enVocab.size() + 1][];
        int[] topN = new int[enVocab.size() + 1];

        for (int slot = 0; slot < t.length; slot++) {
            if (!table.used(slot) || t[slot] < LexiconFormat.MIN_PROBABILITY) continue;
            long key = table.keyAt(slot);
            int e = (int) (key >>> 32);
            int f = (int) (key & 0xFFFFFFFFL);
            if (e == 0 || f == 0) continue;               // bo o "ngoai tu vung"
            if (topId[e] == null) { topId[e] = new int[k]; topP[e] = new float[k]; }
            insertTop(topId[e], topP[e], topN, e, f, t[slot], k);
        }

        List<LexiconWriter.EnglishWord> words = new ArrayList<>(enVocab.size());
        long pairs = 0;
        for (int e = 1; e <= enVocab.size(); e++) {
            if (topId[e] == null || topN[e] == 0) continue;
            List<String> tokens = new ArrayList<>(topN[e]);
            List<Double> probs = new ArrayList<>(topN[e]);
            for (int i = 0; i < topN[e]; i++) {
                tokens.add(viVocab.get(topId[e][i] - 1));
                probs.add((double) topP[e][i]);
            }
            words.add(new LexiconWriter.EnglishWord(enVocab.get(e - 1), tokens, probs));
            pairs += topN[e];
        }
        log.accept(String.format("giu lai %,d tu co ban dich, %,d cap", words.size(), pairs));
        return new Result(words, viVocab, used, (int) pairs, fill);
    }

    /** Chen mot ung vien vao danh sach topK dang giu cho tu {@code e} (chen truc tiep). */
    private static void insertTop(int[] ids, float[] ps, int[] counts, int e, int f, float p, int k) {
        int n = counts[e];
        if (n < k) {
            int i = n - 1;
            while (i >= 0 && ps[i] < p) { ps[i + 1] = ps[i]; ids[i + 1] = ids[i]; i--; }
            ps[i + 1] = p; ids[i + 1] = f;
            counts[e] = n + 1;
            return;
        }
        if (p <= ps[k - 1]) return;
        int i = k - 2;
        while (i >= 0 && ps[i] < p) { ps[i + 1] = ps[i]; ids[i + 1] = ids[i]; i--; }
        ps[i + 1] = p; ids[i + 1] = f;
    }

    /** Token tieng Anh: chu cai, chu so, dau nhay - da lowercase. */
    static List<String> englishTokens(String line) {
        List<String> out = new ArrayList<>(16);
        StringBuilder cur = new StringBuilder(16);
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '\'') cur.append(Character.toLowerCase(c));
            else if (!cur.isEmpty()) { out.add(cur.toString()); cur.setLength(0); }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    private static BufferedReader reader(Path p) throws IOException {
        return Files.newBufferedReader(p, StandardCharsets.UTF_8);
    }

    private static long countLines(Path p) {
        try (BufferedReader r = reader(p)) {
            long n = 0;
            while (r.readLine() != null) n++;
            return n;
        } catch (IOException e) {
            throw new UncheckedIOException("khong dem duoc so dong " + p, e);
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
