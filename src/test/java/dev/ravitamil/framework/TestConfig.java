package dev.ravitamil.framework;

import java.nio.file.Path;

public record TestConfig(String browser, boolean headless, String artifacts, Path artifactDir) {
    public static TestConfig load() {
        String browser = System.getProperty("browser", "chromium");
        String artifacts = System.getProperty("artifacts", "all");
        if (!java.util.Set.of("chromium", "firefox", "webkit").contains(browser)) throw new IllegalArgumentException("Unknown browser: " + browser);
        if (!java.util.Set.of("all", "failure").contains(artifacts)) throw new IllegalArgumentException("artifacts must be all or failure");
        return new TestConfig(browser, Boolean.parseBoolean(System.getProperty("headless", "true")), artifacts, Path.of(System.getProperty("artifactDir", "target/evidence")));
    }
    public boolean retain(boolean failed) { return artifacts.equals("all") || failed; }
}
