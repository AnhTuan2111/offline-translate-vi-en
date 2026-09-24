package com.anhtuan.dict.core.service;

import com.anhtuan.dict.core.model.Candidate;
import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.model.SegmentKind;
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

    public DictionaryGlossEngine(LookupService lookup, Set<String> phraseStarters) {
        this.lookup = lookup;
        this.phraseStarters = phraseStarters;
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
                int lastWord = wi + match.wordCount() - 1;
                Tokenizer.Token lastToken = tokens.get(wordToToken.get(lastWord));
                var phraseEntries = lookup.lookupAll(match.key());
                List<Candidate> candidates = phraseEntries.isEmpty()
                        ? List.of()
                        : lookup.phraseCandidatesOf(phraseEntries, match.key());
                out.add(new Segment(sentence.substring(t.start(), lastToken.end()),
                        t.start(), lastToken.end(), SegmentKind.PHRASE, candidates));
                ti = wordToToken.get(lastWord) + 1;
                wi = lastWord + 1;
                continue;
            }

            Optional<LookupService.Resolution> res = lookup.resolve(words.get(wi));
            if (res.isPresent()) {
                out.add(new Segment(t.text(), t.start(), t.end(), SegmentKind.WORD,
                        lookup.candidatesOf(res.get().entries())));
            } else {
                out.add(new Segment(t.text(), t.start(), t.end(), SegmentKind.UNKNOWN, List.of()));
            }
            ti++;
            wi++;
        }
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
