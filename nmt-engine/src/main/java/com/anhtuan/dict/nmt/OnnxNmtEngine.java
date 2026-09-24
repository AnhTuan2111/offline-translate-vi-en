package com.anhtuan.dict.nmt;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.anhtuan.dict.core.model.Candidate;
import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.model.SegmentKind;
import com.anhtuan.dict.core.spi.TranslationEngine;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dich ca cau bang MO HINH NO-RON chay cuc bo (PLAN.md muc 14).
 *
 * <h2>Noi ro: day la AI chay tren may nguoi dung</h2>
 * Khac han hai engine con lai. {@code DictionaryGlossEngine} va
 * {@code RuleBasedTranslationEngine} chi tra tu dien va ap luat - khong co mo hinh nao.
 * Engine nay nap mot mang no-ron 101 MB (opus-mt-en-vi, 6 lop encoder + 6 lop decoder) va
 * chay no bang CPU. Khong co mang, khong goi server, nhung <b>co AI</b>. UI phai ghi ro dieu
 * do cho nguoi dung biet.
 *
 * <h2>Cach chay</h2>
 * <pre>
 *   cau tieng Anh --[MarianTokenizer]--> day id
 *                 --[encoder.onnx]-----> vector ngu nghia [1, n, 512]
 *   vong lap: decoder.onnx(da sinh + vector) -> xac suat tu tiep theo -> lay tu diem cao nhat
 *                 --[MarianTokenizer]--> cau tieng Viet
 * </pre>
 *
 * <p>Ban nay dung <b>greedy</b> (luon lay tu diem cao nhat) va <b>khong dung KV-cache</b>:
 * moi buoc chay lai ca decoder tren toan bo phan da sinh, tuc do phuc tap O(n²). Doi lai
 * code ngan va khong phai quan ly hang chuc tensor trang thai. Do thuc te: cau 15-20 tu mat
 * khoang 1-2 giay - cham hon engine luat (2 ms) khoang nghin lan, nhung chat luong khac han.
 * Muon nhanh hon thi buoc tiep la KV-cache ({@code decoder_model_merged}) va beam search.
 *
 * <p>Tra ve DUNG MOT {@link Segment} kind = {@link SegmentKind#TRANSLATED}, cung giao uoc voi
 * engine dich bang luat, nen UI khong phai sua gi khi doi engine.
 */
public final class OnnxNmtEngine implements TranslationEngine, Closeable {

    public static final String ENGINE_ID = "onnx-opus-mt";

    /** Ten thu muc model trong thu muc du lieu. */
    public static final String MODEL_DIR = "nmt-en-vi";

    private static final int MAX_INPUT_TOKENS = 200;
    private static final int MAX_OUTPUT_TOKENS = 256;

    /**
     * Cam sinh lai mot day {@value} tu da tung xuat hien.
     *
     * <p>Giai ma greedy co mot benh kinh dien: gap cau KHONG DU chu ngu - vi ngu (dong tieu de,
     * gach dau dong) thi no khong biet dung o dau va lap mot cum den het gioi han. Do thuc te
     * tren 13 cau van ban ky thuat that: 4 cau bi lap, vi du "kích thước kích thước, kích
     * thước, dự án dự án thời gian dự án". Chan n-gram lap la cach re nhat de chua - beam
     * search moi la cach dung, nhung dat hon nhieu.
     */
    private static final int NO_REPEAT_NGRAM = 3;

    /**
     * So gia thuyet giu song song khi giai ma. 4 la gia tri chinh model nay duoc huan luyen de
     * dung ({@code num_beams} trong generation_config.json). Dat 1 = quay ve greedy.
     *
     * <p>Vi sao can: greedy chon tu tot nhat o TUNG BUOC, nen mot lua chon dau cau te co the
     * keo ca cau di sai ma khong cach nao quay lai. Do thuc te: "Identify and justify the most
     * appropriate model" bi greedy dich thanh "Và biện minh cho mô hình..." - nuot han chu
     * "Identify" ngay tu buoc dau.
     */
    private static final int BEAM_SIZE = 4;

    /**
     * He so phat do dai. Chia diem cho {@code len^alpha} truoc khi so sanh cac gia thuyet da
     * xong: khong co no thi cau NGAN luon thang, vi moi tu them vao deu lam tong log xac suat
     * am hon. 1.0 la gia tri mac dinh cua Marian.
     */
    private static final double LENGTH_PENALTY = 1.0;

    private final OrtEnvironment env;
    private final OrtSession encoder;
    private final OrtSession decoder;
    private final MarianTokenizer tokenizer;
    private final long loadMillis;

    private OnnxNmtEngine(OrtEnvironment env, OrtSession encoder, OrtSession decoder,
                          MarianTokenizer tokenizer, long loadMillis) {
        this.env = env;
        this.encoder = encoder;
        this.decoder = decoder;
        this.tokenizer = tokenizer;
        this.loadMillis = loadMillis;
    }

    /** Thu muc model co day du file khong. Dung de biet co nen hien engine nay trong UI. */
    public static boolean isInstalled(Path modelDir) {
        return Files.isRegularFile(modelDir.resolve(MarianTokenizer.VOCAB_FILE))
                && Files.isRegularFile(modelDir.resolve("onnx/encoder_model_quantized.onnx"))
                && Files.isRegularFile(modelDir.resolve("onnx/decoder_model_quantized.onnx"));
    }

    /**
     * Nap model. Ton vai giay va ~300 MB RAM, nen chi goi khi nguoi dung thuc su chon engine
     * nay - dung nap san luc khoi dong.
     */
    public static OnnxNmtEngine load(Path modelDir) {
        if (!isInstalled(modelDir)) {
            throw new IllegalStateException("""
                    Chua cai mo hinh NMT tai %s.
                    Chay:  .\\scripts\\tai-model-nmt.ps1   (tai ~101 MB, mot lan)
                    """.formatted(modelDir.toAbsolutePath()));
        }
        long t0 = System.nanoTime();
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(Math.min(4, Runtime.getRuntime().availableProcessors()));
            OrtSession encoder = env.createSession(
                    modelDir.resolve("onnx/encoder_model_quantized.onnx").toString(), options);
            OrtSession decoder = env.createSession(
                    modelDir.resolve("onnx/decoder_model_quantized.onnx").toString(), options);
            MarianTokenizer tokenizer =
                    MarianTokenizer.load(modelDir.resolve(MarianTokenizer.VOCAB_FILE));
            return new OnnxNmtEngine(env, encoder, decoder, tokenizer,
                    (System.nanoTime() - t0) / 1_000_000);
        } catch (OrtException e) {
            throw new IllegalStateException("khong nap duoc mo hinh ONNX: " + e.getMessage(), e);
        }
    }

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public String displayName() {
        return "Dịch bằng mô hình AI chạy trên máy (opus-mt)";
    }

    public long loadMillis() {
        return loadMillis;
    }

    @Override
    public List<Segment> translate(String sentence) {
        if (sentence == null || sentence.isBlank()) return List.of();
        String vi = translateToString(sentence);
        return List.of(new Segment(sentence, 0, sentence.length(), SegmentKind.TRANSLATED,
                List.of(new Candidate(sentence, vi, null, 1.0))));
    }

    private String translateToString(String sentence) {
        long[] ids = tokenizer.encode(endWithPunctuation(sentence));
        if (ids.length > MAX_INPUT_TOKENS) ids = Arrays.copyOf(ids, MAX_INPUT_TOKENS);

        long[] maskRow = new long[ids.length];
        Arrays.fill(maskRow, 1L);

        try (OnnxTensor inputIds = OnnxTensor.createTensor(env, new long[][] {ids});
             OnnxTensor mask = OnnxTensor.createTensor(env, new long[][] {maskRow})) {

            Map<String, OnnxTensor> encoderInput = new HashMap<>(4);
            encoderInput.put("input_ids", inputIds);
            encoderInput.put("attention_mask", mask);

            try (OrtSession.Result encoded = encoder.run(encoderInput)) {
                float[][][] hidden = (float[][][]) encoded.get(0).getValue();
                return decodeBeam(hidden, maskRow);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("loi khi chay mo hinh: " + e.getMessage(), e);
        }
    }

    /**
     * Them dau cham neu cau chua co dau ket thuc.
     *
     * <p>Model duoc huan luyen tren CAU HOAN CHINH. Dua vao mot manh cau khong dau cham -
     * kieu dong gach dau dong trong de thi - thi no khong co tin hieu nao de dung, va de roi
     * vao vong lap. Them mot dau cham la sua duoc phan lon.
     */
    private static String endWithPunctuation(String sentence) {
        String trimmed = sentence.strip();
        if (trimmed.isEmpty()) return trimmed;
        char last = trimmed.charAt(trimmed.length() - 1);
        return ".!?:;\"')]".indexOf(last) >= 0 ? trimmed : trimmed + ".";
    }

    /**
     * Cac tu bi cam o buoc hien tai vi sinh ra chung se tao mot n-gram da co.
     *
     * <p>Nhin {@value #NO_REPEAT_NGRAM}-1 tu vua sinh, tim trong phan da sinh xem day do da
     * xuat hien chua; neu roi thi tu di ngay sau no lan truoc bi cam lan nay.
     */
    private static java.util.Set<Integer> bannedTokens(List<Integer> generated) {
        int n = NO_REPEAT_NGRAM;
        if (generated.size() < n) return java.util.Set.of();
        java.util.Set<Integer> banned = new java.util.HashSet<>(4);
        List<Integer> suffix = generated.subList(generated.size() - (n - 1), generated.size());
        for (int i = 0; i + n <= generated.size(); i++) {
            if (generated.subList(i, i + n - 1).equals(suffix)) banned.add(generated.get(i + n - 1));
        }
        return banned;
    }

    /** Mot gia thuyet dich dang duoc giu. */
    private record Beam(List<Integer> tokens, double score) {}

    /**
     * Giai ma bang beam search: giu {@value #BEAM_SIZE} gia thuyet song song thay vi mot.
     *
     * <p>Moi buoc, tat ca gia thuyet con song deu co CUNG do dai nen gom duoc thanh mot lo
     * (batch) va chi chay decoder MOT lan - chay rieng tung gia thuyet thi cham gap bon.
     *
     * <p>Diem cua mot gia thuyet la tong log xac suat cua cac tu trong no. Phai dung
     * log-softmax chu khong dung thang logit: logit cua hai gia thuyet khac nhau lech nhau mot
     * hang so rieng, cong thang vao thi so sanh sai.
     */
    private String decodeBeam(float[][][] encoderHidden, long[] maskRow) throws OrtException {
        List<Beam> alive = new ArrayList<>(BEAM_SIZE);
        alive.add(new Beam(List.of(), 0.0));
        List<Beam> finished = new ArrayList<>(BEAM_SIZE);

        for (int step = 0; step < MAX_OUTPUT_TOKENS && !alive.isEmpty(); step++) {
            int batch = alive.size();
            int len = alive.get(0).tokens().size() + 1;

            long[][] decoderIds = new long[batch][len];
            for (int b = 0; b < batch; b++) {
                decoderIds[b][0] = MarianTokenizer.DECODER_START_ID;
                List<Integer> tokens = alive.get(b).tokens();
                for (int i = 0; i < tokens.size(); i++) decoderIds[b][i + 1] = tokens.get(i);
            }

            float[][][] logits;
            try (OnnxTensor decoderInput = OnnxTensor.createTensor(env, decoderIds);
                 OnnxTensor hiddenTensor = OnnxTensor.createTensor(env, tile(encoderHidden, batch));
                 OnnxTensor maskTensor = OnnxTensor.createTensor(env, tile(maskRow, batch))) {
                Map<String, OnnxTensor> input = new HashMap<>(4);
                input.put("input_ids", decoderInput);
                input.put("encoder_hidden_states", hiddenTensor);
                input.put("encoder_attention_mask", maskTensor);
                try (OrtSession.Result result = decoder.run(input)) {
                    logits = (float[][][]) result.get(0).getValue();
                }
            }

            List<Beam> candidates = new ArrayList<>(batch * (BEAM_SIZE + 1));
            for (int b = 0; b < batch; b++) {
                Beam beam = alive.get(b);
                float[] last = logits[b][len - 1];
                double logSumExp = logSumExp(last);
                java.util.Set<Integer> banned = bannedTokens(beam.tokens());

                for (int token : topK(last, banned, BEAM_SIZE + 1)) {
                    double score = beam.score() + (last[token] - logSumExp);
                    if (token == MarianTokenizer.EOS_ID) {
                        finished.add(new Beam(beam.tokens(), score));
                    } else {
                        List<Integer> tokens = new ArrayList<>(beam.tokens());
                        tokens.add(token);
                        candidates.add(new Beam(tokens, score));
                    }
                }
            }

            candidates.sort((x, y) -> Double.compare(y.score(), x.score()));
            alive = new ArrayList<>(candidates.subList(0, Math.min(BEAM_SIZE, candidates.size())));

            // Dung som: gia thuyet song tot nhat da khong the duoi kip gia thuyet da xong.
            if (finished.size() >= BEAM_SIZE && !alive.isEmpty()) {
                double bestAlive = normalised(alive.get(0));
                double bestFinished = finished.stream()
                        .mapToDouble(OnnxNmtEngine::normalised).max().orElse(Double.NEGATIVE_INFINITY);
                if (bestAlive < bestFinished) break;
            }
        }

        // Khong gia thuyet nao ket thuc dung han (cau qua dai) thi lay gia thuyet song tot nhat.
        List<Beam> remaining = alive;
        Beam best = finished.stream()
                .max((x, y) -> Double.compare(normalised(x), normalised(y)))
                .orElseGet(() -> remaining.isEmpty() ? new Beam(List.of(), 0) : remaining.get(0));
        return tokenizer.decode(best.tokens());
    }

    /** Diem da chia cho do dai - dung khi so sanh cac gia thuyet dai ngan khac nhau. */
    private static double normalised(Beam beam) {
        int length = Math.max(1, beam.tokens().size());
        return beam.score() / Math.pow(length, LENGTH_PENALTY);
    }

    private static double logSumExp(float[] values) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : values) {
            if (v > max) max = v;
        }
        double sum = 0;
        for (float v : values) sum += Math.exp(v - max);
        return max + Math.log(sum);
    }

    /** {@code k} tu co diem cao nhat, bo qua cac tu bi cam. */
    private static List<Integer> topK(float[] logits, java.util.Set<Integer> banned, int k) {
        java.util.PriorityQueue<Integer> heap =
                new java.util.PriorityQueue<>((x, y) -> Float.compare(logits[x], logits[y]));
        for (int v = 0; v < logits.length; v++) {
            if (v == MarianTokenizer.PAD_ID || banned.contains(v)) continue;
            heap.add(v);
            if (heap.size() > k) heap.poll();
        }
        List<Integer> out = new ArrayList<>(heap);
        out.sort((x, y) -> Float.compare(logits[y], logits[x]));
        return out;
    }

    /** Nhan ban vector ngu nghia cua cau nguon ra {@code batch} ban de chay mot lo. */
    private static float[][][] tile(float[][][] source, int batch) {
        float[][][] out = new float[batch][][];
        for (int b = 0; b < batch; b++) out[b] = source[0];
        return out;
    }

    private static long[][] tile(long[] row, int batch) {
        long[][] out = new long[batch][];
        for (int b = 0; b < batch; b++) out[b] = row;
        return out;
    }

    @Override
    public void close() {
        try {
            decoder.close();
            encoder.close();
        } catch (OrtException e) {
            throw new IllegalStateException("khong dong duoc phien ONNX", e);
        }
    }
}
