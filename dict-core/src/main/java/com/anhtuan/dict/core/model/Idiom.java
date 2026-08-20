package com.anhtuan.dict.core.model;

import java.util.List;

/**
 * Thanh ngu / cum tu co dinh, mo ra boi dong '!' trong file nguon.
 *
 * CAI BAY LON NHAT CUA PARSER (PLAN.md 4.2 quy tac 3):
 * cac dong '-' dung sau '!' thuoc ve Idiom nay, KHONG thuoc ve Sense dang mo.
 * Idiom cung co the co vi du '=' rieng - vi du '!to be about to' trong entry '@about'.
 */
public record Idiom(String phrase, List<String> glosses, List<Example> examples) {
    public Idiom {
        if (phrase == null) throw new IllegalArgumentException("phrase khong duoc null");
        glosses = List.copyOf(glosses);
        examples = List.copyOf(examples);
    }
}
