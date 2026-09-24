package com.anhtuan.dict.core.nlp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

/**
 * Danh sach TU GHEP tieng Viet, dung de gop am tiet lai thanh tu khi danh chi muc va khi tra.
 *
 * <h2>Vi sao can</h2>
 * "chăm sóc" la MOT tu tieng Viet, nhung ta dang danh chi muc thanh hai am tiet roi. Hau qua:
 * mot muc tu chua "chăm chỉ" o cho nay va "sóc" o cho khac van khop, trong khi muc tu chua
 * dung chu "chăm sóc" lai khong duoc uu tien hon. Tieng Viet viet roi tung am tiet nen may
 * khong tu nhin ra ranh gioi tu - phai co danh sach.
 *
 * <h2>Danh sach nay o dau ra</h2>
 * KHONG tai ve, KHONG nhung thu vien ngoai (VnCoreNLP co bo tach tu rat tot nhung la GPL-3.0,
 * nhung vao la ca ung dung phai theo GPL). Danh sach duoc rut ra tu chinh tu dien dang co:
 *
 * <p>Moi <b>phuong an</b> trong mot dong nghia la mot DON VI DICH. Dong "- trông nom, chăm sóc"
 * cho ta hai don vi. Phuong an nao dai 2-3 am tiet va lap lai o nhieu muc tu khac nhau thi
 * gan nhu chac chan la mot tu ghep that. Do thuc te tren 200.060 dong nghia: nguong 3 lan cho
 * ra <b>16.592 tu</b>, trong do co day du "chăm sóc", "ngân hàng", "kế hoạch", "máy tính"...
 *
 * <h2>Khop TAT CA chu khong phai khop dai nhat</h2>
 * Gap "sự chăm sóc" thi sinh ra ca {@code sự_chăm_sóc} lan {@code chăm_sóc}. Neu chi lay cum
 * dai nhat thi nguoi go "chăm sóc" se khong tim ra muc tu ghi "sự chăm sóc" - dung cai ta dinh
 * sua thi lai hong theo kieu khac.
 */
public final class ViCompounds {

    /** Ky tu noi cac am tiet cua mot tu ghep. Khong bao giờ xuat hien trong van ban that. */
    public static final char JOIN = '_';

    /** So am tiet toi da cua mot tu ghep duoc nhan dien. */
    public static final int MAX_SYLLABLES = 3;

    /** Ten file trong thu muc du lieu. De dang van ban de nguoi dung tu them tu vao duoc. */
    public static final String FILE_NAME = "vi-words.txt";

    private final Set<String> words;
    /** Dang bo dau -> cac dang co dau. Dung cho goi y khi go sai. */
    private final Map<String, List<String>> byNoDiacritics;

    private ViCompounds(Set<String> words) {
        this.words = words;
        this.byNoDiacritics = new HashMap<>(words.size() * 2);
        for (String w : words) {
            byNoDiacritics.computeIfAbsent(TextNormalizer.removeDiacritics(w),
                    k -> new ArrayList<>(2)).add(w);
        }
    }

    public static ViCompounds of(Collection<String> words) {
        return new ViCompounds(new HashSet<>(words));
    }

    public static ViCompounds empty() {
        return new ViCompounds(Set.of());
    }

