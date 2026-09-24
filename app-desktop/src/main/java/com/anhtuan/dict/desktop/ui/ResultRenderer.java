package com.anhtuan.dict.desktop.ui;

import com.anhtuan.dict.core.model.Candidate;
import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Example;
import com.anhtuan.dict.core.model.Idiom;
import com.anhtuan.dict.core.model.Segment;
import com.anhtuan.dict.core.model.SegmentKind;
import com.anhtuan.dict.core.model.Sense;
import com.anhtuan.dict.core.service.ReverseSearchService;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;
import java.util.function.Consumer;

/**
 * Bien ket qua tu {@code dict-core} thanh Node cua JavaFX.
 *
 * <p>TUYET DOI khong dung WebView (PLAN.md AD-7): {@code javafx.web} keo theo ca WebKit,
 * +35 MB dia va +40 MB RAM, chi de hien text co dinh dang. Kiem tra bang
 * {@code mvn -pl app-desktop dependency:tree | grep javafx-web} - phai rong.
 *
 * <p>Hai cach ve, dung cho hai viec khac nhau:
 * <ul>
 *   <li>{@link TextFlow} cho muc tu: van ban chay lien tuc, nhieu kieu chu trong mot dong</li>
 *   <li>{@link FlowPane} cac "chip" cho ket qua dich cau: moi cum la mot o rieng bam duoc
 *       de doi nghia, va FlowPane tu xuong dong khi het chieu ngang</li>
 * </ul>
 */
final class ResultRenderer {

    private ResultRenderer() {}

    // ------------------------------------------------------------------ tra tu

    static Node renderEntries(List<Entry> entries) {
        return renderEntries(entries, null);
    }

    /**
     * Ve muc tu, co the kem mot CUM duoc lam noi len dau.
     *
     * <p>Ly do: "give up" khong phai muc tu rieng, no la dong "!to give up" nam giua muc tu
     * "give" dai 15 nghia. Tra "give up" ma do thang toan bo muc "give" ra thi nguoi dung
     * phai tu di tim - dung y ho hoi thi lai khong tra loi. Nen cum khop duoc dua len dau,
     * muc tu cha van giu nguyen ben duoi.
     */
    static Node renderEntries(List<Entry> entries, String focusPhrase) {
        VBox outer = new VBox(6);
        List<Idiom> focus = focusPhrase == null ? List.of() : findIdioms(entries, focusPhrase);
        if (!focus.isEmpty()) {
            VBox card = new VBox(2);
            card.getStyleClass().add("phrase-card");
            for (Idiom idiom : focus) card.getChildren().add(renderIdiom(idiom));
            outer.getChildren().add(card);
            outer.getChildren().add(styled("Nằm trong mục từ:", "message"));
        }
        outer.getChildren().add(renderEntryList(entries));
        return outer;
    }

    private static List<Idiom> findIdioms(List<Entry> entries, String normPhrase) {
        List<Idiom> out = new java.util.ArrayList<>(2);
        for (Entry e : entries) {
            for (Idiom i : e.idioms()) {
                String norm = com.anhtuan.dict.core.nlp.TextNormalizer.normalizeHeadword(i.phrase());
                if (norm.equals(normPhrase) || norm.equals("to " + normPhrase)) out.add(i);
            }
        }
        return out;
    }

