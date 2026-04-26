package com.streamvault.server.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses movie title and year from folder names like:
 *
 *   "A Beautiful Mind (2001) [1080p]"       → title="A Beautiful Mind",  year=2001
 *   "The Dark Knight (2008) [4K HDR]"       → title="The Dark Knight",   year=2008
 *   "Interstellar (2014)"                   → title="Interstellar",       year=2014
 *   "Dune Part Two (2024) [1080p BluRay]"   → title="Dune Part Two",      year=2024
 *   "Inception.2010.1080p"                  → title="Inception",          year=2010
 *   "The_Godfather_1972_4K"                 → title="The Godfather",      year=1972
 */
@Component
@Slf4j
public class FolderNameParser {

    public record ParsedMovie(String title, Integer year) {}

    /**
     * Primary pattern: title (YYYY) [optional tags]
     * Handles:  "A Beautiful Mind (2001) [1080p]"
     */
    private static final Pattern PAREN_YEAR =
            Pattern.compile("^(.+?)\\s*\\((\\d{4})\\).*$");

    /**
     * Fallback pattern: title.YYYY.tags or title_YYYY_tags
     * Handles:  "Inception.2010.1080p"  /  "The_Godfather_1972_4K"
     */
    private static final Pattern DOT_UNDERSCORE_YEAR =
            Pattern.compile("^(.+?)[._](\\d{4})[._]?.*$");

    public Optional<ParsedMovie> parse(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return Optional.empty();
        }

        // Try "(YYYY)" pattern first — most common for well-organised libraries
        Matcher m = PAREN_YEAR.matcher(folderName.trim());
        if (m.matches()) {
            String title = cleanTitle(m.group(1));
            int year = Integer.parseInt(m.group(2));
            if (isValidYear(year)) {
                log.debug("Parsed [paren]  '{}' → title='{}', year={}", folderName, title, year);
                return Optional.of(new ParsedMovie(title, year));
            }
        }

        // Fallback: dot/underscore separated
        m = DOT_UNDERSCORE_YEAR.matcher(folderName.trim());
        if (m.matches()) {
            String title = cleanTitle(m.group(1).replace('.', ' ').replace('_', ' '));
            int year = Integer.parseInt(m.group(2));
            if (isValidYear(year)) {
                log.debug("Parsed [dot/us] '{}' → title='{}', year={}", folderName, title, year);
                return Optional.of(new ParsedMovie(title, year));
            }
        }

        // Last resort: use the folder name as the title with no year
        String fallbackTitle = cleanTitle(folderName);
        log.warn("Could not parse year from folder '{}' — using folder name as title", folderName);
        return Optional.of(new ParsedMovie(fallbackTitle, null));
    }

    private String cleanTitle(String raw) {
        return raw
                .trim()
                // Remove trailing separators
                .replaceAll("[._\\-]+$", "")
                // Collapse multiple spaces
                .replaceAll("\\s{2,}", " ")
                // Title-case first letter only (leave the rest as-is)
                .trim();
    }

    private boolean isValidYear(int year) {
        // Movies exist from ~1888 to slightly in the future (upcoming releases)
        return year >= 1888 && year <= 2030;
    }
}
