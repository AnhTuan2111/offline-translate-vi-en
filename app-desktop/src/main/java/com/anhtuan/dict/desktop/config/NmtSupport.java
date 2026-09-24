package com.anhtuan.dict.desktop.config;

import com.anhtuan.dict.nmt.OnnxNmtEngine;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Cong vao cho engine dich bang mo hinh no-ron - va la NOI DUY NHAT trong app-desktop
 * biet den su ton tai cua module {@code nmt-engine}.
 *
 * <h2>Vi sao phai bao lai mot lop</h2>
 * Module {@code nmt-engine} keo theo ONNX Runtime, mot jar 132 MB. Ban dong goi mac dinh
 * KHONG mang no theo, nen luc chay cac class do co the khong ton tai. Java nem
 * {@link NoClassDefFoundError} (mot Error, khong phai Exception) khi cham vao class vang mat -
 * bat o day mot lan, o ngoai chi thay {@link Optional} rong va tinh nang tu an di.
 *
 * <p>Hai dieu kien phai dung thi tinh nang moi bat:
 * <ol>
 *   <li>jar cua {@code nmt-engine} + ONNX Runtime co trong classpath;</li>
 *   <li>thu muc mo hinh 98 MB da duoc tai ve ({@code scripts/tai-model-nmt.ps1}).</li>
 * </ol>
 */
public final class NmtSupport {

    private NmtSupport() {}

    /** Thu muc chua mo hinh, nam ngay trong thu muc du lieu. */
    public static Path modelDir(Path dataDir) {
        return dataDir.resolve(OnnxNmtEngine.MODEL_DIR);
    }

    /** Ca thu vien lan mo hinh deu san sang chua. Khong nem loi trong moi truong hop. */
    public static boolean isAvailable(Path dataDir) {
        try {
            return OnnxNmtEngine.isInstalled(modelDir(dataDir));
        } catch (NoClassDefFoundError | RuntimeException e) {
            return false;                       // ban dong goi khong kem thu vien NMT
        }
    }

    /**
     * Nap mo hinh. Ton vai tram ms va ~300 MB RAM nen chi goi khi nguoi dung thuc su bat
     * tinh nang, va nen goi ngoai luong giao dien.
     */
    public static Optional<OnnxNmtEngine> load(Path dataDir) {
        try {
            return Optional.of(OnnxNmtEngine.load(modelDir(dataDir)));
        } catch (NoClassDefFoundError | RuntimeException e) {
            return Optional.empty();
        }
    }
}
