package com.anhtuan.dict.desktop.config;

import com.anhtuan.dict.core.index.IndexFormat;
import com.anhtuan.dict.core.index.InvertedIndex;
import com.anhtuan.dict.core.lexicon.LexicalPrior;
import com.anhtuan.dict.core.lexicon.LexiconFormat;
import com.anhtuan.dict.core.pack.PackReader;
import com.anhtuan.dict.core.service.DictionaryGlossEngine;
import com.anhtuan.dict.core.service.LookupService;
import com.anhtuan.dict.core.service.ReverseSearchService;
import com.anhtuan.dict.core.service.RuleBasedTranslationEngine;
import com.anhtuan.dict.core.spi.TranslationEngine;

import java.nio.file.Path;
import java.util.Set;

/**
 * COMPOSITION ROOT - toan bo viec "lap rap" cua ung dung nam trong mot cho duy nhat.
 *
 * <h2>Vi sao khong dung Spring Boot (sua lai AD-8 cua PLAN.md)</h2>
 * Ke hoach ban dau dinh dung Spring Boot non-web lam DI. Do that thi gia phai tra:
 * <ul>
 *   <li>~10 MB jar (spring-core / context / beans / aop / expression, boot,
 *       autoconfigure, logback, snakeyaml) cho dung mot viec la goi {@code new} 6 lan;</li>
 *   <li>them 0,5-1,5 giay khoi dong cho classpath scanning va context refresh - ma voi
 *       app tra tu goi tu tray (F6) thi do tre khoi dong chinh la thu nguoi dung cam nhan;</li>
 *   <li>DI bang reflection lam kho jlink/jpackage - dung cai rui ro "ket o M8" ma
 *       PLAN.md muc 12 da ghi.</li>
 * </ul>
 * Doi lai duoc gi: voi 6 bean va khong he co transaction, web layer hay profile,
 * cau tra loi trung thuc la khong duoc gi. Class nay la toan bo phan Spring se lam,
 * dai 30 dong, doc mot luot la thay het do thi phu thuoc.
 *
 * <p>Muon quay lai Spring thi doi dung cho nay: bo {@code @Configuration} len class,
 * bien cac field thanh {@code @Bean}, va bootstrap bang
 * {@code new SpringApplicationBuilder(AppContext.class).web(NONE).run()}. Khong cho nao
 * khac trong app biet den su ton tai cua class nay ngoai {@code DictApp}.
 */
public final class AppContext implements AutoCloseable {

    private final Path dataDir;
    private final PackReader pack;
    private final InvertedIndex viIndex;
    private final InvertedIndex viNoDiacIndex;
    private final InvertedIndex trigramIndex;
    private final LexicalPrior lexicalPrior;

    private final LookupService lookupService;
    private final ReverseSearchService reverseSearchService;
    private final DictionaryGlossEngine glossEngine;
    private final RuleBasedTranslationEngine sentenceEngine;
    private final Set<String> phraseStarters;

    private final long startupMillis;

    public AppContext() {
        long t0 = System.nanoTime();
        this.dataDir = DataLocator.locate();
        this.pack = PackReader.open(dataDir.resolve(DataLocator.PACK_FILE));
        this.viIndex = InvertedIndex.open(dataDir.resolve(IndexFormat.VI_INDEX));
        this.viNoDiacIndex = InvertedIndex.open(dataDir.resolve(IndexFormat.VI_NODIAC_INDEX));
        this.trigramIndex = InvertedIndex.open(dataDir.resolve(IndexFormat.TRIGRAM_INDEX));

        this.lookupService = new LookupService(pack);
        this.reverseSearchService =
                new ReverseSearchService(pack, viIndex, viNoDiacIndex, trigramIndex);
        // Quet vung KEYS mot lan de lay 5.927 tu mo dau cum - re hon giu mot file rieng.
        this.phraseStarters = pack.multiWordStarters();
        // Thieu lex.bin thi app van chay, chi chon nghia kem hon - khong bat nguoi dung
        // phai sinh lai du lieu.
        this.lexicalPrior = LexicalPrior.openIfPresent(dataDir.resolve(LexiconFormat.FILE_NAME));
        this.glossEngine = new DictionaryGlossEngine(lookupService, phraseStarters, lexicalPrior);
        this.sentenceEngine =
                new RuleBasedTranslationEngine(glossEngine, lookupService, lexicalPrior);

        this.startupMillis = (System.nanoTime() - t0) / 1_000_000;
    }

    public Path dataDir() {
        return dataDir;
    }

    public PackReader pack() {
        return pack;
    }

    public LookupService lookup() {
        return lookupService;
    }

    public ReverseSearchService search() {
        return reverseSearchService;
    }

    /** Engine chu giai theo cum - dung cho phan chi tiet tung tu. */
    public TranslationEngine engine() {
        return glossEngine;
    }

    /**
     * Engine dich ca cau. Khai bao kieu cu the (khong phai {@link TranslationEngine}) vi UI
     * can goi ca {@code glossSegments}. Khi cam NMT vao sau nay, cho nay doi thanh mot
     * danh sach engine cho nguoi dung chon.
     */
    public RuleBasedTranslationEngine sentenceEngine() {
        return sentenceEngine;
    }

    /** Thoi gian nap du lieu, hien o thanh trang thai lam bang chung do duoc. */
    public long startupMillis() {
        return startupMillis;
    }

    public LexicalPrior lexicalPrior() {
        return lexicalPrior;
    }

    @Override
    public void close() {
        // Dong theo thu tu nguoc lai luc mo. Moi cai unmap ngay nho Arena.
        lexicalPrior.close();
        trigramIndex.close();
        viNoDiacIndex.close();
        viIndex.close();
        pack.close();
    }
}
