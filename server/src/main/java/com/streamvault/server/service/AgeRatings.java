package com.streamvault.server.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a country's film certification (as TMDB reports it) into the minimum viewer age
 * used by profile age limits. Unknown certifications give null ("not rated").
 */
public final class AgeRatings {

    private AgeRatings() {}

    private static final Map<String, Map<String, Integer>> BY_COUNTRY = Map.of(
            "US", Map.of("G", 0, "PG", 7, "PG-13", 13, "R", 17, "NC-17", 18),
            "IN", Map.of("U", 0, "UA", 12, "UA 7+", 7, "UA 13+", 13, "UA 16+", 16, "A", 18, "S", 18),
            "GB", Map.of("U", 0, "PG", 7, "12", 12, "12A", 12, "15", 15, "18", 18, "R18", 18)
    );

    /** Ratings that are just an age, e.g. Germany's "12" or "FSK 16", Australia's "MA15+". */
    private static final Pattern AGE_NUMBER = Pattern.compile("(\\d{1,2})\\+?$");

    public static Integer minimumAge(String country, String certification) {
        if (certification == null || certification.isBlank()) return null;
        String cert = certification.trim().toUpperCase(Locale.ROOT);

        Map<String, Integer> table = BY_COUNTRY.get(country == null ? "" : country.toUpperCase(Locale.ROOT));
        if (table != null && table.containsKey(cert)) {
            return table.get(cert);
        }

        Matcher m = AGE_NUMBER.matcher(cert);
        if (m.find()) {
            int age = Integer.parseInt(m.group(1));
            return age <= 21 ? Math.min(age, 18) : null;
        }
        return null;
    }
}
