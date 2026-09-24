package com.anhtuan.dict.desktop;

import com.anhtuan.dict.desktop.config.AppContext;
import com.anhtuan.dict.desktop.tray.ClipboardWatcher;
import com.anhtuan.dict.desktop.tray.QuickLookPopup;
import com.anhtuan.dict.desktop.tray.TrayService;
import com.anhtuan.dict.desktop.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Diem vao cua app desktop.
 *
 * <p>Vong doi cua JavaFX duoc dung dung nhu no duoc thiet ke:
 * <ul>
 *   <li>{@link #init()} chay TRUOC khi UI hien - nap du lieu o day thi cua so khong
 *       bi hien ra roi treo mot luc;</li>
 *   <li>{@link #start(Stage)} chi con viec ve, chay tren JavaFX Application Thread;</li>
 *   <li>{@link #stop()} dong {@link AppContext} de unmap cac file mmap ngay lap tuc.</li>
 * </ul>
 *
 * <p>Khong co Spring Boot o day - ly do da ghi trong {@link AppContext}.
 */
public final class DictApp extends Application {

    private AppContext ctx;
    private String failure;
    private MainView view;
    private TrayService tray;
    private ClipboardWatcher clipboardWatcher;
    private QuickLookPopup popup;
    private Stage mainStage;
    private boolean trayNoticeShown;

    @Override
    public void init() {
        try {
            ctx = new AppContext();
        } catch (RuntimeException e) {
            // Thieu du lieu la loi thuong gap nhat khi chay lan dau. Bao ngay tren cua so,
            // dung de nguoi dung phai di doc stack trace trong console.
            failure = e.getMessage();
        }
    }

    @Override
    public void start(Stage stage) {
        if (ctx == null) {
            Label label = new Label(failure);
            label.setWrapText(true);
            label.setStyle("-fx-padding: 16; -fx-font-family: 'Consolas', monospace;");
            stage.setScene(new Scene(new VBox(label), 720, 260));
            stage.setTitle("Tu dien offline - thieu du lieu");
            stage.show();
            return;
        }
        mainStage = stage;
        view = new MainView(ctx, getParameters().getRaw());
        view.show(stage);
        installTray(stage);
    }

    /**
     * Thu cua so vao khay thay vi thoat han, va bat tra nhanh tu clipboard (PLAN.md F6).
     *
     * <p>{@link Platform#setImplicitExit} phai tat TRUOC khi an cua so cuoi cung, neu khong
     * JavaFX tu tat may luc cua so dong va ung dung chet ngay khi vua thu vao khay.
     */
    private void installTray(Stage stage) {
        popup = new QuickLookPopup(ctx, this::openFromPopup);
        clipboardWatcher = new ClipboardWatcher(
                text -> Platform.runLater(() -> popup.show(text)));

        tray = new TrayService(clipboardWatcher, this::showMainWindow, this::quit);
        if (!tray.install()) return;                 // he dieu hanh khong ho tro khay

        Platform.setImplicitExit(false);
        clipboardWatcher.start();

        stage.setOnCloseRequest(event -> {
            event.consume();                          // khong dong, chi thu vao khay
            stage.hide();
            if (!trayNoticeShown) {
                trayNoticeShown = true;
                tray.notify("Từ điển vẫn đang chạy",
                        "Ứng dụng thu vào khay hệ thống. Bôi đen chữ ở bất kỳ đâu rồi Ctrl+C "
                                + "để tra nhanh. Bấm phải vào biểu tượng để thoát hẳn.");
            }
        });
    }

    private void showMainWindow() {
        if (mainStage == null) return;
        mainStage.show();
        mainStage.setIconified(false);
        mainStage.toFront();
    }

    private void openFromPopup(String headword) {
        showMainWindow();
        if (view != null) view.lookup(headword);
    }

    private void quit() {
        if (clipboardWatcher != null) clipboardWatcher.stop();
        if (tray != null) tray.remove();
        Platform.setImplicitExit(true);
        if (mainStage != null) mainStage.close();
        stop();
    }

    @Override
    public void stop() {
        if (clipboardWatcher != null) clipboardWatcher.stop();
        if (tray != null) tray.remove();
        if (ctx != null) ctx.close();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
