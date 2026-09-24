package com.anhtuan.dict.core.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Danh muc cac nguon tu dien: bat / tat / doi thu tu uu tien (PLAN.md F5, M7).
 *
 * <h2>Vi sao bat-tat lam duoc ngay luc chay ma khong phai build lai</h2>
 * Moi {@code Entry} trong pack deu mang san {@code sourceId}. Vi vay "tat mot nguon" chi la
 * loc bo cac entry co sourceId do khi tra cuu - khong dong den file du lieu. Doi lai, THEM
 * mot nguon moi thi phai sinh lai pack (khoang 5 giay): pack la file bat bien, khong chen
 * them vao giua duoc, va day cung la ly do no nho va tra cuu nhanh (AD-3).
 *
 * <h2>Dinh dang file</h2>
 * {@code sources.tsv} de dang van ban, moi dong mot nguon, ngan bang TAB:
 * <pre>
 *   id  ten                 dinh-dang    file                 so-muc-tu  bat  uu-tien
 *   0   Anh-Viet 109K       anhviet109k  anhviet109K.txt      108854     1    0
 *   1   Thuat ngu CNTT      tsv          cntt.tsv             1240       1    1
 * </pre>
 * Dang van ban co chu y: nguoi dung mo ra sua tay duoc, va khi co gi la thi nhin mot cai la
 * hieu - khac han mot file nhi phan.
 */
public final class SourceCatalog {

    public static final String FILE_NAME = "sources.tsv";

    private final List<DictSource> sources;

    private SourceCatalog(List<DictSource> sources) {
        this.sources = new ArrayList<>(sources);
        this.sources.sort(Comparator.comparingInt(DictSource::priority)
                .thenComparingInt(DictSource::id));
    }

    public static SourceCatalog of(List<DictSource> sources) {
        return new SourceCatalog(sources);
    }

    /** Khong co file thi tra ve danh muc mot nguon mac dinh - du lieu cu van chay duoc. */
    public static SourceCatalog loadOrDefault(Path file, String defaultName, int entryCount) {
        if (!Files.isRegularFile(file)) {
            return new SourceCatalog(List.of(
                    new DictSource(0, defaultName, "anhviet109k", "", entryCount, true, 0)));
        }
        try {
            List<DictSource> out = new ArrayList<>(4);
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.charAt(0) == '#') continue;
                String[] c = line.split("\t", -1);
                if (c.length < 7) continue;
                out.add(new DictSource(Integer.parseInt(c[0].trim()), c[1].trim(), c[2].trim(),
                        c[3].trim(), Integer.parseInt(c[4].trim()),
                        "1".equals(c[5].trim()), Integer.parseInt(c[6].trim())));
            }
            return out.isEmpty()
                    ? loadOrDefault(Path.of("khong-ton-tai"), defaultName, entryCount)
                    : new SourceCatalog(out);
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("file " + file + " hong: " + e.getMessage(), e);
        }
    }

    public void save(Path file) {
        List<String> lines = new ArrayList<>(sources.size() + 2);
        lines.add("# id\tten\tdinh-dang\tfile\tso-muc-tu\tbat(1/0)\tuu-tien");
        for (DictSource s : sources) {
            lines.add(String.join("\t", String.valueOf(s.id()), s.name(), s.format(), s.file(),
                    String.valueOf(s.entries()), s.enabled() ? "1" : "0",
                    String.valueOf(s.priority())));
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("khong ghi duoc " + file, e);
        }
    }

    /** Tat ca nguon, da sap theo uu tien. */
    public List<DictSource> all() {
        return List.copyOf(sources);
    }

    public int size() {
        return sources.size();
    }

    /** Id cua cac nguon dang bat. Rong nghia la nguoi dung tat het - luc do khong loc gi ca. */
    public Set<Integer> enabledIds() {
        Set<Integer> out = new LinkedHashSet<>(sources.size());
        for (DictSource s : sources) if (s.enabled()) out.add(s.id());
        return out;
    }

    /** Nguon nay co dang bat khong. Nguon la (khong co trong danh muc) coi nhu bat. */
    public boolean isEnabled(int sourceId) {
        for (DictSource s : sources) if (s.id() == sourceId) return s.enabled();
        return true;
    }

    /** Thu tu uu tien cua mot nguon; nguon la xep sau cung. */
    public int priorityOf(int sourceId) {
        for (DictSource s : sources) if (s.id() == sourceId) return s.priority();
        return Integer.MAX_VALUE;
    }

    /** Con it nhat mot nguon dang bat khong. Tat het thi UI phai bao, khong de man hinh trong. */
    public boolean hasEnabled() {
        return sources.stream().anyMatch(DictSource::enabled);
    }

    public SourceCatalog setEnabled(int sourceId, boolean enabled) {
        List<DictSource> out = new ArrayList<>(sources.size());
        for (DictSource s : sources) out.add(s.id() == sourceId ? s.withEnabled(enabled) : s);
        return new SourceCatalog(out);
    }

    /** Day mot nguon len tren hoac xuong duoi mot bac trong thu tu uu tien. */
    public SourceCatalog move(int sourceId, int delta) {
        List<DictSource> ordered = new ArrayList<>(sources);
        int at = -1;
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).id() == sourceId) at = i;
        int to = at + delta;
        if (at < 0 || to < 0 || to >= ordered.size()) return this;
        DictSource moved = ordered.remove(at);
        ordered.add(to, moved);
        List<DictSource> renumbered = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) renumbered.add(ordered.get(i).withPriority(i));
        return new SourceCatalog(renumbered);
    }
}
