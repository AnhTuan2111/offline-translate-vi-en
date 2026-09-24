package com.anhtuan.dict.desktop.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Phien ban va ban phat hanh, doc tu {@code version.properties} do Maven sinh luc build.
 *
 * <p>Co hai BAN PHAT HANH tu cung mot ma nguon:
 * <ul>
 *   <li><b>thuong</b> - tu dien + dich bang luat. Khong co mo hinh AI nao. ~46 MB.</li>
 *   <li><b>ai</b> - kem them mo hinh no-ron dich cau chay cuc bo. ~185 MB.</li>
 * </ul>
 * Tach thanh hai ban de nguoi dung chon dung thu minh can, chu KHONG phai hai nhanh ma nguon:
 * cung mot code, khac nhau o cho bo dong goi co mang theo mo hinh hay khong. Hai nhanh git
 * se phan ky va tra gia bang moi sua loi phai lam hai lan.
 */
public final class AppVersion {

    private static final Properties PROPERTIES = load();

    private AppVersion() {}

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = AppVersion.class.getResourceAsStream("/version.properties")) {
            if (in != null) p.load(in);
        } catch (IOException e) {
            // Chay tu IDE khi chua build thi thieu file - khong phai ly do de app khong chay
        }
        return p;
    }

    public static String version() {
        return PROPERTIES.getProperty("version", "dev");
    }

    /** "thuong" hoac "ai". */
    public static String edition() {
        return PROPERTIES.getProperty("edition", "thuong");
    }

    public static boolean isAiEdition() {
        return "ai".equals(edition());
    }

    /** Chuoi hien o thanh trang thai: "v0.1.0 · bản thường". */
    public static String display() {
        return "v" + version() + " · " + (isAiEdition() ? "bản AI" : "bản thường");
    }
}
