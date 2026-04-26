package com.streamvault.server.streaming;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.server.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FfprobeService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public record ProbeResult(String videoCodec, String audioCodec, boolean isH264) {}

    public ProbeResult probe(String filePath) {
        // Derive ffprobe from ffmpeg path — replace only the filename, not the directory
        String ffmpegPath = appProperties.media().ffmpegPath();
        Path ffmpegFile = Path.of(ffmpegPath);
        String ffprobeFilename = ffmpegFile.getFileName().toString()
                .replace("ffmpeg.exe", "ffprobe.exe")
                .replace("ffmpeg", "ffprobe");
        String ffprobePath = ffmpegFile.getParent() != null
                ? ffmpegFile.getParent().resolve(ffprobeFilename).toString()
                : ffprobeFilename;

        log.debug("Using ffprobe at: {}", ffprobePath);

        try {
            // ProcessBuilder handles path quoting automatically — do NOT wrap in quotes
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobePath,
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_streams",
                    "-select_streams", "v:0",
                    filePath   // ProcessBuilder handles spaces correctly
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            FfprobeOutput result = objectMapper.readValue(output, FfprobeOutput.class);

            if (result.streams() != null && !result.streams().isEmpty()) {
                String codec = result.streams().get(0).codecName();
                boolean isH264 = "h264".equalsIgnoreCase(codec);
                log.debug("Probed '{}': videoCodec={}, isH264={}", filePath, codec, isH264);
                return new ProbeResult(codec, null, isH264);
            }
        } catch (IOException | InterruptedException e) {
            log.warn("ffprobe failed for '{}': {} — defaulting to re-encode", filePath, e.getMessage());
        }

        return new ProbeResult("unknown", "unknown", false);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FfprobeOutput(List<StreamInfo> streams) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StreamInfo(
            @JsonProperty("codec_name") String codecName,
            @JsonProperty("codec_type") String codecType
    ) {}
}