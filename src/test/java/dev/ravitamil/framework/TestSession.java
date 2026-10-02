package dev.ravitamil.framework;

import com.aventstack.extentreports.ExtentTest;
import com.microsoft.playwright.*;
import dev.ravitamil.demo.BankingDemo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Created, used and closed on the same TestNG worker. No shared Playwright objects. */
public final class TestSession implements AutoCloseable {
    public final BankingDemo app;
    public final Playwright playwright;
    public final Browser browser;
    public final BrowserContext context;
    public final Page page;
    public final ExtentTest report;
    public final Path directory;
    public final List<String> console = new ArrayList<>();
    public final List<String> network = new ArrayList<>();
    private boolean contextClosed;

    public TestSession(TestConfig config, Path directory, ExtentTest report) throws IOException {
        this.directory = directory;
        this.report = report;
        Files.createDirectories(directory);
        app = new BankingDemo();
        playwright = Playwright.create();
        browser = switch (config.browser()) {
            case "firefox" -> playwright.firefox().launch(new BrowserType.LaunchOptions().setHeadless(config.headless()));
            case "webkit" -> playwright.webkit().launch(new BrowserType.LaunchOptions().setHeadless(config.headless()));
            default -> playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(config.headless()));
        };
        context = browser.newContext(new Browser.NewContextOptions().setBaseURL(app.baseUrl()).setViewportSize(1366, 900).setLocale("en-GB")
                .setRecordVideoDir(directory.resolve("video")).setRecordVideoSize(1280, 720));
        context.setDefaultTimeout(7000);
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
        page = context.newPage();
        page.onConsoleMessage(message -> console.add(message.type() + ": " + message.text()));
        page.onPageError(error -> console.add("pageerror: " + error));
        page.onRequestFailed(request -> network.add("FAILED " + request.method() + " " + request.url() + " " + request.failure()));
        page.onResponse(response -> { if (response.url().contains("/api/")) network.add(response.status() + " " + response.request().method() + " " + response.url()); });
        page.navigate("/");
    }
    public void step(String description, Runnable action) { report.info(description); action.run(); }
    public void closeContext() { if (!contextClosed) { context.close(); contextClosed = true; } }
    @Override public void close() {
        try { closeContext(); }
        finally { try { browser.close(); } finally { try { playwright.close(); } finally { app.close(); } } }
    }
}
