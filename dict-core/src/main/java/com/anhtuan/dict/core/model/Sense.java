package com.anhtuan.dict.core.model;

import java.util.List;

/**
 * Mot nhom nghia theo tu loai, mo ra boi dong '*' trong file nguon.
 * Vi du '@about' co 2 Sense: "pho tu" va "gioi tu".
 *
 * @param pos      tu loai ("danh tu", "dong tu"...); NULL neu entry khong co dong '*'
 * @param glosses  cac nghia, lay tu cac dong '-'
 * @param examples cac vi du, lay tu cac dong '='
 */
public record Sense(String pos, List<String> glosses, List<Example> examples) {
    public Sense {
        glosses = List.copyOf(glosses);
        examples = List.copyOf(examples);
    }

    /** Nghia dau tien - dung lam gloss mac dinh khi dich cau (PLAN.md 8.1 buoc 4). */
    public String primaryGloss() {
        return glosses.isEmpty() ? null : glosses.getFirst();
    }
}
