package com.anhtuan.dict.core.service;

import com.anhtuan.dict.core.model.Candidate;
import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.model.SegmentKind;
import com.anhtuan.dict.core.nlp.FunctionWords;
import com.anhtuan.dict.core.nlp.Lemmatizer;
import com.anhtuan.dict.core.spi.TranslationEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dich CA CAU Anh -&gt; Viet bang luat, dua tren ket qua chu giai cua
 * {@link DictionaryGlossEngine}.
 *
 * <h2>Day la gi va KHONG phai la gi</h2>
 * Day la dich may dua tren luat: chon nghia theo tu loai, dich cung tu chuc nang, roi sap
 * lai trat tu tu cho dung tieng Viet. No cho ra cau doc duoc voi cau tran thuat thong thuong,
 * va se sai voi cau phuc tap, thanh ngu, hay cau nhieu menh de long nhau.
 * No KHONG phai dich may no-ron - muon chat luong do thi phai cam mot model NMT vao dung
 * cai cong {@link TranslationEngine} nay (PLAN.md muc 14).
 *
 * <h2>Ba viec no lam, theo thu tu</h2>
 * <ol>
 *   <li><b>Tu chuc nang</b> tra bang {@link FunctionWords} thay vi tra tu dien. Tu dien dich
 *       {@code he} thanh "nó, anh ấy, ông ấy... (chỉ người và động vật giống đực)" - nhet
 *       nguyen chuoi do vao cau thi khong con gi doc duoc.</li>
 *   <li><b>Chon nghia theo tu loai.</b> Tu dien da ghi san tu loai cho tung nhom nghia;
 *       chi can doan dung tu loai trong ngu canh la chon duoc nghia dung. {@code old} trong
 *       "the old system" phai lay nghia tinh tu ("cũ") chu khong phai danh tu ("thời xưa").</li>
 *   <li><b>Sap lai trat tu.</b> Tieng Anh la DANH NGU nguoc voi tieng Viet:
 *       {@code the old system} -&gt; "hệ thống cũ", {@code his job} -&gt; "công việc của anh ấy".
 *       Them dau hieu thi: {@code gave} -&gt; "đã ...", {@code will} -&gt; "sẽ ...".</li>
 * </ol>
 *
 * <p>Tra ve DUNG MOT {@link Segment} kind = {@link SegmentKind#TRANSLATED}, dung nhu giao uoc
 * ma {@link TranslationEngine} dat ra cho engine dich nguyen cau.
 */
public final class RuleBasedTranslationEngine implements TranslationEngine {

    public static final String ENGINE_ID = "rule-based-vi";

    private final DictionaryGlossEngine glossEngine;
    private final LookupService lookup;

    public RuleBasedTranslationEngine(DictionaryGlossEngine glossEngine, LookupService lookup) {
        this.glossEngine = glossEngine;
        this.lookup = lookup;
    }

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public String displayName() {
        return "Dịch câu bằng luật (thử nghiệm)";
    }

    /** Tu loai da doan cho tung doan. */
    private enum Pos { NOUN, VERB, ADJ, ADV, FUNC, PUNCT, UNKNOWN }

    private static final class Item {
        String source;
        String vi;
        Pos pos = Pos.UNKNOWN;
        FunctionWords.Fw fw;
        List<Candidate> candidates = List.of();
        boolean past;
        boolean gerund;
        boolean dropped;
        /** Da nuot chu "not" cua tu ben canh - tu nay khong duoc bo di nua. */
        boolean negated;

        boolean isFunc(FunctionWords.Category... cats) {
            if (fw == null) return false;
            for (FunctionWords.Category c : cats) if (fw.cat() == c) return true;
            return false;
        }

        boolean isContent() {
            return fw == null && pos != Pos.PUNCT;
        }
    }

    @Override
    public List<Segment> translate(String sentence) {
        if (sentence == null || sentence.isBlank()) return List.of();

        List<Item> items = toItems(glossEngine.translate(sentence));
        assignPartOfSpeech(items);
        chooseVietnamese(items);
        applyGrammarRules(items);
        String vi = join(items);

        return List.of(new Segment(sentence, 0, sentence.length(), SegmentKind.TRANSLATED,
                List.of(new Candidate(sentence, vi, null, 1.0))));
    }

    /** Ban chu giai tung cum - UI hien duoi cau dich de nguoi dung doi chieu va sua. */
    public List<Segment> glossSegments(String sentence) {
        return glossEngine.translate(sentence);
    }

    // ------------------------------------------------------------------ buoc 1: doc segment

    private static List<Item> toItems(List<Segment> segments) {
        List<Item> items = new ArrayList<>(segments.size());
        for (Segment s : segments) {
            String text = s.sourceText();
            if (s.kind() == SegmentKind.PUNCT) {
                if (text.isBlank()) continue;                 // khoang trang tu sinh lai luc noi
                Item it = new Item();
                it.source = text.trim();
                it.vi = text.trim();
                it.pos = Pos.PUNCT;
                items.add(it);
                continue;
            }
            Item it = new Item();
            it.source = text;
            it.candidates = s.candidates();
            it.fw = FunctionWords.get(text);
            String firstWord = text.split("\\s+")[0];
            it.past = Lemmatizer.isPastForm(firstWord);
            it.gerund = firstWord.toLowerCase(Locale.ROOT).endsWith("ing");
            if (s.kind() == SegmentKind.PHRASE) it.pos = Pos.VERB;   // cum dong tu la chu yeu
            items.add(it);
        }
        return items;
    }

    // ------------------------------------------------------------------ buoc 2: doan tu loai

    /**
     * Doan tu loai ma KHONG dung POS tagger.
     *
     * <p>Meo o day: tu dien da ghi san tu loai cho tung nhom nghia, nen khong can doan tu loai
     * tu con so khong - chi can chon giua vai kha nang ma tu dien ĐA LIET KE. Ngu canh xung
     * quanh (mao tu, dai tu, tro dong tu) du de chon dung trong phan lon truong hop.
     */
    private void assignPartOfSpeech(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.pos == Pos.PUNCT) continue;
            if (it.fw != null) {
                it.pos = Pos.FUNC;
                continue;
            }
            if (it.pos == Pos.VERB) continue;                  // cum da chot o buoc truoc

            Item prev = i > 0 ? items.get(i - 1) : null;
            Item next = i + 1 < items.size() ? items.get(i + 1) : null;
            String w = it.source.toLowerCase(Locale.ROOT);

            boolean hasNoun = hasPos(it, "danh từ");
            boolean hasVerb = hasPos(it, "động từ");
            boolean hasAdj = hasPos(it, "tính từ");
            boolean hasAdv = hasPos(it, "phó từ") || hasPos(it, "trạng từ");

            if (w.endsWith("ly") && hasAdv) {
                it.pos = Pos.ADV;
            } else if (prev != null && prev.isFunc(FunctionWords.Category.TRANG_TU) && hasAdj) {
                // "very COLD", "too SMALL": sau trang tu muc do gan nhu chac chan la tinh tu
                it.pos = Pos.ADJ;
            } else if (next != null && next.isContent() && hasAdj) {
                // Dung truoc mot tu noi dung khac -> gan nhu chac chan la bo nghia cho no.
                // "the OLD system", "the GROWING number".
                it.pos = Pos.ADJ;
            } else if (prev != null && prev.isFunc(FunctionWords.Category.PRONOUN,
                    FunctionWords.Category.MODAL, FunctionWords.Category.FUTURE,
                    FunctionWords.Category.NEGATION, FunctionWords.Category.DO,
                    FunctionWords.Category.INFINITIVE, FunctionWords.Category.HAVE)
                    && (hasVerb || promoteToVerbViaLemma(it))) {
                // Sau dai tu / tro dong tu thi gan nhu chac chan la dong tu. Nguon hay co
                // muc tu rieng cho dang chia ("@finished" chi ghi tinh tu), nen phai lui ve
                // nguyen the moi lay duoc nghia dong tu.
                it.pos = Pos.VERB;
            } else if (prev != null && prev.isFunc(FunctionWords.Category.BE) && it.gerund
                    && (hasVerb || promoteToVerbViaLemma(it))) {
                it.pos = Pos.VERB;
            } else if (prev != null && prev.isFunc(FunctionWords.Category.ARTICLE,
                    FunctionWords.Category.QUANTIFIER, FunctionWords.Category.POSSESSIVE,
                    FunctionWords.Category.DEMONSTRATIVE, FunctionWords.Category.GIOI_TU)
                    && hasNoun) {
                it.pos = Pos.NOUN;
            } else if (it.past && hasVerb) {
                it.pos = Pos.VERB;
            } else if (hasNoun) {
                it.pos = Pos.NOUN;
            } else if (hasVerb) {
                it.pos = Pos.VERB;
            } else if (hasAdj) {
                it.pos = Pos.ADJ;
            } else if (hasAdv) {
                it.pos = Pos.ADV;
            } else {
                it.pos = Pos.UNKNOWN;
            }
        }
    }

    /**
     * "reading" trong "is reading" phai la DONG TU, nhung nguon lai co han mot muc tu
     * {@code @reading} chi mang tu loai danh tu ("sự đọc"). Truong hop nay phai lui ve
     * dang nguyen the {@code read} de lay nghia dong tu.
     *
     * @return true neu tim duoc, va {@code it.candidates} da duoc thay bang nghia cua lemma
     */
    private boolean promoteToVerbViaLemma(Item it) {
        for (String cand : Lemmatizer.candidates(it.source.toLowerCase(Locale.ROOT))) {
            var resolved = lookup.resolve(cand);
            if (resolved.isEmpty()) continue;
            List<Candidate> viaLemma = lookup.candidatesOf(resolved.get().entries());
            for (Candidate c : viaLemma) {
                if (c.pos() != null && c.pos().contains("động từ")) {
                    it.candidates = viaLemma;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasPos(Item it, String posName) {
        for (Candidate c : it.candidates) {
            if (c.pos() != null && c.pos().contains(posName)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ buoc 3: chon nghia

    private static void chooseVietnamese(List<Item> items) {
        for (Item it : items) {
            if (it.pos == Pos.PUNCT) continue;
            if (it.fw != null) {
                it.vi = it.fw.vi();
                continue;
            }
            Candidate chosen = pick(it);
            it.vi = chosen == null ? it.source : shorten(chosen.gloss());
        }
    }

    private static Candidate pick(Item it) {
        String wanted = switch (it.pos) {
            case NOUN -> "danh từ";
            case VERB -> "động từ";
            case ADJ -> "tính từ";
            case ADV -> "phó từ";
            default -> null;
        };
        if (wanted != null) {
            for (Candidate c : it.candidates) {
                if (c.pos() != null && c.pos().contains(wanted) && !isJunk(c.gloss())) return c;
            }
        }
        for (Candidate c : it.candidates) {
            if (!isJunk(c.gloss())) return c;
        }
        return it.candidates.isEmpty() ? null : it.candidates.getFirst();
    }

    /**
     * Nghia hong cua nguon: con sot markup ("&lt;vt&gt; vg rất tốt") hoac chi la chu thich
     * cach dung, khong phai nghia. Nhet vao cau dich thi thanh rac.
     */
    private static boolean isJunk(String gloss) {
        if (gloss == null || gloss.isBlank()) return true;
        if (gloss.indexOf('<') >= 0) return true;
        return shorten(gloss).isBlank();
    }

    /**
     * Mot dong nghia trong tu dien la ca mot chum dong nghia kem chu thich:
     * "giữ vững, giữ không cho đổ, giữ không cho hạ (máy...)". Trong cau dich chi lay
     * phuong an dau, bo chu thich trong ngoac - nguoi doc can MOT tu o dung cho do.
     */
    static String shorten(String gloss) {
        if (gloss == null) return "";
        String s = gloss.replaceAll("\\([^)]*\\)", " ").replaceAll("\\[[^]]*]", " ");
        int cut = s.length();
        for (String sep : new String[] {",", ";"}) {
            int at = s.indexOf(sep);
            if (at > 0) cut = Math.min(cut, at);
        }
        s = s.substring(0, cut).replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? gloss.replaceAll("\\s+", " ").trim() : s;
    }

    // ------------------------------------------------------------------ buoc 4: luat ngu phap

    private static void applyGrammarRules(List<Item> items) {
        mergeAdverbIntoAdjective(items);
        mergeNegation(items);
        fixNegatedDegree(items);
        handleAuxiliaries(items);
        markTense(items);
        reorderNounPhrases(items);
    }

    /**
     * Gop trang tu muc do vao tinh tu dung sau no thanh MOT don vi.
     *
     * <p>Neu khong gop, buoc sap lai danh ngu se day tinh tu ra sau danh tu con trang tu thi
     * o lai: "a very good book" -&gt; "rất sách tốt". Gop roi thi ra "sách rất tốt".
     * Tieng Viet giu nguyen thu tu trang tu + tinh tu nen chi viec noi chuoi.
     */
    private static void mergeAdverbIntoAdjective(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            Item adv = items.get(i);
            if (adv.dropped || !adv.isFunc(FunctionWords.Category.TRANG_TU)) continue;
            Item next = nextLive(items, i);
            if (next == null || next.pos != Pos.ADJ) continue;
            next.vi = adv.vi + " " + next.vi;
            adv.dropped = true;
        }
    }

    /** "could not" -&gt; "không thể", "did not" -&gt; "không", "has not" -&gt; "chưa". */
    private static void mergeNegation(List<Item> items) {
        for (int i = 1; i < items.size(); i++) {
            Item neg = items.get(i);
            if (!neg.isFunc(FunctionWords.Category.NEGATION)) continue;
            Item prev = previousLive(items, i);
            if (prev == null || prev.fw == null) continue;
            switch (prev.fw.cat()) {
                case MODAL -> {
                    // "có thể" + "không" khong ghep may moc thanh "không có thể"
                    prev.vi = prev.vi.equals("có thể") ? "không thể" : "không " + prev.vi;
                    neg.dropped = true;
                }
                case FUTURE -> { prev.vi = prev.vi + " không"; neg.dropped = true; }
                case BE, DO -> { prev.vi = "không"; prev.negated = true; neg.dropped = true; }
                case HAVE -> { prev.vi = "chưa"; prev.negated = true; neg.dropped = true; }
                default -> { }
            }
        }
    }

    /**
     * "not very difficult" -&gt; "không khó lắm", khong phai "không rất khó".
     * Tieng Viet day muc do ra SAU khi co phu dinh.
     */
    private static void fixNegatedDegree(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.dropped || !(it.negated || it.isFunc(FunctionWords.Category.NEGATION))) continue;
            Item next = nextLive(items, i);
            if (next == null || next.vi == null || !next.vi.startsWith("rất ")) continue;
            next.vi = next.vi.substring(4) + " lắm";
        }
    }

    /**
     * to be dung truoc tinh tu thi tieng Viet KHONG can he tu: "the system is old" ->
     * "hệ thống cũ", khong phai "hệ thống là cũ". Truoc dong tu -ing thi thanh "đang".
     */
    private static void handleAuxiliaries(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.dropped) continue;
            Item next = nextLive(items, i);
            if (next == null) continue;

            if (it.negated) continue;                       // "does not" -> "không", giu lai

            if (it.isFunc(FunctionWords.Category.BE)) {
                if (next.pos == Pos.VERB && next.gerund) it.vi = "đang";
                else if (next.pos == Pos.ADJ) it.dropped = true;
            } else if (it.isFunc(FunctionWords.Category.DO) && next.pos == Pos.VERB) {
                it.dropped = true;                              // tro dong tu rong
            } else if (it.isFunc(FunctionWords.Category.GIOI_TU)
                    && it.source.equalsIgnoreCase("to") && next.pos == Pos.VERB) {
                it.dropped = true;                              // to-infinitive, khong phai "đến"
            }
        }
    }

    /** Chen "đã" truoc dong tu qua khu, tru khi da co dau hieu thi o ngay truoc. */
    private static void markTense(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.dropped || it.pos != Pos.VERB || !it.past) continue;
            Item prev = previousLive(items, i);
            if (prev != null && (prev.isFunc(FunctionWords.Category.HAVE,
                    FunctionWords.Category.FUTURE, FunctionWords.Category.MODAL,
                    FunctionWords.Category.BE, FunctionWords.Category.NEGATION))) {
                continue;                                       // "had gone", "will go", "không thể"
            }
            it.vi = "đã " + it.vi;
        }
    }

    /**
     * Sap lai danh ngu cho dung trat tu tieng Viet.
     *
     * <pre>
     *   Anh:  [mạo từ] [lượng từ] [tính từ] DANH TỪ        the old system
     *   Việt: [lượng từ] DANH TỪ [tính từ] [chỉ định] [sở hữu]   hệ thống cũ này của tôi
     * </pre>
     *
     * Quet mot cum lien tuc gom mao tu / luong tu / so huu / chi dinh / tinh tu va ket thuc
     * bang danh tu, roi phat lai theo thu tu tieng Viet. Gioi tu, dong tu, dau cau deu cat
     * cum - nho vay "number of users" khong bi tron lam mot.
     */
    private static void reorderNounPhrases(List<Item> items) {
        int i = 0;
        while (i < items.size()) {
            int start = i;
            int lastNoun = -1;
            int j = i;
            while (j < items.size() && inNounPhrase(items.get(j))) {
                // Gap mao tu / luong tu / so huu SAU khi da co danh tu nghia la mot danh ngu
                // MOI bat dau: "Vietnamese every day" la hai cum, khong phai mot.
                if (lastNoun >= 0 && isDeterminer(items.get(j))) break;
                if (items.get(j).pos == Pos.NOUN) lastNoun = j;
                j++;
            }
            if (lastNoun < 0 || lastNoun == start) {
                i = Math.max(j, i + 1);
                continue;
            }
            List<Item> span = new ArrayList<>(items.subList(start, lastNoun + 1));
            // articles van phai nam trong danh sach phat lai du da bi bo: so o phai khop
            // dung voi so o cu, neu khong thi o cuoi con giu item cu va tu bi LAP LAI.
            List<Item> articles = new ArrayList<>();
            List<Item> quantifiers = new ArrayList<>();
            List<Item> nouns = new ArrayList<>();
            List<Item> adjectives = new ArrayList<>();
            List<Item> demonstratives = new ArrayList<>();
            List<Item> possessives = new ArrayList<>();
            for (Item it : span) {
                if (it.isFunc(FunctionWords.Category.ARTICLE)) { it.dropped = true; articles.add(it); }
                else if (it.isFunc(FunctionWords.Category.QUANTIFIER)) quantifiers.add(it);
                else if (it.isFunc(FunctionWords.Category.DEMONSTRATIVE)) demonstratives.add(it);
                else if (it.isFunc(FunctionWords.Category.POSSESSIVE)) possessives.add(it);
                else if (it.pos == Pos.ADJ) adjectives.add(it);
                else nouns.add(it);
            }
            List<Item> rebuilt = new ArrayList<>(span.size());
            rebuilt.addAll(articles);
            rebuilt.addAll(quantifiers);
            rebuilt.addAll(nouns);
            rebuilt.addAll(adjectives);
            rebuilt.addAll(demonstratives);
            rebuilt.addAll(possessives);
            if (rebuilt.size() != span.size()) {
                throw new IllegalStateException("sap lai danh ngu lam mat item: "
                        + span.size() + " -> " + rebuilt.size());
            }
            for (int k = 0; k < rebuilt.size(); k++) items.set(start + k, rebuilt.get(k));

            i = lastNoun + 1;
        }
    }

    private static boolean isDeterminer(Item it) {
        return it.isFunc(FunctionWords.Category.ARTICLE, FunctionWords.Category.QUANTIFIER,
                FunctionWords.Category.POSSESSIVE, FunctionWords.Category.DEMONSTRATIVE);
    }

    private static boolean inNounPhrase(Item it) {
        if (it.dropped) return false;
        if (it.pos == Pos.NOUN || it.pos == Pos.ADJ) return true;
        return it.isFunc(FunctionWords.Category.ARTICLE, FunctionWords.Category.QUANTIFIER,
                FunctionWords.Category.POSSESSIVE, FunctionWords.Category.DEMONSTRATIVE);
    }

    private static Item previousLive(List<Item> items, int from) {
        for (int i = from - 1; i >= 0; i--) {
            if (!items.get(i).dropped) return items.get(i);
        }
        return null;
    }

    private static Item nextLive(List<Item> items, int from) {
        for (int i = from + 1; i < items.size(); i++) {
            if (!items.get(i).dropped) return items.get(i);
        }
        return null;
    }

    // ------------------------------------------------------------------ buoc 5: noi cau

    private static String join(List<Item> items) {
        StringBuilder sb = new StringBuilder(96);
        for (Item it : items) {
            if (it.dropped) continue;
            String piece = it.vi == null ? "" : it.vi.trim();
            if (piece.isEmpty()) continue;
            if (it.pos == Pos.PUNCT) {
                sb.append(piece);                                // dau cau dinh sat tu truoc
            } else {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(piece);
            }
        }
        String out = sb.toString().replaceAll("\\s+", " ").trim();
        if (out.isEmpty()) return out;
        return Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }
}
