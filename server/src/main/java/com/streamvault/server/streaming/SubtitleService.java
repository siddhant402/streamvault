package com.streamvault.server.streaming;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds subtitle files (.srt, .ass, .ssa, .vtt) inside a movie's folder.
 */
@Service
@Slf4j
public class SubtitleService {

    private static final List<String> SUBTITLE_EXTENSIONS = List.of(
            ".srt", ".ass", ".ssa", ".vtt"
    );

    /** What the API returns: no server paths. */
    public record SubtitleFile(String filename, String language, String format) {}

    /**
     * Lists all subtitle files found in the movie's folder.
     */
    public List<SubtitleFile> findSubtitles(String folderPath) {
        List<SubtitleFile> results = new ArrayList<>();
        Path folder = Path.of(folderPath);

        if (!Files.exists(folder)) return results;

        try (var stream = Files.list(folder)) {
            stream.filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return SUBTITLE_EXTENSIONS.stream().anyMatch(name::endsWith);
            }).forEach(p -> {
                String filename = p.getFileName().toString();
                results.add(new SubtitleFile(
                        filename,
                        detectLanguage(filename),
                        detectFormat(filename)
                ));
            });
        } catch (IOException e) {
            log.warn("Could not list subtitles in {}: {}", folderPath, e.getMessage());
        }

        return results;
    }

    /**
     * Finds a subtitle file by name. Only a plain file name with a subtitle extension, sitting
     * directly in the movie's folder, is accepted: no "..", slashes, drive letters or other
     * file types.
     */
    public Optional<Path> findSubtitleFile(String folderPath, String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains(":")
                || filename.contains("..") || filename.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        String lower = filename.toLowerCase();
        if (SUBTITLE_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            return Optional.empty();
        }

        Path folder;
        Path file;
        try {
            folder = Path.of(folderPath).toAbsolutePath().normalize();
            file = folder.resolve(filename).normalize();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        if (!folder.equals(file.getParent())) {
            return Optional.empty();
        }
        if (Files.isRegularFile(file) && Files.isReadable(file)) {
            return Optional.of(file);
        }
        return Optional.empty();
    }

    /**
     * Guesses language from filename patterns like:
     * "Movie.en.srt", "Movie.English.srt", "Movie_fr.srt"
     */
    private String detectLanguage(String filename) {
        String lower = filename.toLowerCase();

        if (lower.contains(".en.") || lower.contains("_en.") || lower.contains("english")) return "English";
        if (lower.contains(".fr.") || lower.contains("_fr.") || lower.contains("french"))  return "French";
        if (lower.contains(".de.") || lower.contains("_de.") || lower.contains("german"))  return "German";
        if (lower.contains(".es.") || lower.contains("_es.") || lower.contains("spanish")) return "Spanish";
        if (lower.contains(".hi.") || lower.contains("_hi.") || lower.contains("hindi"))   return "Hindi";
        if (lower.contains(".ja.") || lower.contains("_ja.") || lower.contains("japanese")) return "Japanese";
        if (lower.contains(".ko.") || lower.contains("_ko.") || lower.contains("korean"))  return "Korean";
        if (lower.contains(".pt.") || lower.contains("_pt.") || lower.contains("portuguese")) return "Portuguese";

        return "Unknown";
    }

    private String detectFormat(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".srt")) return "srt";
        if (lower.endsWith(".ass") || lower.endsWith(".ssa")) return "ass";
        if (lower.endsWith(".vtt")) return "vtt";
        return "unknown";
    }
}
