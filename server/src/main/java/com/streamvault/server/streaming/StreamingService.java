package com.streamvault.server.streaming;

import com.streamvault.server.exception.ResourceNotFoundException;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.repository.MovieRepository;
import com.streamvault.server.streaming.SubtitleService.SubtitleFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final MovieRepository movieRepository;
    private final TranscodeManager transcodeManager;
    private final SubtitleService subtitleService;

    private static final String API_BASE = "http://localhost:8080";

    public Resource getPlaylist(UUID movieId, String token) throws IOException, InterruptedException {
        Movie movie = getMovie(movieId);
        TranscodeSession session = transcodeManager.getOrStartSession(movieId, movie.getLocalPath());
        session.touch();

        Path playlistPath = Path.of(session.getPlaylistPath());

        // Wait up to 60s for the playlist file — just its existence means
        // FFmpeg is running and the rolling window has valid segments
// Wait for playlist to exist AND have content
        int waited = 0;
        while (waited < 120) {
            if (Files.exists(playlistPath)) {
                Path seg0 = session.getOutputDir().resolve("seg0.ts");
                if (Files.exists(seg0) && Files.size(playlistPath) > 0) break;
            }
            try { Thread.sleep(500); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); break;
            }
            waited++;
        }
        if (!Files.exists(playlistPath) || Files.size(playlistPath) == 0) {
            throw new ResourceNotFoundException("Playlist not ready for movie: " + movieId);
        }

        String content = Files.readString(playlistPath, StandardCharsets.UTF_8);
        String rewritten = rewritePlaylist(content, movieId, token);
        return new ByteArrayResource(rewritten.getBytes(StandardCharsets.UTF_8));
    }

    private String rewritePlaylist(String content, UUID movieId, String token) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.endsWith(".ts") && !trimmed.startsWith("#")) {
                String filename = Path.of(trimmed).getFileName().toString();
                String absoluteUrl = API_BASE + "/api/stream/" + movieId
                        + "/segments/" + filename
                        + (token != null ? "?token=" + token : "");
                sb.append(absoluteUrl).append("\n");
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public Resource getSegment(UUID movieId, String segmentFilename) {
        TranscodeSession session = transcodeManager.getSession(movieId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active stream session for movie: " + movieId));
        session.touch();

        Path segmentPath = session.getOutputDir().resolve(segmentFilename);
        if (!Files.exists(segmentPath)) {
            throw new ResourceNotFoundException("Segment not found: " + segmentFilename);
        }
        return new FileSystemResource(segmentPath);
    }

    public List<SubtitleFile> getSubtitles(UUID movieId) {
        Movie movie = getMovie(movieId);
        if (movie.getFolderPath() == null) return List.of();
        return subtitleService.findSubtitles(movie.getFolderPath());
    }

    public Resource getSubtitleFile(UUID movieId, String filename) {
        Movie movie = getMovie(movieId);
        if (movie.getFolderPath() == null)
            throw new ResourceNotFoundException("No folder path for movie: " + movieId);
        Path subtitlePath = subtitleService.findSubtitleFile(movie.getFolderPath(), filename)
                .orElseThrow(() -> new ResourceNotFoundException("Subtitle file not found: " + filename));
        return new FileSystemResource(subtitlePath);
    }

    /**
     * Only stops the session if FFmpeg is done/failed — never kills a running session.
     * The idle cleanup scheduler handles killing truly abandoned sessions.
     */
    public void stopStream(UUID movieId) {
        transcodeManager.getSession(movieId).ifPresent(session -> {
            if (!session.isAlive()) {
                log.info("Session for movie {} already finished, cleaning up", movieId);
                transcodeManager.removeSession(movieId);
            } else {
                // Just touch it — keep it alive, idle cleanup will handle it
                session.touch();
                log.debug("stopStream called for movie {} but FFmpeg still running — keeping alive", movieId);
            }
        });
    }

    private Movie getMovie(UUID movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found: " + movieId));
    }
}
