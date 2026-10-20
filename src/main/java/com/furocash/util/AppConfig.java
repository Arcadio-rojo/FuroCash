package com.furocash.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Local .env values are defaults; process environment and JVM properties win. */
public final class AppConfig {

  private static final Map<String, String> LOCAL = load();

  private AppConfig() {}

  private static Map<String, String> load() {
    Map<String, String> values = new HashMap<>();
    Path path = Path.of(".env");
    if (!Files.exists(path)) return values;
    try {
      for (String line : Files.readAllLines(path)) {
        line = line.replace("\uFEFF", "").trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        int separator = line.indexOf('=');
        if (separator < 1) continue;
        String value = line.substring(separator + 1).trim();
        if (
          value.length() >= 2 &&
          ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'")))
        ) value = value.substring(1, value.length() - 1);
        values.put(line.substring(0, separator).trim(), value);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not read .env", e);
    }
    return values;
  }

  public static String get(String name, String fallback) {
    String value = System.getProperty(name);
    if (value == null) value = System.getenv(name);
    if (value == null) value = LOCAL.get(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  public static boolean enabled(String name) {
    return Boolean.parseBoolean(get(name, "false"));
  }
}
