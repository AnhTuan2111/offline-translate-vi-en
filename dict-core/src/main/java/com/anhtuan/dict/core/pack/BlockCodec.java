package com.anhtuan.dict.core.pack;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Nen / giai nen mot block du lieu bang Deflate CO SAN TRONG JDK (PLAN.md AD-6).
 *
 * Da do thuc te tren anhviet109K.txt:
 *   - zstd -19 theo block : 4,8 MB
 *   - Deflate theo block  : 4,9 MB
 * Chenh 2% nhung doi lai dict-core giu duoc ZERO DEPENDENCY. Deal tot.
 */
public final class BlockCodec {
    private BlockCodec() {}

    public static byte[] compress(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true); // nowrap: bo header zlib
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3);
            byte[] chunk = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(chunk);
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static byte[] decompress(byte[] compressed, int rawLength) {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(compressed);
            byte[] raw = new byte[rawLength];
            int total = 0;
            while (total < rawLength) {
                int n = inflater.inflate(raw, total, rawLength - total);
                if (n == 0) break;
                total += n;
            }
            if (total != rawLength) {
                throw new IllegalStateException("giai nen thieu: mong " + rawLength + " duoc " + total);
            }
            return raw;
        } catch (DataFormatException e) {
            throw new IllegalStateException("block hong, khong giai nen duoc", e);
        } finally {
            inflater.end();
        }
    }
}
