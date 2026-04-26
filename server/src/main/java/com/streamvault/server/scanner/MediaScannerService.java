package com.streamvault.server.scanner;

import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.repository.MovieRepository;
import com.streamvault.server.service.TmdbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaScannerService {

    @Value("${app.media.root-path}")
    private String mediaRootPath;

    private final MovieRepository movieRepository;
    private final TmdbService tmdbService;
    private final FolderNameParser parser;

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mkv", ".mp4", ".avi", ".mov"
    );

    public record ScanResult(int scanned, int added, int skipped, int errors) {}

    /**
     * Runs automatically on startup.
     * Safe to call multiple times — already-indexed files are skipped.
     */
    @PostConstruct
    public void scanOnStartup() {
        log.info("=== StreamVault Media Scanner starting on: {} ===", mediaRootPath);
        ScanResult result = scan();
        log.info("=== Scan complete — scanned={}, added={}, skipped={}, errors={} ===",
                result.scanned(), result.added(), result.skipped(), result.errors());
    }

    public ScanResult scan() {
        Path root = Path.of(mediaRootPath);

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            log.error("Media root path does not exist or is not a directory: {}", mediaRootPath);
            return new ScanResult(0, 0, 0, 1);
        }

        int[] counters = {0, 0, 0, 0}; // scanned, added, skipped, errors

        try {
            // Walk one level deep: each immediate subdirectory is a movie folder
            try (DirectoryStream<Path> folders = Files.newDirectoryStream(root)) {
                for (Path folder : folders) {
                    if (!Files.isDirectory(folder)) continue;
                    processMovieFolder(folder, counters);
                }
            }
        } catch (IOException e) {
            log.error("Failed to walk media root: {}", e.getMessage(), e);
            counters[3]++;
        }

        return new ScanResult(counters[0], counters[1], counters[2], counters[3]);
    }

    private void processMovieFolder(Path folder, int[] counters) {
        String folderName = folder.getFileName().toString();

        // Find the primary video file inside this folder
        Path videoFile = findVideoFile(folder);
        if (videoFile == null) {
            log.debug("No video file found in folder: {}", folderName);
            return;
        }

        counters[0]++; // scanned
        String localPath = videoFile.toAbsolutePath().toString();

        // Already in DB — skip
        if (movieRepository.existsByLocalPath(localPath)) {
            log.debug("Already indexed, skipping: {}", folderName);
            counters[2]++; // skipped
            return;
        }

        try {
            // Parse title + year from folder name
            FolderNameParser.ParsedMovie parsed = parser.parse(folderName)
                    .orElseThrow(() -> new IllegalStateException("Could not parse folder: " + folderName));

            log.info("New movie found: '{}' ({}) — fetching TMDB metadata...",
                    parsed.title(), parsed.year());

            // Build movie — start with local info, enrich with TMDB
            Movie movie = buildMovieFromLocal(localPath, folder, parsed, videoFile);

            // Enrich with TMDB if API key is set
            tmdbService.enrich(movie, parsed.title(), parsed.year());

            movieRepository.save(movie);
            log.info("  ✓ Saved: '{}'", movie.getTitle());
            counters[1]++; // added

        } catch (Exception e) {
            log.error("Error processing folder '{}': {}", folderName, e.getMessage(), e);
            counters[3]++; // errors
        }
    }

    /**
     * Finds the largest video file in the folder (largest = main feature, not trailer/sample).
     */
    private Path findVideoFile(Path folder) {
        try (var stream = Files.list(folder)) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
                    })
                    .filter(p -> {
                        // Skip obvious non-features
                        String lower = p.getFileName().toString().toLowerCase();
                        return !lower.contains("sample") && !lower.contains("trailer");
                    })
                    .max((a, b) -> {
                        // Pick largest file — most likely the main feature
                        try {
                            return Long.compare(Files.size(a), Files.size(b));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Could not list files in folder {}: {}", folder, e.getMessage());
            return null;
        }
    }

    private Movie buildMovieFromLocal(
            String localPath, Path folder,
            FolderNameParser.ParsedMovie parsed, Path videoFile) throws IOException {

        long fileSize = Files.size(videoFile);

        return Movie.builder()
                .title(parsed.title())
                .releaseYear(parsed.year())
                .localPath(localPath)
                .folderPath(folder.toAbsolutePath().toString())
                .fileSizeBytes(fileSize)
                .isTranscoded(false)
                .build();
    }
}
