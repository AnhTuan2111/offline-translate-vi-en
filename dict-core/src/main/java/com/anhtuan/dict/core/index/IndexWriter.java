package com.anhtuan.dict.core.index;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Idiom;
import com.anhtuan.dict.core.model.Sense;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.nlp.ViCompounds;
import com.anhtuan.dict.core.pack.Utf8Compare;
import com.anhtuan.dict.core.pack.VarInt;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Dung inverted index Viet-&gt;Anh va index fuzzy (PLAN.md 7.1).
 *
 * <p>Sinh BA file:
 * <ul>
 *   <li>{@code vi.idx}        - term tieng Viet CO dau</li>
 *   <li>{@code vi-nodiac.idx} - term da BO dau; thieu file nay thi nguoi go nhanh
 *       ("cham soc") khong tim ra gi va app "cam giac ngu" ngay</li>
 *   <li>{@code tri.idx}       - trigram cua headword, de doan tu go sai</li>
 * </ul>
 *
 * <p>QUAN TRONG: docId = chi so cua entry trong dict.pack. Vi vay danh sach entry truyen
 * vao day phai duoc sap xep GIONG HET PackWriter. Ham nay tu sap xep lai bang cung
 * comparator nen goi voi danh sach chua sap xep cung dung.
 */
public final class IndexWriter {
    private IndexWriter() {}

    /** Mang int tang dan, tranh phi cua Integer boxing khi co ~700.000 cap postings. */
    private static final class IntList {
        private int[] a = new int[4];
        private int n;

        void add(int v) {
            if (n == a.length) {
                int[] b = new int[a.length * 2];
                System.arraycopy(a, 0, b, 0, n);
                a = b;
            }
            a[n++] = v;
        }

        int size() { return n; }
        int get(int i) { return a[i]; }
    }

    public record Stats(String fileName, int termCount, int docCount, long postingPairs, long fileSize) {}

    /**
     * Dung ca ba index vao {@code targetDir}.
     *
     * @return so lieu tung file, de CLI in ra doi chieu tieu chi nghiem thu M3
     */
    /** So lan mot phuong an phai lap lai thi moi duoc coi la tu ghep that. */
    public static final int MIN_COMPOUND_COUNT = 3;

    public static List<Stats> build(Path targetDir, List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(Entry::headwordNorm, Utf8Compare.COMPARATOR));

        // Rut danh sach tu ghep tieng Viet TU CHINH TU DIEN. Danh sach nay KHONG di vao
        // index: da do thu ca hai cach, nhet tu ghep thanh term rieng lam hai file index
        // phong tu 4,3 MB len 6,3 MB ma thu tu ket qua gan nhu khong khac. Ly do: he so do
        // phu truy van (binh phuong) von da lam gan het viec ma tu ghep dinh lam. Danh sach
        // chi duoc dung o buoc XEP LAI, noi no thuc su tao ra khac biet - xem
        // ReverseSearchService.
        Set<String> compoundWords = mineCompounds(sorted, MIN_COMPOUND_COUNT);
        ViCompounds.write(targetDir.resolve(ViCompounds.FILE_NAME), compoundWords);

