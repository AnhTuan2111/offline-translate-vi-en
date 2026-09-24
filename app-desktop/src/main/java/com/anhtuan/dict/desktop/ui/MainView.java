package com.anhtuan.dict.desktop.ui;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.nlp.TextNormalizer;
import com.anhtuan.dict.core.service.ReverseSearchService;
import com.anhtuan.dict.desktop.config.AppContext;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Locale;

/**
 * Cua so chinh. Ba che do dung chung mot o nhap:
 * <ul>
 *   <li>TRA TU  - Anh sang Viet, mot tu hoac cum (F1); truot thi goi y tu gan giong (F4)</li>
 *   <li>DICH CAU - chu giai theo cum (F2)</li>
 *   <li>VIET-ANH - tim tu tieng Anh tu nghia tieng Viet, xep theo do lien quan (F3)</li>
 * </ul>
 *
 * <p>Toan bo tra cuu chay tren UI thread va day la CO Y: do do duoc la 13,7 us moi luot
 * tra va 16 ms cho search - dua sang thread khac chi them phuc tap ma khong ai thay nhanh
 * hon. Neu sau nay cam NMT vao (moi cau vai tram ms) thi luc do moi can Task nen.
 */
public final class MainView {

    private enum Mode {
        WORD("Tra từ (Anh → Việt)"),
        SENTENCE("Dịch câu (Anh → Việt)"),
        REVERSE("Việt → Anh");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private final AppContext ctx;
    private final TextField input = new TextField();
    private final VBox resultHolder = new VBox();
    private final ScrollPane resultScroll = new ScrollPane(resultHolder);
    private final Label status = new Label();
    private final java.util.Map<Mode, ToggleButton> modeButtons = new java.util.EnumMap<>(Mode.class);
    private Mode mode = Mode.WORD;

    public MainView(AppContext ctx) {
        this.ctx = ctx;
    }

    public void show(Stage stage) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(12));
        root.getChildren().addAll(buildToolbar(), buildInput(), buildResultArea(), status);
        status.getStyleClass().add("status");

        Scene scene = new Scene(root, 900, 640);
        scene.getStylesheets().add(
                MainView.class.getResource("/css/dict.css").toExternalForm());

        stage.setTitle("Từ điển offline Anh - Việt");
        stage.setScene(scene);
        stage.show();
        input.requestFocus();

