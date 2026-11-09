package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class LangParityTest {

    private static final String[] LANGS = { "zh_CN", "zh_TW", "zh_HK", "ja_JP", "de_DE" };
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sd]");

    private static Map<String, String> load(String lang) throws Exception {
        InputStream in = LangParityTest.class.getResourceAsStream("/assets/minopp/lang/" + lang + ".lang");
        assertNotNull(in, lang);
        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || !line.contains("=")) continue;
                int i = line.indexOf('=');
                String key = line.substring(0, i);
                assertEquals(null, map.put(key, line.substring(i + 1)), lang + " has a duplicate key " + key);
            }
        }
        return map;
    }

    private static List<String> placeholders(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) out.add(m.group());
        Collections.sort(out);
        return new ArrayList<>(new TreeSet<>(out));
    }

    @Test
    public void everyLanguageHasEveryKeyWithTheSamePlaceholders() throws Exception {
        Map<String, String> en = load("en_US");
        for (String lang : LANGS) {
            Map<String, String> other = load(lang);
            TreeSet<String> missing = new TreeSet<>(en.keySet());
            missing.removeAll(other.keySet());
            TreeSet<String> extra = new TreeSet<>(other.keySet());
            extra.removeAll(en.keySet());
            assertEquals(Collections.emptySet(), missing, lang + " is missing keys");
            assertEquals(Collections.emptySet(), extra, lang + " has keys English does not have");
            for (Map.Entry<String, String> e : en.entrySet()) {
                assertEquals(
                    placeholders(e.getValue()),
                    placeholders(other.get(e.getKey())),
                    lang + " placeholders differ for " + e.getKey());
            }
        }
    }
}
