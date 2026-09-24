package com.anhtuan.dict.nmt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tach tu cho mo hinh Marian (opus-mt), viet thuan Java.
 *
 * <h2>Vi sao tu viet</h2>
 * Duong chinh thong la dung thu vien {@code tokenizers} cua HuggingFace qua DJL. Da thu:
 * no lam <b>sup ca JVM</b> (EXCEPTION_UNCAUGHT_CXX_EXCEPTION trong native code) khi doc
 * {@code tokenizer.json} cua model nay, vi truong {@code precompiled_charsmap} bang null.
 * Doc ky file cau hinh thi thay phan con lai rat don gian:
 *
 * <pre>
 *   normalizer    : Precompiled voi charsmap = null   -> khong chuan hoa gi ca
 *   pre_tokenizer : WhitespaceSplit + Metaspace("▁")  -> cat theo khoang trang, them "▁"
 *   model         : Unigram, 53.685 muc (tu, diem)    -> Viterbi chon cach cat tot nhat
 *   post_processor: them "&lt;/s&gt;" o cuoi
 * </pre>
 *
 * Viet lai het chua toi 150 dong, doi lai bo duoc mot phu thuoc 18 MB kem thu vien native.
 *
 * <h2>Unigram la gi</h2>
 * Moi manh tu co mot diem (log xac suat). Cat mot tu thanh manh co nhieu cach; chon cach co
 * TONG DIEM CAO NHAT bang quy hoach dong (Viterbi). Vi du "unbelievable" co the thanh
 * "▁un|bel|iev|able" hoac "▁unbeliev|able" - cach nao tong diem cao hon thi thang.
 */
public final class MarianTokenizer {

    /** Ky tu thay cho khoang trang trong SentencePiece. */
    private static final char META = '▁';

    /** Cac id dac biet, doc tu config.json cua model. */
    public static final int EOS_ID = 0;
    public static final int UNK_ID = 1;
    public static final int PAD_ID = 53684;
    public static final int DECODER_START_ID = PAD_ID;

    /** Ten file tu vung da chuyen sang dang bang, sinh boi scripts/tai-model-nmt.ps1. */
    public static final String VOCAB_FILE = "vocab.tsv";

    private final Map<String, Integer> pieceToId;
    private final String[] idToPiece;
    private final float[] scores;
    private final int maxPieceLength;

    private MarianTokenizer(Map<String, Integer> pieceToId, String[] idToPiece,
                            float[] scores, int maxPieceLength) {
        this.pieceToId = pieceToId;
        this.idToPiece = idToPiece;
        this.scores = scores;
        this.maxPieceLength = maxPieceLength;
    }

    /** Doc {@code vocab.tsv}: moi dong la "manh<TAB>diem", so dong chinh la id. */
    public static MarianTokenizer load(Path vocabFile) {
        List<String> pieces = new ArrayList<>(60_000);
        List<Float> scoreList = new ArrayList<>(60_000);
        int maxLen = 1;
        try (BufferedReader r = Files.newBufferedReader(vocabFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.lastIndexOf('\t');
                if (tab < 0) continue;
                String piece = line.substring(0, tab);
                pieces.add(piece);
                scoreList.add(Float.parseFloat(line.substring(tab + 1)));
                maxLen = Math.max(maxLen, piece.length());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("khong doc duoc " + vocabFile, e);
        }

        String[] idToPiece = pieces.toArray(new String[0]);
        float[] scores = new float[scoreList.size()];
        Map<String, Integer> pieceToId = new HashMap<>(idToPiece.length * 2);
        for (int i = 0; i < idToPiece.length; i++) {
            scores[i] = scoreList.get(i);
            pieceToId.putIfAbsent(idToPiece[i], i);
        }
        return new MarianTokenizer(pieceToId, idToPiece, scores, maxLen);
    }

    public int vocabSize() {
        return idToPiece.length;
    }

    // ------------------------------------------------------------------ ma hoa

    /** Cau tieng Anh -&gt; day id, da them {@code &lt;/s&gt;} o cuoi dung nhu Marian cho. */
    public long[] encode(String text) {
        List<Integer> ids = new ArrayList<>(32);
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            viterbi(META + word, ids);
        }
        ids.add(EOS_ID);
        long[] out = new long[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return out;
    }

    /**
     * Cat mot tu thanh cac manh sao cho TONG DIEM cao nhat.
     *
     * <p>{@code best[i]} = diem tot nhat de phu het {@code word[0..i)}. Voi moi vi tri, thu
     * moi manh bat dau tu do; manh nao co trong tu vung thi cap nhat diem cho diem ket thuc
     * cua no. Cuoi cung lan nguoc theo {@code from[]} de lay day manh.
     */
    private void viterbi(String word, List<Integer> out) {
        int n = word.length();
        double[] best = new double[n + 1];
        int[] from = new int[n + 1];
        int[] pieceAt = new int[n + 1];
        java.util.Arrays.fill(best, Double.NEGATIVE_INFINITY);
        java.util.Arrays.fill(from, -1);
        best[0] = 0;

        for (int i = 0; i < n; i++) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue;
            int limit = Math.min(n, i + maxPieceLength);
            for (int j = i + 1; j <= limit; j++) {
                Integer id = pieceToId.get(word.substring(i, j));
                if (id == null) continue;
                double score = best[i] + scores[id];
                if (score > best[j]) { best[j] = score; from[j] = i; pieceAt[j] = id; }
            }
            // Duong lui: ky tu khong co trong tu vung -> mot ky tu = mot <unk>.
            // Phat nang de Viterbi chi chon khi khong con cach nao khac.
            if (best[i + 1] == Double.NEGATIVE_INFINITY) {
                best[i + 1] = best[i] - 100;
                from[i + 1] = i;
                pieceAt[i + 1] = UNK_ID;
            }
        }

        List<Integer> reversed = new ArrayList<>(8);
        int at = n;
        while (at > 0 && from[at] >= 0) {
            reversed.add(pieceAt[at]);
            at = from[at];
        }
        for (int i = reversed.size() - 1; i >= 0; i--) out.add(reversed.get(i));
    }

    // ------------------------------------------------------------------ giai ma

    /** Day id -&gt; cau tieng Viet. Bo cac id dac biet. */
    public String decode(List<Integer> ids) {
        StringBuilder sb = new StringBuilder(128);
        for (int id : ids) {
            if (id == EOS_ID || id == PAD_ID || id < 0 || id >= idToPiece.length) continue;
            sb.append(idToPiece[id]);
        }
        return sb.toString().replace(META, ' ').trim();
    }
}
