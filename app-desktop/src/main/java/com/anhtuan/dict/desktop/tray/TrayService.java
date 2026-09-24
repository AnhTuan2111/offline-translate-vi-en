package com.anhtuan.dict.desktop.tray;

import javafx.application.Platform;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * Bieu tuong o khay he thong (PLAN.md F6, M6).
 *
 * <p>Day la AWT chu khong phai JavaFX - JavaFX khong co API cho khay he thong. Hai bo thu
 * vien chay chung mot tien trinh duoc, chi can nho: moi thao tac tren menu chay o luong AWT
 * nen phai {@link Platform#runLater} truoc khi cham vao giao dien.
 *
 * <p>Bieu tuong <b>luon hien</b> va co ten ro rang. Ung dung nay khong nup o dau ca: dong
 * cua so chinh thi no thu vao khay va nguoi dung nhin thay no o do.
 */
public final class TrayService {

    private final Runnable onOpen;
    private final Runnable onExit;
    private final ClipboardWatcher watcher;
    private TrayIcon icon;

    public TrayService(ClipboardWatcher watcher, Runnable onOpen, Runnable onExit) {
        this.watcher = watcher;
        this.onOpen = onOpen;
        this.onExit = onExit;
    }

    /** @return false neu he dieu hanh khong ho tro khay - luc do app chay nhu binh thuong */
    public boolean install() {
        if (!SystemTray.isSupported()) return false;
        try {
            PopupMenu menu = new PopupMenu();

            MenuItem open = new MenuItem("Mở cửa sổ tra từ");
            open.addActionListener(e -> Platform.runLater(onOpen));
            menu.add(open);

            CheckboxMenuItem watch = new CheckboxMenuItem("Tra nhanh khi copy (Ctrl+C)", true);
            watch.addItemListener(e -> watcher.setEnabled(watch.getState()));
            menu.add(watch);

            menu.addSeparator();
            MenuItem exit = new MenuItem("Thoát");
            exit.addActionListener(e -> Platform.runLater(onExit));
            menu.add(exit);

            icon = new TrayIcon(drawIcon(), "Từ điển offline Anh - Việt", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> Platform.runLater(onOpen));   // bam dup -> mo cua so
            SystemTray.getSystemTray().add(icon);
            return true;
        } catch (AWTException e) {
            return false;
        }
    }

    public void remove() {
        if (icon != null) SystemTray.getSystemTray().remove(icon);
    }

    /** Bao ngan o goc man hinh, dung khi thu cua so vao khay lan dau. */
    public void notify(String caption, String text) {
        if (icon != null) icon.displayMessage(caption, text, TrayIcon.MessageType.NONE);
    }

    /**
     * Ve bieu tuong bang code thay vi kem file anh: chu "V" tren nen xanh, 32x32.
     * Mot file .png cho viec nay khong dang, va ve tay thi ro net o moi muc phong to.
     */
    private static BufferedImage drawIcon() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x3B6EA5));
        g.fillRoundRect(0, 0, 32, 32, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 20));
        g.drawString("V", 10, 24);
        g.dispose();
        return image;
    }
}
