package dev.ravitamil.tools;

import com.microsoft.playwright.*;
import java.nio.file.Path;

/** Generate a real screenshot of an existing Extent report; never fabricates test results. */
public final class CaptureReport {
    public static void main(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("Expected report HTML path and output PNG path");
        try (var playwright = Playwright.create()) {
            var options = new BrowserType.LaunchOptions().setHeadless(true);
            String executable = System.getProperty("browserExecutable", "");
            if (!executable.isBlank()) options.setExecutablePath(Path.of(executable));
            try (var browser = playwright.chromium().launch(options)) {
                var page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1440, 960));
                page.navigate(Path.of(args[0]).toAbsolutePath().toUri().toString());
                page.locator(".test-list-item > .test-item").filter(new Locator.FilterOptions().setHasText("transferUpdatesUiAndApi")).first().click();
                page.locator(".test-content h5.test-status").filter(new Locator.FilterOptions().setHasText("transferUpdatesUiAndApi")).first().waitFor();
                page.getByText("All assertions passed").filter(new Locator.FilterOptions().setVisible(true)).first().waitFor();
                page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(args[1])).setFullPage(true));
            }
        }
    }
}
