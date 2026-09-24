package com.anhtuan.dict.desktop.ui;

import com.anhtuan.dict.core.source.DictSource;
import com.anhtuan.dict.core.source.SourceCatalog;
import com.anhtuan.dict.desktop.config.AppContext;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Locale;

/**
 * Cua so quan ly nguon tu dien: bat / tat / doi thu tu uu tien (PLAN.md F5, M7).
 *
 * <p>Bat-tat co hieu luc NGAY, khong phai sinh lai du lieu: moi muc tu trong pack mang san
 * {@code sourceId} nen tat mot nguon chi la mot phep loc. Doi lai, THEM mot nguon moi thi
 * phai chay lai lenh {@code build} - pack la file bat bien.
 */
final class SourceDialog {

    private SourceDialog() {}

    static void show(Window owner, AppContext ctx, Runnable onChanged) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Nguồn từ điển");

        VBox root = new VBox(10);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("source-dialog");

        VBox list = new VBox(6);
        render(list, ctx, onChanged);

        Label note = new Label("""
                Bật/tắt có hiệu lực ngay. Nguồn ở trên được ưu tiên: nghĩa của nó hiện trước.

                Thêm nguồn mới (một file .tsv hai cột: từ tiếng Anh <TAB> nghĩa tiếng Việt):
                  ImporterMain build anhviet109K.txt data/build tudien-cua-ban.tsv
                """);
        note.getStyleClass().add("message");
        note.setWrapText(true);

        Button close = new Button("Đóng");
        close.setOnAction(e -> stage.close());
        HBox bottom = new HBox(close);
        bottom.setStyle("-fx-alignment: center-right;");

        root.getChildren().addAll(list, note, bottom);
        Scene scene = new Scene(root, 520, 360);
        scene.getStylesheets().add(SourceDialog.class.getResource("/css/dict.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static void render(VBox list, AppContext ctx, Runnable onChanged) {
        list.getChildren().clear();
        SourceCatalog catalog = ctx.catalog();

        for (DictSource source : catalog.all()) {
            HBox row = new HBox(8);
            row.getStyleClass().add("source-row");

            CheckBox enabled = new CheckBox(source.name());
            enabled.setSelected(source.enabled());
            enabled.setOnAction(e -> {
                ctx.updateCatalog(ctx.catalog().setEnabled(source.id(), enabled.isSelected()));
                render(list, ctx, onChanged);
                onChanged.run();
            });

            Label count = new Label(String.format(Locale.ROOT, "%,d mục · %s",
                    source.entries(), source.format()));
            count.getStyleClass().add("source-count");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button up = new Button("▲");
            up.setOnAction(e -> {
                ctx.updateCatalog(ctx.catalog().move(source.id(), -1));
                render(list, ctx, onChanged);
                onChanged.run();
            });
            Button down = new Button("▼");
            down.setOnAction(e -> {
                ctx.updateCatalog(ctx.catalog().move(source.id(), 1));
                render(list, ctx, onChanged);
                onChanged.run();
            });
            up.getStyleClass().add("source-move");
            down.getStyleClass().add("source-move");

            row.getChildren().addAll(enabled, count, spacer, up, down);
            list.getChildren().add(row);
        }

        if (!catalog.hasEnabled()) {
            Label warn = new Label("Đã tắt hết nguồn — ứng dụng đang dùng lại tất cả để "
                    + "màn hình không trống trơn.");
            warn.getStyleClass().add("source-warning");
            warn.setWrapText(true);
            list.getChildren().add(warn);
        }
    }
}
