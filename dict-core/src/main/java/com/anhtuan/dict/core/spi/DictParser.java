package com.anhtuan.dict.core.spi;

import com.anhtuan.dict.core.model.Entry;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * CONG MO RONG SO 1 - "bo sung tai lieu" (PLAN.md F5, M7).
 *
 * Them mot dinh dang tu dien moi (StarDict, CSV, TSV, JSON...) chi can
 * viet mot class implement interface nay. KHONG duoc sua gi trong dict-core.
 *
 * Tra ve Stream chu khong phai List: file nguon co the rat lon (15 MB / 108k entry),
 * doc lazy giup importer khong phai giu toan bo trong RAM.
 */
public interface DictParser {

    /** Ten dinh dang, dung trong CLI va metadata nguon. Vi du "anhviet109k". */
    String formatId();

    /** Doan xem parser nay co xu ly duoc file hay khong (dua vao duoi file / vai dong dau). */
    boolean canParse(Path source);

    /**
     * Doc file nguon thanh cac Entry.
     * KHONG duoc throw khi gap dong loi dinh dang - phai bo qua va ghi log
     * (PLAN.md 4.2 quy tac 9). Chi throw khi khong doc duoc file.
     *
     * @return stream lazy; nguoi goi phai dong bang try-with-resources
     */
    Stream<Entry> parse(Path source, int sourceId);
}
