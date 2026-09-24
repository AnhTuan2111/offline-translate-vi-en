package com.anhtuan.dict.desktop.tray;

import com.anhtuan.dict.core.model.Entry;
import com.anhtuan.dict.core.model.Idiom;
import com.anhtuan.dict.core.model.Sense;
import com.anhtuan.dict.desktop.config.AppContext;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.MouseInfo;
import java.awt.Point;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cua so nho hien nghia ngay tai cho, khi nguoi dung boi den chu o ung dung khac roi Ctrl+C
 * (PLAN.md F6).
 *
 * <p>Dung {@link StageStyle#UNDECORATED} va luon noi len tren: no phai xuat hien va bien mat
 * nhanh, khong chiem cho tren thanh tac vu, khong cuop tieu diem cua ung dung nguoi dung
 * dang lam viec. Bam Esc hoac bam ra ngoai la dong.
 *
 * <p>Noi dung co y ngan - phien am, tu loai, toi da ba nghia. Muon xem day du thi bam vao
 * dong cuoi de mo cua so chinh.
 */
public final class QuickLookPopup {

    private static final int MAX_GLOSSES = 3;

    /** Bao lau thi tu an neu nguoi dung khong dong y gi. */
    private static final int VISIBLE_SECONDS = 10;

    private final AppContext ctx;
    private final Consumer<String> onOpenFull;
    private Stage stage;
    private VBox content;
    private PauseTransition autoHide;

    public QuickLookPopup(AppContext ctx, Consumer<String> onOpenFull) {
        this.ctx = ctx;
        this.onOpenFull = onOpenFull;
    }

    /** Goi tren JavaFX Application Thread. */
    public void show(String text) {
        List<Entry> entries = ctx.lookup().lookupAll(text);
        String lemmaNote = null;
        if (entries.isEmpty()) {
            var resolved = ctx.lookup().resolve(text);
            if (resolved.isPresent()) {
                entries = resolved.get().entries();
                lemmaNote = "dạng nguyên thể: " + resolved.get().key();
            }
        }
        if (entries.isEmpty()) return;                 // khong co gi de hien thi thi im lang

        ensureStage();
        content.getChildren().clear();
        content.getChildren().add(styled(entries.getFirst().headword()
                + (entries.getFirst().ipa() == null ? "" : "  /" + entries.getFirst().ipa() + "/"),
                "popup-headword"));
        if (lemmaNote != null) content.getChildren().add(styled(lemmaNote, "popup-note"));

        int shown = 0;
        for (Entry e : entries) {
            for (Sense s : e.senses()) {
                for (String g : s.glosses()) {
                    if (shown >= MAX_GLOSSES) break;
                    content.getChildren().add(styled(
                            (s.pos() == null ? "" : "(" + s.pos() + ") ") + g, "popup-gloss"));
                    shown++;
                }
            }
            for (Idiom i : e.idioms()) {
                if (shown >= MAX_GLOSSES || i.glosses().isEmpty()) break;
                content.getChildren().add(styled(i.phrase() + " — " + i.glosses().getFirst(),
                        "popup-gloss"));
                shown++;
            }
        }
        content.getChildren().add(styled("Bấm để mở cửa sổ đầy đủ · Esc để đóng", "popup-note"));

        Point mouse = MouseInfo.getPointerInfo().getLocation();
        stage.setX(mouse.x + 12);
        stage.setY(mouse.y + 12);
        stage.show();
        stage.toFront();
        autoHide.playFromStart();
    }

    public void hide() {
        if (autoHide != null) autoHide.stop();
        if (stage != null) stage.hide();
    }

    private void ensureStage() {
        if (stage != null) return;
        content = new VBox(4);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("popup-box");

        Scene scene = new Scene(content, 420, 190);
        scene.getStylesheets().add(
                QuickLookPopup.class.getResource("/css/dict.css").toExternalForm());
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) hide(); });
        content.setOnMouseClicked(e -> {
            String headword = content.getChildren().isEmpty() ? "" :
                    ((Label) content.getChildren().getFirst()).getText().split("\\s{2}")[0];
            hide();
            onOpenFull.accept(headword);
        });

        stage = new Stage(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);

        // KHONG tu an khi mat tieu diem. Da thu va sai: popup nay hien ra trong luc nguoi
        // dung dang lam viec o ung dung KHAC, no khong bao gio giu tieu diem ca, nen quy tac
        // "mat tieu diem thi an" lam no tat ngay lap tuc. Thay bang hen gio.
        autoHide = new PauseTransition(Duration.seconds(VISIBLE_SECONDS));
        autoHide.setOnFinished(e -> hide());
    }

    private static Label styled(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        l.setWrapText(true);
        return l;
    }
}
