package com.streamvault.server.streaming;

import com.streamvault.server.exception.ResourceNotFoundException;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.repository.MovieRepository;
import com.streamvault.server.security.JwtTokenProvider;
import com.streamvault.server.security.JwtTokenProvider.StreamToken;
import com.streamvault.server.service.ViewerAgeLimit;
import com.streamvault.server.streaming.SubtitleService.SubtitleFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final MovieRepository movieRepository;
    private final TranscodeManager transcodeManager;
    private final SubtitleService subtitleService;
    private final JwtTokenProvider jwtTokenProvider;

    /** FFmpeg names segments seg0.ts, seg1.ts, ... — nothing else may be requested. */
    private static final Pattern SEGMENT_NAME = Pattern.compile("seg\\d{1,6}\\.ts");

    public Resource getPlaylist(UUID movieId, String token, String owner, int ageLimit)
            throws IOException, InterruptedException {
        Movie movie = getMovie(movieId);
        ViewerAgeLimit.require(ageLimit, movie);
        TranscodeSession session = transcodeManager.getOrStartSession(movieId, movie.getLocalPath(), owner);
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

        // Carry the token into segment URLs only if it is a stream token for this movie,
        // so a login token sent in a URL is never echoed back into the playlist.
        String segmentToken = token != null && jwtTokenProvider.isStreamTokenFor(token, movieId) ? token : null;

        String content = Files.readString(playlistPath, StandardCharsets.UTF_8);
        String rewritten = rewritePlaylist(content, movieId, segmentToken);
        return new ByteArrayResource(rewritten.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Rewrites segment lines to URLs relative to the playlist ("segments/seg0.ts"), so the
     * player fetches them from whatever address it used for the playlist: localhost, a LAN
     * address or the HTTPS proxy. Adds the stream token when one is given.
     */
    static String rewritePlaylist(String content, UUID movieId, String token) {
        StringBuilder sb = new StringBuilder();
        String query = token != null ? "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8) : "";
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.endsWith(".ts") && !trimmed.startsWith("#")) {
                // FFmpeg may write absolute Windows or Unix paths: keep only the file name
                String filename = trimmed.substring(Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\')) + 1);
                sb.append("segments/").append(filename).append(query).append("\n");
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /** Issues a short-lived token that only unlocks this movie's playlist and segments. */
    public StreamToken issueStreamToken(UUID movieId, UserDetails user, int ageLimit) {
        ViewerAgeLimit.require(ageLimit, getMovie(movieId));   // 404 if missing or not allowed
        return jwtTokenProvider.generateStreamToken(user, movieId);
    }

    public Resource getSegment(UUID movieId, String segmentFilename) {
        TranscodeSession session = transcodeManager.getSession(movieId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active stream session for movie: " + movieId));
        session.touch();

        Path segmentPath = resolveSegment(session.getOutputDir(), segmentFilename);
        if (!Files.isRegularFile(segmentPath)) {
            throw new ResourceNotFoundException("Segment not found");
        }
        return new FileSystemResource(segmentPath);
    }

    /**
     * Only "seg<number>.ts" directly inside the session folder can be served; anything
     * else (other files, "..", absolute paths) is treated as not found.
     */
    static Path resolveSegment(Path outputDir, String filename) {
        if (filename == null || !SEGMENT_NAME.matcher(filename).matches()) {
            throw new ResourceNotFoundException("Segment not found");
        }
        Path base = outputDir.toAbsolutePath().normalize();
        Path resolved = base.resolve(filename).normalize();
        if (!base.equals(resolved.getParent())) {
            throw new ResourceNotFoundException("Segment not found");
        }
        return resolved;
    }

    public List<SubtitleFile> getSubtitles(UUID movieId, int ageLimit) {
        Movie movie = getMovie(movieId);
        ViewerAgeLimit.require(ageLimit, movie);
        if (movie.getFolderPath() == null) return List.of();
        return subtitleService.findSubtitles(movie.getFolderPath());
    }

    public Resource getSubtitleFile(UUID movieId, String filename, int ageLimit) {
        Movie movie = getMovie(movieId);
        ViewerAgeLimit.require(ageLimit, movie);
        if (movie.getFolderPath() == null)
            throw new ResourceNotFoundException("No folder path for movie: " + movieId);
        Path subtitlePath = subtitleService.findSubtitleFile(movie.getFolderPath(), filename)
                .orElseThrow(() -> new ResourceNotFoundException("Subtitle file not found"));
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
