package com.anhtuan.dict.desktop.tray;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.function.Consumer;

/**
 * Theo doi clipboard de tra nhanh (PLAN.md F6, AD-9).
 *
 * <h2>Vi sao doc clipboard theo chu ky chu khong bat phim tat toan he thong</h2>
 * Bat phim tat toan he thong phai dung thu vien native (JNativeHook / JNA RegisterHotKey):
 * them file .dll di kem, them rac roi khi ky so installer, va de bi phan mem diet virus
 * soi. Doc clipboard 300 ms mot lan thi chi dung JDK, khong them mot byte phu thuoc nao,
 * va do duoc la CPU luc ranh duoi 1%.
 *
 * <h2>Ba quy tac de khong lam phien nguoi dung</h2>
 * <ul>
 *   <li>Chi phan ung khi noi dung THAY DOI - copy hai lan cung mot tu thi khong hien lai.</li>
 *   <li>Chi nhan van ban ngan ({@value #MAX_LENGTH} ky tu): chep ca trang tai lieu ma cung
 *       nhay popup thi phien.</li>
 *   <li>Doc clipboard co the nem loi khi ung dung khac dang giu no - bo qua luot do, lan sau
 *       doc lai, tuyet doi khong lam sap ung dung vi mot lan doc hong.</li>
 * </ul>
 */
public final class ClipboardWatcher {

    /** Chu ky doc, mili giay. 300 ms la nguong nguoi dung con thay "ngay lap tuc". */
    public static final int POLL_MILLIS = 300;

    /** Dai hon nay thi coi nhu nguoi dung dang chep tai lieu, khong phai tra tu. */
    public static final int MAX_LENGTH = 200;

    private final Consumer<String> onNewText;
    private volatile boolean enabled = true;
    private volatile boolean running;
    private String lastSeen = "";
    private Thread thread;

    public ClipboardWatcher(Consumer<String> onNewText) {
        this.onNewText = onNewText;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "theo-doi-clipboard");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) lastSeen = readClipboard();   // bo qua thu dang nam san trong clipboard
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void loop() {
        lastSeen = readClipboard();
        while (running) {
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!enabled) continue;
            String text = readClipboard();
            if (text.isEmpty() || text.equals(lastSeen)) continue;
            lastSeen = text;
            if (isLookupCandidate(text)) onNewText.accept(text);
        }
    }

    /** Rong neu clipboard khong co van ban, hoac dang bi ung dung khac giu. */
    private static String readClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return "";
            Object data = clipboard.getData(DataFlavor.stringFlavor);
            return data == null ? "" : data.toString().trim();
        } catch (Exception e) {
            return "";                                   // ung dung khac dang giu clipboard
        }
    }

    /** Van ban nay co dang mot thu can tra khong. */
    static boolean isLookupCandidate(String text) {
        if (text.isEmpty() || text.length() > MAX_LENGTH) return false;
        if (text.indexOf('\n') >= 0) return false;       // nhieu dong -> dang chep tai lieu
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) return true;
        }
        return false;                                    // toan so hoac ky hieu
    }
}