    /** Doc danh sach; file khong ton tai thi tra ve ban rong, moi thu van chay binh thuong. */
    public static ViCompounds loadIfPresent(Path file) {
        if (!Files.isRegularFile(file)) return empty();
        try {
            Set<String> words = new HashSet<>(32_768);
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String w = line.trim();
                if (!w.isEmpty() && w.charAt(0) != '#') words.add(w);
            }
            return new ViCompounds(words);
        } catch (IOException e) {
            throw new UncheckedIOException("khong doc duoc " + file, e);
        }
    }

    public static void write(Path file, Collection<String> words) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            List<String> sorted = new ArrayList<>(new TreeSet<>(words));
            Files.write(file, sorted, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("khong ghi duoc " + file, e);
        }
    }

    public boolean isAvailable() {
        return !words.isEmpty();
    }

    public int size() {
        return words.size();
    }

    public boolean contains(String compound) {
        return words.contains(compound);
    }

    /**
     * Tra ve cac am tiet goc KEM cac tu ghep nhan dien duoc.
     *
     * <pre>
     *   [sự, chăm, sóc]  ->  [sự, chăm, sóc, sự_chăm_sóc, chăm_sóc]
     * </pre>
     *
     * Giu lai ca am tiet roi de nguoi go mot chu van tim ra; them tu ghep de nguoi go dung
     * ca tu duoc uu tien - tu ghep hiem hon nen diem IDF cao hon han.
     */
    public List<String> expand(List<String> syllables) {
        if (words.isEmpty() || syllables.size() < 2) return syllables;
        List<String> out = new ArrayList<>(syllables.size() + 4);
        out.addAll(syllables);
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < syllables.size(); i++) {
            for (int n = 2; n <= MAX_SYLLABLES && i + n <= syllables.size(); n++) {
                sb.setLength(0);
                for (int k = 0; k < n; k++) {
                    if (k > 0) sb.append(' ');
                    sb.append(syllables.get(i + k));
                }
                String phrase = sb.toString();
                if (words.contains(phrase)) out.add(phrase.replace(' ', JOIN));
            }
        }
        return out;
    }

    /** Tach chuoi thanh am tiet roi gop tu ghep - duong tat dung o ca luc build lan luc tra. */
    public List<String> tokenize(String text) {
        return expand(TextNormalizer.splitTokens(text));
    }

    /**
     * Doan tu nguoi dung DINH go, khi ho go sai chinh ta tieng Viet.
     *
     * <p>Vi sao can rieng duong nay: tim theo am tiet van "chay" khi go sai, nhung chay ra
     * rac. Do thuc te truoc khi co ham nay: go {@code "cham sok"} tra ve
     * {@code slow, sculp, shock} - khong mot ket qua nao lien quan, va nguoi dung khong he
     * biet minh go sai o dau. Index tieng Anh da co duong chong go sai ({@code tri.idx}),
     * phia tieng Viet thi chua.
     *
     * <p>So sanh bang trigram ky tu tren dang DA BO DAU, nen cau ai go thieu dau van khop.
     * Chi quet khi that su can nen 16.762 tu la thua suc nhanh.
     *
     * @return cac tu ghep gan giong nhat, rong neu truy van von da dung
     */
    public List<String> suggest(String query, int limit) {
        if (words.isEmpty()) return List.of();
        List<String> syllables = TextNormalizer.splitTokens(query);
        if (syllables.size() < 2) return List.of();

        String plain = TextNormalizer.removeDiacritics(String.join(" ", syllables));
        if (byNoDiacritics.containsKey(plain)) return List.of();   // go dung roi, khong goi y

        Set<String> queryGrams = new java.util.HashSet<>(trigrams(plain));
        if (queryGrams.isEmpty()) return List.of();

        record Scored(String word, double score) {}
        List<Scored> scored = new ArrayList<>(16);
        for (Map.Entry<String, List<String>> e : byNoDiacritics.entrySet()) {
            Set<String> grams = new java.util.HashSet<>(trigrams(e.getKey()));
            int matched = 0;
            for (String g : queryGrams) if (grams.contains(g)) matched++;
            double jaccard = (double) matched / (queryGrams.size() + grams.size() - matched);
            if (jaccard >= 0.5) scored.add(new Scored(e.getValue().getFirst(), jaccard));
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<String> out = new ArrayList<>(Math.min(limit, scored.size()));
        for (int i = 0; i < scored.size() && i < limit; i++) out.add(scored.get(i).word());
        return out;
    }

    private static List<String> trigrams(String text) {
        String padded = "$$" + text + "$$";
        List<String> out = new ArrayList<>(padded.length());
        for (int i = 0; i + 3 <= padded.length(); i++) out.add(padded.substring(i, i + 3));
        return out;
    }
}