    private static Node renderEntryList(List<Entry> entries) {
        VBox box = new VBox(2);
        box.getStyleClass().add("result-box");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (i > 0) {
                Label hr = new Label("(muc tu dong am " + (i + 1) + ")");
                hr.getStyleClass().add("homograph-note");
                box.getChildren().add(hr);
            }
            box.getChildren().add(headline(e));
            for (Sense s : e.senses()) box.getChildren().add(renderSense(s));
            if (!e.idioms().isEmpty()) {
                Label title = new Label("Thành ngữ / cụm từ");
                title.getStyleClass().add("section-title");
                box.getChildren().add(title);
                for (Idiom idiom : e.idioms()) box.getChildren().add(renderIdiom(idiom));
            }
            if (!e.crossRefs().isEmpty()) {
                for (String ref : e.crossRefs()) box.getChildren().add(styled("→ " + ref, "cross-ref"));
            }
        }
        return box;
    }

    private static Node headline(Entry e) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("headline");
        Text head = new Text(e.headword());
        head.getStyleClass().add("headword");
        flow.getChildren().add(head);
        if (e.ipa() != null) {
            Text ipa = new Text("  /" + e.ipa() + "/");
            ipa.getStyleClass().add("ipa");
            flow.getChildren().add(ipa);
        }
        if (e.variant() != null) {
            Text v = new Text("   (còn viết: " + e.variant() + ")");
            v.getStyleClass().add("variant");
            flow.getChildren().add(v);
        }
        return flow;
    }

    private static Node renderSense(Sense s) {
        VBox box = new VBox(1);
        box.getStyleClass().add("sense");
        if (s.pos() != null) box.getChildren().add(styled(s.pos(), "pos"));
        List<String> glosses = s.glosses();
        for (int i = 0; i < glosses.size(); i++) {
            box.getChildren().add(styled((i + 1) + ". " + glosses.get(i), "gloss"));
        }
        for (Example ex : s.examples()) box.getChildren().add(renderExample(ex));
        return box;
    }

    private static Node renderIdiom(Idiom idiom) {
        VBox box = new VBox(1);
        box.getStyleClass().add("idiom");
        box.getChildren().add(styled(idiom.phrase(), "idiom-phrase"));
        for (String g : idiom.glosses()) box.getChildren().add(styled("• " + g, "gloss"));
        for (Example ex : idiom.examples()) box.getChildren().add(renderExample(ex));
        return box;
    }

    private static Node renderExample(Example ex) {
        String text = ex.hasTranslation()
                ? ex.en() + "   —   " + ex.vi()
                : ex.en();
        return styled(text, "example");
    }

    // ------------------------------------------------------------------ dich cau

    /**
     * Ve ket qua dich cau. Moi segment la mot chip: tu goc o tren, nghia o duoi.
     * Chip nao co nhieu nghia thi bam vao se ra menu doi nghia - day la cach ung xu
     * voi han che that cua chu giai theo cum: may khong biet chon nghia nao, nen de
     * nguoi doc chon.
     */
    static Node renderGloss(List<Segment> segments) {
        FlowPane pane = new FlowPane(6, 8);
        pane.getStyleClass().add("gloss-pane");
        for (Segment s : segments) {
            if (s.kind() == SegmentKind.PUNCT) {
                if (s.sourceText().isBlank()) continue;
                pane.getChildren().add(styled(s.sourceText(), "chip-punct"));
                continue;
            }
            pane.getChildren().add(chip(s));
        }
        return pane;
    }

    private static Node chip(Segment s) {
        VBox chip = new VBox(1);
        chip.getStyleClass().addAll("chip", switch (s.kind()) {
            case PHRASE -> "chip-phrase";
            case UNKNOWN -> "chip-unknown";
            default -> "chip-word";
        });

        Label source = new Label(s.sourceText());
        source.getStyleClass().add("chip-source");

        String gloss = s.displayGloss();
        Label meaning = new Label(gloss == null ? "(không có trong từ điển)" : gloss);
        meaning.getStyleClass().add("chip-gloss");
        meaning.setWrapText(false);

        chip.getChildren().addAll(source, meaning);

        if (s.candidates().size() > 1) {
            Label more = new Label(s.candidates().size() - 1 + " nghĩa khác ▾");
            more.getStyleClass().add("chip-more");
            chip.getChildren().add(more);

            ContextMenu menu = new ContextMenu();
            for (Candidate c : s.candidates()) {
                String label = c.pos() == null ? c.gloss() : c.gloss() + "   [" + c.pos() + "]";
                MenuItem item = new MenuItem(label);
                item.setOnAction(ev -> meaning.setText(c.gloss()));
                menu.getItems().add(item);
            }
            chip.setOnMouseClicked(ev -> {
                if (ev.getButton() == MouseButton.PRIMARY) menu.show(chip, Side.BOTTOM, 0, 0);
            });
        }
        return chip;
    }

    // ------------------------------------------------------------------ Viet -> Anh

    static Node renderHits(List<ReverseSearchService.Hit> hits, Consumer<String> onOpen) {
        VBox box = new VBox(4);
        box.getStyleClass().add("result-box");
        int i = 1;
        for (ReverseSearchService.Hit h : hits) {
            VBox row = new VBox(1);
            row.getStyleClass().add("hit");

            TextFlow flow = new TextFlow();
            Text rank = new Text(i++ + ". ");
            rank.getStyleClass().add("hit-rank");
            Text word = new Text(h.display());
            word.getStyleClass().add("hit-word");
            flow.getChildren().addAll(rank, word);
            if (h.entry().ipa() != null) {
                Text ipa = new Text("  /" + h.entry().ipa() + "/");
                ipa.getStyleClass().add("ipa");
                flow.getChildren().add(ipa);
            }
            row.getChildren().add(flow);
            if (h.matchedGloss() != null) row.getChildren().add(styled(h.matchedGloss(), "hit-gloss"));

            String headword = h.entry().headword();
            row.setOnMouseClicked(ev -> onOpen.accept(headword));
            box.getChildren().add(row);
        }
        return box;
    }

    // ------------------------------------------------------------------ chung

    /** Cau da dich, hien to va noi bat - day la thu nguoi dung tim den o che do dich cau. */
    static Node translation(String text) {
        Label l = new Label(text == null || text.isBlank() ? "(không dịch được)" : text);
        l.getStyleClass().add("translation");
        l.setWrapText(true);
        VBox box = new VBox(l);
        box.getStyleClass().add("translation-box");
        return box;
    }

    /** Dong "Y ban la ...?" - bam vao la tra luon tu duoc goi y. */
    static Node suggestion(List<String> words, Consumer<String> onPick) {
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(8);
        row.getStyleClass().add("suggestion-row");
        row.getChildren().add(styled("Ý bạn là:", "message"));
        for (String w : words) {
            Label link = new Label(w);
            link.getStyleClass().add("suggestion-link");
            link.setOnMouseClicked(e -> onPick.accept(w));
            row.getChildren().add(link);
        }
        return row;
    }

    static Node message(String text) {
        Label l = styled(text, "message");
        return l;
    }

    private static Label styled(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        l.setWrapText(true);
        return l;
    }
}