        List<Stats> stats = new ArrayList<>(3);
        stats.add(writeIndex(targetDir.resolve(IndexFormat.VI_INDEX), sorted,
                IndexWriter::vietnameseTokens));
        stats.add(writeIndex(targetDir.resolve(IndexFormat.VI_NODIAC_INDEX), sorted,
                e -> vietnameseTokens(e).stream().map(TextNormalizer::removeDiacritics).toList()));
        stats.add(writeIndex(targetDir.resolve(IndexFormat.TRIGRAM_INDEX), sorted,
                e -> TrigramIndex.trigrams(e.headwordNorm())));
        return stats;
    }

    /**
     * Tim tu ghep tieng Viet bang cach dem cac PHUONG AN dich lap lai.
     *
     * <p>Moi phuong an trong mot dong nghia la mot don vi dich: dong "- trông nom, chăm sóc"
     * cho hai don vi. Phuong an dai 2-3 am tiet ma lap lai o nhieu muc tu khac nhau thi gan
     * nhu chac chan la mot tu ghep that, khong phai mot cum ngau nhien.
     *
     * <p>Do tren 200.060 dong nghia cua nguon: nguong 3 lan cho 16.592 tu, du ca "chăm sóc",
     * "ngân hàng", "kế hoạch", "máy tính". Ha xuong 2 lan thi duoc 31.438 tu nhung bat dau
     * lan cac cum ngau nhien; len 4 lan thi con 10.585 va bat dau sot tu that.
     */
    public static Set<String> mineCompounds(List<Entry> entries, int minCount) {
        Map<String, Integer> count = new HashMap<>(1 << 17);
        for (Entry e : entries) {
            for (Sense s : e.senses()) for (String g : s.glosses()) countAlternatives(count, g);
            for (Idiom i : e.idioms()) for (String g : i.glosses()) countAlternatives(count, g);
        }
        Set<String> out = new HashSet<>(count.size() / 4);
        for (Map.Entry<String, Integer> en : count.entrySet()) {
            if (en.getValue() >= minCount) out.add(en.getKey());
        }
        return out;
    }

    private static void countAlternatives(Map<String, Integer> count, String gloss) {
        for (String alt : TextNormalizer.glossAlternatives(gloss)) {
            List<String> syllables = TextNormalizer.splitTokens(alt);
            if (syllables.size() < 2 || syllables.size() > ViCompounds.MAX_SYLLABLES) continue;
            boolean allLetters = true;
            for (String syl : syllables) {
                for (int i = 0; i < syl.length(); i++) {
                    if (!Character.isLetter(syl.charAt(i))) { allLetters = false; break; }
                }
                if (!allLetters) break;
            }
            if (allLetters) count.merge(String.join(" ", syllables), 1, Integer::sum);
        }
    }

    /**
     * Toan bo token tieng Viet cua mot entry: nghia cua sense + nghia cua thanh ngu.
     *
     * <p>KHONG lay ban dich cua vi du: chung dai, nhieu tu chuc nang, se lam loang
     * diem BM25 va phong index len gap ba.
     */
    static List<String> vietnameseTokens(Entry e) {
        List<String> out = new ArrayList<>(16);
        for (Sense s : e.senses()) {
            for (String g : s.glosses()) out.addAll(TextNormalizer.splitTokens(g));
        }
        for (Idiom i : e.idioms()) {
            for (String g : i.glosses()) out.addAll(TextNormalizer.splitTokens(g));
        }
        return out;
    }

    private static Stats writeIndex(Path target, List<Entry> docs, Function<Entry, List<String>> tokenizer) {
        Map<String, IntList> postings = new HashMap<>(1 << 16);
        int[] docLengths = new int[docs.size()];
        long totalTokens = 0;
        long pairCount = 0;

        Map<String, Integer> tf = new HashMap<>(64);
        for (int docId = 0; docId < docs.size(); docId++) {
            List<String> tokens = tokenizer.apply(docs.get(docId));
            docLengths[docId] = Math.min(tokens.size(), IndexFormat.MAX_DOC_LENGTH);
            totalTokens += tokens.size();

            tf.clear();
            for (String t : tokens) tf.merge(t, 1, Integer::sum);
            for (Map.Entry<String, Integer> en : tf.entrySet()) {
                IntList list = postings.computeIfAbsent(en.getKey(), k -> new IntList());
                list.add(docId);
                list.add(en.getValue());
                pairCount++;
            }
        }

        List<String> terms = new ArrayList<>(postings.keySet());
        terms.sort(Utf8Compare.COMPARATOR);

        // TERMS + POSTINGS dung truoc de biet do dai, roi moi ghi file mot luot.
        ByteArrayOutputStream termsRegion = new ByteArrayOutputStream(terms.size() * 12);
        ByteArrayOutputStream postingsRegion = new ByteArrayOutputStream((int) pairCount * 3);
        int[] termOffsets = new int[terms.size()];
        int[] docFreqs = new int[terms.size()];
        long[] postingOffsets = new long[terms.size()];

        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            termOffsets[i] = termsRegion.size();
            byte[] tb = term.getBytes(StandardCharsets.UTF_8);
            termsRegion.write(tb, 0, tb.length);
            termsRegion.write(0);

            IntList list = postings.get(term);
            docFreqs[i] = list.size() / 2;
            postingOffsets[i] = postingsRegion.size();          // tam thoi tuong doi, cong base sau
            int prevDoc = 0;
            for (int k = 0; k < list.size(); k += 2) {
                int delta = list.get(k) - prevDoc;
                int freq = list.get(k + 1);
                // Bit thap nhat = "con byte tf dang sau". 85% cap co tf = 1 nen bo han
                // byte tf di, tiet kiem gan 1 MB tren ba file index.
                VarInt.write(postingsRegion, (delta << 1) | (freq > 1 ? 1 : 0));
                if (freq > 1) VarInt.write(postingsRegion, freq);
                prevDoc = list.get(k);
            }
        }

        byte[] termsBytes = termsRegion.toByteArray();
        byte[] postingsBytes = postingsRegion.toByteArray();

        long termsOffset = IndexFormat.HEADER_SIZE;
        long termPtrOffset = termsOffset + termsBytes.length;
        long docLenOffset = termPtrOffset + (long) terms.size() * IndexFormat.TERM_PTR_SIZE;
        long postingsBase = docLenOffset + (long) docs.size() * 2;

        float avgDocLen = docs.isEmpty() ? 1f : (float) ((double) totalTokens / docs.size());

        try {
            Files.createDirectories(targetDir(target));
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target), 1 << 16)) {
                ByteBuffer h = le(IndexFormat.HEADER_SIZE);
                h.put(IndexFormat.MAGIC);
                h.putInt(IndexFormat.OFF_TERM_COUNT, terms.size());
                h.putInt(IndexFormat.OFF_DOC_COUNT, docs.size());
                h.putFloat(IndexFormat.OFF_AVG_DOC_LEN, Math.max(avgDocLen, 1f));
                h.putLong(IndexFormat.OFF_TERMS_OFFSET, termsOffset);
                h.putLong(IndexFormat.OFF_TERM_PTR_OFFSET, termPtrOffset);
                h.putLong(IndexFormat.OFF_DOC_LEN_OFFSET, docLenOffset);
                out.write(h.array());

                out.write(termsBytes);

                ByteBuffer ptrs = le(terms.size() * IndexFormat.TERM_PTR_SIZE);
                for (int i = 0; i < terms.size(); i++) {
                    ptrs.putInt(termOffsets[i]);
                    ptrs.putInt(docFreqs[i]);
                    ptrs.putLong(postingsBase + postingOffsets[i]);
                }
                out.write(ptrs.array());

                ByteBuffer lens = le(docs.size() * 2);
                for (int len : docLengths) lens.putShort((short) len);
                out.write(lens.array());

                out.write(postingsBytes);
            }
            return new Stats(target.getFileName().toString(), terms.size(), docs.size(),
                    pairCount, Files.size(target));
        } catch (IOException e) {
            throw new UncheckedIOException("khong ghi duoc " + target, e);
        }
    }

    private static Path targetDir(Path target) {
        Path parent = target.toAbsolutePath().getParent();
        return parent == null ? Path.of(".") : parent;
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
}
