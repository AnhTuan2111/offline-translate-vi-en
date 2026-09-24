package com.anhtuan.dict.core.service;

import com.anhtuan.dict.core.index.BM25Scorer;
import com.anhtuan.dict.core.index.InvertedIndex;
import com.anhtuan.dict.core.index.TrigramIndex;
import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Idiom;
import com.anhtuan.dict.core.model.Sense;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.pack.PackReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Viet-&gt;Anh va search theo muc do lien quan (PLAN.md F3, F4).
 *
 * <p>Ba duong di:
 * <ul>
 *   <li>{@link #searchVietnamese} - BM25 tren index nghia tieng Viet, tu chon index
 *       co dau hay khong dau tuy theo truy van nguoi dung go</li>
 *   <li>{@link #fuzzyEnglish} - trigram + Jaccard, CHI dung khi tra chinh xac da truot</li>
 * </ul>
 *
 * <h2>Vi sao BM25 tho KHONG dung duoc cho tu dien</h2>
 * Do thuc te: go "chăm sóc" bang BM25 tho ra
 * {@code [herdsman, childminding, horse-hoe, loving-kindness]} - toan tu hiem, trong khi
 * {@code care} tut hang. Ly do la BM25 PHAT tai lieu dai (he so b = 0,75), ma trong tu dien
 * "tai lieu dai" lai chinh la dau hieu cua TU QUAN TRONG: {@code care} co 12 nghia,
 * {@code horse-hoe} co 1.
 *
 * <p>Nen diem BM25 chi dung de LOC {@value #RERANK_POOL} ung vien, sau do doc entry that
 * len va xep lai bang bon yeu to:
 * <ol>
 *   <li>do phu truy van: tai lieu khop du ca hai tu "chăm" + "sóc" phai tren tai lieu
 *       chi khop mot tu (binh phuong de tach hang manh)</li>
 *   <li>khop nguyen cum trong mot dong nghia (x2), khop dung ca dong nghia (x3 - PLAN.md 7.2)</li>
 *   <li>do phu NGUOC: truy van chiem bao nhieu phan cua dong nghia. "sự chăm sóc" tot hon
 *       "người chăm sóc gia súc" khi nguoi ta go "chăm sóc"</li>
 *   <li>do noi bat: {@code 1 + 0,25 x ln(1 + do dai tai lieu)} - bu lai chinh cai
 *       BM25 vua phat</li>
 * </ol>
 */
public final class ReverseSearchService {

    /** So ung vien lay ra tu BM25 de xep lai. Lon hon = chinh xac hon = cham hon. */
    private static final int RERANK_POOL = 400;

    /**
     * So mu cua he so "do noi bat" = {@code doDaiTaiLieu ^ PROMINENCE}.
     *
     * <p>Day la thay the duy nhat cho danh sach tan suat tu ma ban offline khong co:
     * trong tu dien, muc tu DAI la muc tu QUAN TRONG. {@code bank} co 20 nghia va vi du,
     * {@code clearing-house} co 1 - nguoi go "ngân hàng" muon thay {@code bank} truoc.
     *
     * <p>Da sweep tren cac truy van that: 0 (tat) va 0,25 khong day duoc tu pho thong len,
     * 0,5 thi cac muc khong lo kieu {@code take} / {@code set} bat dau chen vao moi truy van.
     * 0,35 la diem can bang.
     */
    private static final double PROMINENCE =
            Double.parseDouble(System.getProperty("dict.prominence", "0.35"));

    private final PackReader pack;
    private final InvertedIndex viIndex;
    private final InvertedIndex viNoDiacIndex;
    private final InvertedIndex trigramIndex;

    public ReverseSearchService(PackReader pack, InvertedIndex viIndex,
                                InvertedIndex viNoDiacIndex, InvertedIndex trigramIndex) {
        this.pack = pack;
        this.viIndex = viIndex;
        this.viNoDiacIndex = viNoDiacIndex;
        this.trigramIndex = trigramIndex;
    }

    /**
     * @param display      chuoi hien cho nguoi dung: headword, HOAC cum thanh ngu neu nghia
     *                     khop nam o dong '!' - nho vay go "chăm sóc" hien ra duoc
     *                     "to look after" chu khong phai mot chu "look" tro tro
     * @param matchedGloss dong nghia khop nhat, de UI giai thich ngay ly do co ket qua nay
     */
    public record Hit(Entry entry, int docId, double score, String display, String matchedGloss) {}

    /**
     * Tim tu tieng Anh tu nghia tieng Viet.
     *
     * <p>Tu dong chon index: go "chăm sóc" dung index co dau, go "cham soc" dung index
     * khong dau. Nguoi Viet go nhanh thuong bo dau - thieu duong thu hai la app
     * "cam giac ngu" ngay (PLAN.md 7.1).
     */
    public List<Hit> searchVietnamese(String query, int limit) {
        List<String> queryTokens = TextNormalizer.splitTokens(query);
        if (queryTokens.isEmpty()) return List.of();

        boolean hasDiacritics = !TextNormalizer.removeDiacritics(query).equals(query);
        InvertedIndex index = hasDiacritics ? viIndex : viNoDiacIndex;
        List<String> terms = hasDiacritics
                ? queryTokens
                : queryTokens.stream().map(TextNormalizer::removeDiacritics).toList();

        BM25Scorer scorer = new BM25Scorer(index.docCount(), index.avgDocLength(),
                BM25Scorer.DICTIONARY_B);
        Map<Integer, Double> bm25 = new HashMap<>(4096);
        Map<Integer, Integer> hitTerms = new HashMap<>(4096);
        for (String term : terms) {
            int df = index.docFreq(term);
            if (df == 0) continue;
            for (int[] posting : index.postings(term)) {
                int docId = posting[0];
                bm25.merge(docId, scorer.score(df, posting[1], index.docLength(docId)), Double::sum);
                hitTerms.merge(docId, 1, Integer::sum);
            }
        }
        if (bm25.isEmpty()) return List.of();

        // Loc ung vien. Hai dieu quan trong o buoc nay:
        //  - uu tien tuyet doi tai lieu khop NHIEU TU truy van nhat;
        //  - phai nhan do noi bat NGAY TU DAY, khong doi den luc xep lai. Go "đi" co
        //    ~20.000 tai lieu khop; neu cat pool bang diem BM25 tho thi @go (tai lieu rat
        //    dai nen bi BM25 phat) roi khoi pool va khong bao gio duoc xep lai. Do dai
        //    tai lieu doc duoc tu DOC_LENS bang mot phep doc mmap, khong ton gi.
        List<Map.Entry<Integer, Double>> pool = new ArrayList<>(bm25.entrySet());
        pool.sort(Comparator.<Map.Entry<Integer, Double>>comparingInt(
                        e -> -hitTerms.getOrDefault(e.getKey(), 0))
                .thenComparing(Comparator.comparingDouble(
                        (Map.Entry<Integer, Double> e) ->
                                -e.getValue() * prominence(index.docLength(e.getKey())))));
        if (pool.size() > RERANK_POOL) pool = pool.subList(0, RERANK_POOL);

        String joinedQuery = String.join(" ", terms);
        List<Hit> hits = new ArrayList<>(pool.size());
        for (Map.Entry<Integer, Double> e : pool) {
            int docId = e.getKey();
            Entry entry = pack.entryAt(docId);
            GlossMatch gm = bestGloss(entry, terms, hasDiacritics, joinedQuery);

            double queryCoverage = (double) hitTerms.getOrDefault(docId, 1) / terms.size();
            double glossCoverage = gm.glossTokens() == 0
                    ? 0 : (double) gm.matchedInGloss() / gm.glossTokens();
            double prominence = prominence(index.docLength(docId));
            double factor = BM25Scorer.boost(gm.exactGloss(), gm.inPrimaryGloss(),
                    !entry.isMultiWord(), entry.headwordNorm().length());
            if (gm.phraseHit() && !gm.exactGloss()) factor *= 2.0;

            double score = e.getValue()
                    * queryCoverage * queryCoverage
                    * (0.4 + 0.6 * glossCoverage)
                    * prominence
                    * factor;
            hits.add(new Hit(entry, docId, score, gm.display() == null ? entry.headword() : gm.display(),
                    gm.gloss()));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return dedupe(hits, limit);
    }

    private static double prominence(int docLength) {
        return Math.pow(1 + docLength, PROMINENCE);
    }

    /**
     * Bo ket qua trung. Nguon co rat nhieu cap trung lap that: cum "to send away" nam
     * trong ca "@send" lan "@away", "ride" va "ridden" la hai entry cung mot dong nghia.
     * Hien ca hai chi lam nguoi dung roi mat.
     */
    private static List<Hit> dedupe(List<Hit> sorted, int limit) {
        List<Hit> out = new ArrayList<>(Math.min(limit, sorted.size()));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Hit h : sorted) {
            if (out.size() >= limit) break;
            if (seen.add(h.display() + " " + h.matchedGloss())) out.add(h);
        }
        return out;
    }

    /**
     * Doan tu tieng Anh go sai (PLAN.md 7.3). Xep hang bang he so Jaccard tren trigram
     * ky tu; chi nen goi KHI tra chinh xac da truot.
     */
    public List<Hit> fuzzyEnglish(String query, int limit) {
        String norm = TextNormalizer.normalizeHeadword(query);
        List<String> grams = TrigramIndex.trigrams(norm);
        if (grams.isEmpty()) return List.of();

        Map<Integer, Integer> matched = new HashMap<>(4096);
        for (String g : grams) {
            for (int[] posting : trigramIndex.postings(g)) {
                matched.merge(posting[0], 1, Integer::sum);
            }
        }
        List<Map.Entry<Integer, Integer>> pool = new ArrayList<>(matched.entrySet());
        pool.sort(Map.Entry.<Integer, Integer>comparingByValue().reversed());
        if (pool.size() > RERANK_POOL) pool = pool.subList(0, RERANK_POOL);

        List<Hit> hits = new ArrayList<>(pool.size());
        for (Map.Entry<Integer, Integer> e : pool) {
            double j = TrigramIndex.jaccard(e.getValue(), grams.size(),
                    trigramIndex.docLength(e.getKey()));
            if (j < 0.2) continue;
            Entry entry = pack.entryAt(e.getKey());
            hits.add(new Hit(entry, e.getKey(), j, entry.headword(), firstGloss(entry)));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return dedupe(hits, limit);
    }

    // ------------------------------------------------------------------ noi bo

    private record GlossMatch(String gloss, String display, int matchedInGloss, int glossTokens,
                              boolean exactGloss, boolean phraseHit, boolean inPrimaryGloss) {}

    /** Dong nghia khop nhat cua mot entry, kem cac co dung de tinh diem. */
    private static GlossMatch bestGloss(Entry entry, List<String> terms,
                                        boolean hasDiacritics, String joinedQuery) {
        GlossMatch best = null;
        boolean isFirst = true;

        for (Sense s : entry.senses()) {
            for (String gloss : s.glosses()) {
                GlossMatch m = scoreGloss(gloss, entry.headword(), terms, hasDiacritics,
                        joinedQuery, isFirst);
                best = better(best, m);
                isFirst = false;
            }
        }
        for (Idiom i : entry.idioms()) {
            for (String gloss : i.glosses()) {
                GlossMatch m = scoreGloss(gloss, i.phrase(), terms, hasDiacritics,
                        joinedQuery, false);
                best = better(best, m);
            }
        }
        return best == null
                ? new GlossMatch(null, entry.headword(), 0, 0, false, false, false)
                : best;
    }

    private static GlossMatch better(GlossMatch a, GlossMatch b) {
        if (a == null) return b;
        if (b == null) return a;
        if (b.matchedInGloss() != a.matchedInGloss()) {
            return b.matchedInGloss() > a.matchedInGloss() ? b : a;
        }
        // Cung so tu khop -> chon dong nghia NGAN hon: no noi dung y nguoi dung hon.
        return b.glossTokens() < a.glossTokens() ? b : a;
    }

    private static GlossMatch scoreGloss(String gloss, String display, List<String> terms,
                                         boolean hasDiacritics, String joinedQuery, boolean isFirst) {
        List<String> tokens = TextNormalizer.splitTokens(gloss);
        List<String> cmp = hasDiacritics
                ? tokens
                : tokens.stream().map(TextNormalizer::removeDiacritics).toList();

        int matched = 0;
        for (String t : terms) if (cmp.contains(t)) matched++;

        String joinedGloss = String.join(" ", cmp);
        boolean exact = joinedGloss.equals(joinedQuery);
        boolean phrase = !exact && joinedGloss.contains(joinedQuery);
        return new GlossMatch(gloss, display, matched, cmp.size(), exact, phrase, isFirst && matched > 0);
    }

    private static String firstGloss(Entry entry) {
        for (Sense s : entry.senses()) {
            String g = s.primaryGloss();
            if (g != null) return g;
        }
        for (Idiom i : entry.idioms()) {
            if (!i.glosses().isEmpty()) return i.glosses().getFirst();
        }
        return null;
    }
}