        showWelcome();
        applyStartupQuery();
    }

    /**
     * Cho phep mo san mot truy van: {@code -Ddict.query="give up" -Ddict.mode=word}.
     *
     * <p>Co ba cong dung that: chup anh man hinh de dan vao tai lieu ma khong phai go tay,
     * kiem thu tay nhanh, va sau nay la duong vao cho tinh nang tra nhanh tu tray (F6) -
     * luc do chi viec goi cung mot ham voi noi dung clipboard.
     */
    private void applyStartupQuery() {
        String query = System.getProperty("dict.query");
        if (query == null || query.isBlank()) return;
        String m = System.getProperty("dict.mode", "word").toLowerCase(Locale.ROOT);
        mode = switch (m) {
            case "sentence" -> Mode.SENTENCE;
            case "reverse" -> Mode.REVERSE;
            default -> Mode.WORD;
        };
        selectModeButton();
        input.setText(query);
        run();
    }

    private Node buildToolbar() {
        HBox bar = new HBox(6);
        ToggleGroup group = new ToggleGroup();
        for (Mode m : Mode.values()) {
            ToggleButton b = new ToggleButton(m.label);
            b.setToggleGroup(group);
            b.setSelected(m == mode);
            b.setOnAction(e -> {
                b.setSelected(true);                 // khong cho bo chon het
                mode = m;
                run();
            });
            modeButtons.put(m, b);
            bar.getChildren().add(b);
        }
        return bar;
    }

    /** Dong bo nut khi che do bi doi tu code (bam vao ket qua, hoac -Ddict.mode). */
    private void selectModeButton() {
        ToggleButton b = modeButtons.get(mode);
        if (b != null) b.setSelected(true);
    }

    private Node buildInput() {
        input.setPromptText("Nhập từ, cụm từ, cả câu tiếng Anh, hoặc nghĩa tiếng Việt rồi bấm Enter");
        input.getStyleClass().add("search-input");
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) run();
        });
        HBox.setHgrow(input, Priority.ALWAYS);
        return new HBox(input);
    }

    private Node buildResultArea() {
        resultScroll.setFitToWidth(true);            // bat buoc de Label wrapText hoat dong
        resultScroll.getStyleClass().add("result-scroll");
        VBox.setVgrow(resultScroll, Priority.ALWAYS);
        return resultScroll;
    }

    // ------------------------------------------------------------------ tra cuu

    private void run() {
        String query = input.getText() == null ? "" : input.getText().trim();
        if (query.isEmpty()) {
            showWelcome();
            return;
        }
        long t0 = System.nanoTime();
        Node content = switch (mode) {
            case WORD -> lookupWord(query);
            case SENTENCE -> translateSentence(query);
            case REVERSE -> searchVietnamese(query);
        };
        double ms = (System.nanoTime() - t0) / 1_000_000.0;

        resultHolder.getChildren().setAll(content);
        resultScroll.setVvalue(0);                   // ket qua moi thi phai xem tu dau
        status.setText(String.format(Locale.ROOT, "%s · %,d mục từ · %,d khoá · tra trong %.1f ms",
                mode.label, ctx.pack().entryCount(), ctx.pack().keyCount(), ms));
    }

    private Node lookupWord(String query) {
        List<Entry> entries = ctx.lookup().lookupAll(query);
        if (!entries.isEmpty()) {
            String norm = TextNormalizer.normalizeHeadword(query);
            return ResultRenderer.renderEntries(entries, norm.contains(" ") ? norm : null);
        }

        // Truot: thu lemma truoc (gave -> give), roi moi den doan tu go sai (F4).
        var resolved = ctx.lookup().resolve(query);
        if (resolved.isPresent()) {
            VBox box = new VBox(6);
            box.getChildren().add(ResultRenderer.message(
                    "Không có \"" + query + "\". Dạng nguyên thể: " + resolved.get().key()));
            box.getChildren().add(ResultRenderer.renderEntries(resolved.get().entries()));
            return box;
        }
        List<ReverseSearchService.Hit> near = ctx.search().fuzzyEnglish(query, 10);
        if (near.isEmpty()) {
            return ResultRenderer.message("Không có \"" + query + "\" và không tìm được từ nào gần giống.");
        }
        VBox box = new VBox(6);
        box.getChildren().add(ResultRenderer.message(
                "Không có \"" + query + "\". Có phải bạn muốn tìm:"));
        box.getChildren().add(ResultRenderer.renderHits(near, this::openWord));
        return box;
    }

    private Node translateSentence(String sentence) {
        List<Segment> segments = ctx.engine().translate(sentence);
        VBox box = new VBox(8);
        box.getChildren().add(ResultRenderer.message(
                "Đây là CHÚ GIẢI theo cụm, không phải bản dịch tự nhiên. "
                        + "Bấm vào ô có dấu ▾ để đổi nghĩa."));
        box.getChildren().add(ResultRenderer.renderGloss(segments));
        return box;
    }

    private Node searchVietnamese(String query) {
        List<ReverseSearchService.Hit> hits = ctx.search().searchVietnamese(query, 25);
        if (hits.isEmpty()) {
            return ResultRenderer.message("Không tìm thấy từ tiếng Anh nào cho \"" + query + "\".");
        }
        return ResultRenderer.renderHits(hits, this::openWord);
    }

    /** Bam vao mot ket qua -> mo han muc tu do o che do tra tu. */
    private void openWord(String headword) {
        mode = Mode.WORD;
        selectModeButton();
        input.setText(headword);
        run();
    }

    private void showWelcome() {
        VBox box = new VBox(6);
        box.getChildren().add(ResultRenderer.message("""
                Từ điển offline Anh - Việt. Không cần mạng, không có tài khoản.

                Thử:
                  · Tra từ     :  about,  give up,  acid-proof
                  · Dịch câu   :  He gave up his job because the system could not keep up
                  · Việt → Anh :  chăm sóc   (gõ không dấu "cham soc" cũng ra cùng kết quả)
                """));
        resultHolder.getChildren().setAll(box);
        status.setText(String.format(Locale.ROOT,
                "%,d mục từ · %,d khoá tra cứu · nạp dữ liệu trong %d ms · dữ liệu: %s",
                ctx.pack().entryCount(), ctx.pack().keyCount(), ctx.startupMillis(),
                ctx.dataDir().toAbsolutePath()));
    }
}
