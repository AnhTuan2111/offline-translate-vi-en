package com.anhtuan.dict.core.service;

import com.anhtuan.dict.core.lexicon.LexicalPrior;
import com.anhtuan.dict.core.model.Candidate;
import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.model.SegmentKind;
import com.anhtuan.dict.core.nlp.FunctionWords;
import com.anhtuan.dict.core.nlp.PhraseProbe;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.nlp.Tokenizer;
import com.anhtuan.dict.core.spi.TranslationEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Engine dich cau cua v1: chu giai theo cum (PLAN.md 8.1).
 *
 * <p>LUU Y VE KY VONG - day la CHU GIAI, khong phai ban dich tu nhien:
 * <pre>
 *   "He gave up his job."
 *   -> He[anh ay] gave up[tu bo] his[cua anh ay] job[cong viec]
 * </pre>
 * Muon cau tieng Viet tu nhien thi can NMT; cong {@link TranslationEngine} de ngo
 * san cho viec do (PLAN.md 13) va UI khong phai sua mot dong nao khi cam vao.
 *
 * <p>Thu tu xu ly moi vi tri trong cau:
 * <ol>
 *   <li>khop cum dai nhat (4 -&gt; 3 -&gt; 2 tu), ke ca qua khoa bi danh va lemma</li>
 *   <li>khong co cum thi tra tu don theo bac thang cua {@link LookupService#resolve}</li>
 *   <li>van truot thi tra ve {@link SegmentKind#UNKNOWN}, giu nguyen tu goc</li>
 * </ol>
 */
public final class DictionaryGlossEngine implements TranslationEngine {

    public static final String ENGINE_ID = "dictionary-gloss";

    private final LookupService lookup;
    private final Set<String> phraseStarters;
    private final LexicalPrior prior;

    public DictionaryGlossEngine(LookupService lookup, Set<String> phraseStarters) {
        this(lookup, phraseStarters, LexicalPrior.empty());
    }

    /**
     * @param prior dung de XEP LAI thu tu cac nghia hien cho nguoi dung. Neu khong, o chu giai
     *              hien "sự cai trị" trong khi cau dich ben tren lai dung "chính phủ" -
     *              nguoi doc se tuong app mau thuan voi chinh no.
     */
    public DictionaryGlossEngine(LookupService lookup, Set<String> phraseStarters,
                                 LexicalPrior prior) {
        this.lookup = lookup;
        this.phraseStarters = phraseStarters;
        this.prior = prior;
    }

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public String displayName() {
        return "Chú giải theo cụm (từ điển)";
    }

    @Override
    public List<Segment> translate(String sentence) {
        if (sentence == null || sentence.isEmpty()) return List.of();

        List<Tokenizer.Token> tokens = Tokenizer.tokenize(sentence);

        // Chi cac token la TU, kem chi so token goc de nhay offset khi khop cum.
        List<String> words = new ArrayList<>(tokens.size());
        List<Integer> wordToToken = new ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).isWord()) {
                words.add(TextNormalizer.normalizeHeadword(tokens.get(i).text()));
                wordToToken.add(i);
            }
        }

        List<Segment> out = new ArrayList<>(tokens.size());
        int ti = 0;
        int wi = 0;
        while (ti < tokens.size()) {
            Tokenizer.Token t = tokens.get(ti);
            if (!t.isWord()) {
                out.add(new Segment(t.text(), t.start(), t.end(), SegmentKind.PUNCT, List.of()));
                ti++;
                continue;
            }

            PhraseProbe.Match match =
                    PhraseProbe.longestMatch(words, wi, phraseStarters, lookup::contains);
            if (match != null) {
                var phraseEntries = lookup.lookupAll(match.key());
                List<Candidate> candidates = phraseEntries.isEmpty()
                        ? List.of()
                        : lookup.phraseCandidatesOf(phraseEntries, match.key());
                // Cum chi co nghia rac thi coi nhu KHONG khop: tra tung tu con hon dua ra
                // mot chuoi vo nghia ("very good" -> "<vt> vg rất tốt" trong nguon).
                if (candidates.stream().allMatch(c -> LookupService.isJunkGloss(c.gloss()))) {
                    match = null;
                }
                if (match != null) {
                    int lastWord = wi + match.wordCount() - 1;
                    Tokenizer.Token lastToken = tokens.get(wordToToken.get(lastWord));
                    out.add(new Segment(sentence.substring(t.start(), lastToken.end()),
                            t.start(), lastToken.end(), SegmentKind.PHRASE, candidates));
                    ti = wordToToken.get(lastWord) + 1;
                    wi = lastWord + 1;
                    continue;
                }
            }

            Optional<LookupService.Resolution> res = lookup.resolve(words.get(wi));
            if (res.isPresent()) {
                List<Candidate> candidates = byLikelihood(t.text(),
                        lookup.candidatesOf(res.get().entries()));
                out.add(new Segment(t.text(), t.start(), t.end(), SegmentKind.WORD,
                        withFunctionWord(t.text(), candidates)));
            } else {
                out.add(new Segment(t.text(), t.start(), t.end(), SegmentKind.UNKNOWN, List.of()));
            }
            ti++;
            wi++;
        }
        return out;
    }

    /**
     * Xep lai cac nghia theo do hay dung THAT SU, lay tu bang xac suat (PLAN.md 7.4).
     * Nghia nao khong co trong bang thi giu nguyen thu tu tu dien va nam sau.
     */
    private List<Candidate> byLikelihood(String source, List<Candidate> candidates) {
        if (!prior.isAvailable() || candidates.size() < 2) return candidates;
        String en = source.toLowerCase(java.util.Locale.ROOT);

        record Scored(Candidate candidate, double score, int order) {}
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            double best = 0;
            for (String alt : RuleBasedTranslationEngine.alternatives(c.gloss())) {
                double s = prior.scoreGloss(en, TextNormalizer.splitTokens(alt));
                if (s == 0 && c.headword() != null) {
                    s = prior.scoreGloss(c.headword(), TextNormalizer.splitTokens(alt));
                }
                best = Math.max(best, s);
            }
            scored.add(new Scored(c, best, i));
        }
        scored.sort((a, b) -> {
            int cmp = Double.compare(b.score(), a.score());
            return cmp != 0 ? cmp : Integer.compare(a.order(), b.order());
        });
        List<Candidate> out = new ArrayList<>(candidates.size());
        for (Scored sc : scored) out.add(sc.candidate());
        return out;
    }

    /**
     * Voi TU CHUC NANG, dua nghia da dich cung len lam nghia dau.
     *
     * <p>Tu dien dich {@code he} thanh "đàn ông; con đực" (nghia duoc viet ky nhat cua muc tu
     * do) va {@code the} thanh "cái, con, người...". Deu dung theo nghia tu dien va deu vo
     * dung trong mot cau. Cac nghia tu dien van con nguyen o phia sau de nguoi dung xem.
     */
    private static List<Candidate> withFunctionWord(String source, List<Candidate> dictionary) {
        FunctionWords.Fw fw = FunctionWords.get(source);
        if (fw == null) return dictionary;
        String vi = fw.vi().isEmpty()
                ? "(" + source.toLowerCase(java.util.Locale.ROOT) + " — tiếng Việt không cần dịch)"
                : fw.vi();
        List<Candidate> out = new ArrayList<>(dictionary.size() + 1);
        out.add(new Candidate(source, vi, "từ chức năng", 2.0));
        out.addAll(dictionary);
        return out;
    }

    /**
     * Noi cac nghia mac dinh lai thanh mot dong text - tien cho log, test va che do
     * "sao chep ket qua" cua UI.
     */
    public static String flatten(List<Segment> segments) {
        StringBuilder sb = new StringBuilder(64);
        for (Segment s : segments) {
            if (s.kind() == SegmentKind.PUNCT) {
                sb.append(s.sourceText());
            } else {
                String gloss = s.displayGloss();
                sb.append(gloss == null ? s.sourceText() : gloss);
            }
        }
        return sb.toString();
    }
}
