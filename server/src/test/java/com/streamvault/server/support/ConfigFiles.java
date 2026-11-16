package com.streamvault.server.support;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/** Reads the config files in the server folder (tests run with server/ as the working directory). */
public final class ConfigFiles {

    private ConfigFiles() {}

    public static Path path(String name) {
        Path p = Path.of(name);
        if (!Files.exists(p)) {
            throw new IllegalStateException("Run the tests from the server folder: cannot find " + p.toAbsolutePath());
        }
        return p;
    }

    public static String text(String name) throws IOException {
        return Files.readString(path(name), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> yaml(String name) throws IOException {
        try (Reader r = Files.newBufferedReader(path(name), StandardCharsets.UTF_8)) {
            return (Map<String, Object>) new Yaml().load(r);
        }
    }

    /**
     * Looks up a dotted key such as "spring.datasource.url"; null if any part is missing.
     * YAML keys that themselves contain dots (e.g. "logging.level.com.streamvault", where
     * "com.streamvault" is one key) are found by trying the longest key first.
     */
    public static Object get(Map<String, Object> yaml, String dottedKey) {
        return get(yaml, dottedKey.split("\\."), 0);
    }

    private static Object get(Object node, String[] parts, int from) {
        if (from == parts.length) return node;
        if (!(node instanceof Map<?, ?> map)) return null;
        for (int to = parts.length; to > from; to--) {
            String key = String.join(".", Arrays.copyOfRange(parts, from, to));
            if (map.containsKey(key)) {
                Object found = get(map.get(key), parts, to);
                if (found != null) return found;
            }
        }
        return null;
    }
}
