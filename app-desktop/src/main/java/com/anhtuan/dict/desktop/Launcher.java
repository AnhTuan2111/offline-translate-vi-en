package com.anhtuan.dict.desktop;

import javafx.application.Application;

/**
 * Diem vao danh RIENG cho ban da dong goi bang jpackage.
 *
 * <h2>Vi sao phai co class nay</h2>
 * JavaFX co mot phep kiem tra: neu class {@code main} KE THUA {@link Application} ma
 * javafx.graphics khong nam tren module path, no dung ngay voi thong bao
 * "JavaFX runtime components are missing". Trong khi do jpackage lai tu dong nem MOI file
 * .jar trong thu muc dau vao - ke ca cac jar JavaFX nam trong thu muc con - vao classpath.
 * Dat JavaFX tren ca classpath lan module path thi JVM bao trung module va app chet im lang:
 * bam .exe, khong co cua so nao hien ra, khong co thong bao loi nao het.
 *
 * <p>Cach ra: cho diem vao la mot class KHONG ke thua Application. Phep kiem tra tren khong
 * chay, JavaFX khoi dong binh thuong o che do classpath.
 *
 * <p>Chay tu ma nguon (scripts/run.ps1) thi van dung {@link DictApp} voi module path chuan.
 */
public final class Launcher {

    public static void main(String[] args) {
        Application.launch(DictApp.class, args);
    }

    private Launcher() {}
}
