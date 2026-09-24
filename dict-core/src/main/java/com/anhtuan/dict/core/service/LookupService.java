package com.anhtuan.dict.core.service;

import com.anhtuan.dict.core.model.Candidate;
import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Idiom;
import com.anhtuan.dict.core.model.Sense;
import com.anhtuan.dict.core.nlp.Lemmatizer;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.pack.PackReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tra tu Anh-&gt;Viet (PLAN.md F1) va BAC THANG FALLBACK cua PLAN.md 8.1 buoc 3.
 *
 * <pre>
 *   a. tra thang headwordNorm
 *   b. truot -> thu cac ung vien lemma (went->go, running->run, studies->study)
 *   c. van truot -> khong co ket qua, nguoi goi tu quyet dinh coi la UNKNOWN
 * </pre>
 *
 * <p>Tang b va c cua ban dac ta goc duoc gop lam mot: {@link Lemmatizer#candidates}
 * sinh ca ung vien bat quy tac lan ung vien bo hau to, va o day tung ung vien duoc
 * DOI CHIEU VOI TU DIEN nen khong can file lemma rieng.
 */
public final class LookupService {

    /** So nghia toi da dua vao danh sach candidate cua mot segment. */
    private static final int MAX_CANDIDATES = 12;

    private final PackReader pack;

    public LookupService(PackReader pack) {
        this.pack = pack;
    }

    /**
     * @param key     khoa thuc su tim thay trong tu dien
     * @param entries cac entry dong am ung voi khoa do
     * @param viaLemma true neu phai lemma hoa moi tim ra (UI hien "&lt;- gave")
     */
    public record Resolution(String key, List<Entry> entries, boolean viaLemma) {
        public Entry first() {
            return entries.getFirst();
        }
    }

    /** Tra cuu theo dung bac thang. Empty nghia la tu dien khong co tu nay. */
    public Optional<Resolution> resolve(String word) {
        String norm = TextNormalizer.normalizeHeadword(word);
        if (norm.isEmpty()) return Optional.empty();

        List<Entry> direct = pack.lookupAll(norm);
        if (hasGloss(direct)) return Optional.of(new Resolution(norm, direct, false));

        // Entry chi co dong '+' tham chieu cheo van tinh la TRUOT. Nguon co rat nhieu muc
        // kieu "@went  + thoi qua khu cua go": tim thay ma khong co nghia nao de hien thi.
        // Phai lemma hoa tiep de ra "@go", neu khong thi UI in ra o trong.
        for (String cand : Lemmatizer.candidates(norm)) {
            List<Entry> hit = pack.lookupAll(cand);
            if (hasGloss(hit)) return Optional.of(new Resolution(cand, hit, true));
        }
        // Khong con duong nao khac: tra ve entry rong con hon khong tra gi (con crossRefs de hien).
        return direct.isEmpty() ? Optional.empty() : Optional.of(new Resolution(norm, direct, false));
    }

    private static boolean hasGloss(List<Entry> entries) {
        for (Entry e : entries) {
            for (Sense s : e.senses()) if (!s.glosses().isEmpty()) return true;
            for (Idiom i : e.idioms()) if (!i.glosses().isEmpty()) return true;
        }
        return false;
    }

    /** Toan bo entry dong am cua mot tu, khong lemma hoa. Dung cho o tra tu cua UI. */
    public List<Entry> lookupAll(String word) {
        return pack.lookupAll(word);
    }

    public boolean contains(String normalizedKey) {
        return pack.contains(normalizedKey);
    }

    public List<String> suggest(String prefix, int limit) {
        return pack.prefixScan(prefix, limit);
    }

    public PackReader pack() {
        return pack;
    }

    // ------------------------------------------------------------------ candidate

    /**
     * Cac nghia de hien thi cho mot tu don: nghia dau tien cua tung sense truoc,
     * roi moi den cac nghia con lai. Nho vay bam "doi nghia" o UI se nhay giua cac
     * TU LOAI khac nhau truoc (danh tu / dong tu), thay vi loanh quanh trong cung mot sense.
     */
    public List<Candidate> candidatesOf(List<Entry> entries) {
        List<Candidate> out = new ArrayList<>(MAX_CANDIDATES);
        double score = 1.0;
        for (Entry e : entries) {
            for (Sense s : e.senses()) {
                String g = s.primaryGloss();
                if (g != null) out.add(new Candidate(e.headword(), g, s.pos(), score));
                score *= 0.9;
            }
        }
        for (Entry e : entries) {
            for (Sense s : e.senses()) {
                List<String> glosses = s.glosses();
                for (int i = 1; i < glosses.size() && out.size() < MAX_CANDIDATES; i++) {
                    out.add(new Candidate(e.headword(), glosses.get(i), s.pos(), score));
                    score *= 0.9;
                }
            }
        }
        return out.size() <= MAX_CANDIDATES ? out : out.subList(0, MAX_CANDIDATES);
    }

    /**
     * Cac nghia cho mot CUM da khop. Khac ham tren o cho quan trong nhat cua M4:
     * cum "give up" khop qua khoa bi danh nen entry tra ve la "@give" - neu lay nghia
     * dau tien cua entry do thi ra "cho, tang" thay vi "tu bo". Phai tim dung dong
     * "!to give up" ben trong entry.
     */
    public List<Candidate> phraseCandidatesOf(List<Entry> entries, String phraseKey) {
        List<Candidate> out = new ArrayList<>(MAX_CANDIDATES);
        double score = 1.0;
        for (Entry e : entries) {
            for (Idiom idiom : e.idioms()) {
                if (!matchesPhrase(idiom.phrase(), phraseKey)) continue;
                for (String g : idiom.glosses()) {
                    if (out.size() >= MAX_CANDIDATES) break;
                    out.add(new Candidate(idiom.phrase(), g, null, score));
                    score *= 0.95;
                }
            }
        }
        if (!out.isEmpty()) return out;
        // Cum la headword thuc (vi du "a la carte") -> dung nghia cua sense nhu tu don.
        return candidatesOf(entries);
    }

    /** "to give up" khop voi khoa "give up", va tat nhien voi ca "to give up". */
    private static boolean matchesPhrase(String idiomPhrase, String phraseKey) {
        String norm = TextNormalizer.normalizeHeadword(idiomPhrase);
        return norm.equals(phraseKey) || norm.equals("to " + phraseKey);
    }
}
