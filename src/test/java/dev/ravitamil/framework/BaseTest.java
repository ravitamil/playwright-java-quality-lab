package dev.ravitamil.framework;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.aventstack.extentreports.MediaEntityBuilder;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.testng.ITestResult;
import org.testng.annotations.*;

public abstract class BaseTest {
    private final ThreadLocal<TestSession> sessions = new ThreadLocal<>();
    protected final TestConfig config = TestConfig.load();
    protected TestSession session() { return sessions.get(); }

    @BeforeSuite(alwaysRun = true) public void startReport() throws Exception {
        Files.createDirectories(config.artifactDir());
        ReportManager.start(config);
    }
    @BeforeMethod(alwaysRun = true) public void startSession(Method method) throws Exception {
        String id = method.getName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        var test = method.getAnnotation(Test.class);
        var report = ReportManager.test(method.getName(), test == null ? "" : test.description());
        report.assignAuthor("Ravikumar Tamilmani").assignDevice(config.browser());
        if (test != null) report.assignCategory(test.groups());
        sessions.set(new TestSession(config, config.artifactDir().resolve(id), report));
    }
    @AfterMethod(alwaysRun = true) public void captureEvidence(ITestResult result) throws Exception {
        TestSession s = session();
        if (s == null) return;
        boolean failed = result.getStatus() == ITestResult.FAILURE;
        Exception artifactError = null;
        try {
            Path screenshot = s.directory.resolve("screenshot.png");
            Path trace = s.directory.resolve("trace.zip");
            s.page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(true));
            s.context.tracing().stop(new Tracing.StopOptions().setPath(trace));
            s.closeContext(); // Video is finalized only after context closes.
            Path video = s.page.video().path();
            Files.write(s.directory.resolve("console.log"), s.console);
            Files.write(s.directory.resolve("network.log"), s.network);
            Files.writeString(s.directory.resolve("result.json"), "{\"test\":\"" + result.getMethod().getMethodName() + "\",\"status\":\"" + (failed ? "FAIL" : result.getStatus() == ITestResult.SKIP ? "SKIP" : "PASS") + "\",\"browser\":\"" + config.browser() + "\"}");
            if (failed) s.report.fail(result.getThrowable());
            else if (result.getStatus() == ITestResult.SKIP) s.report.skip("Test skipped");
            else s.report.pass("All assertions passed");
            if (config.retain(failed)) {
                s.report.info("Final browser state", MediaEntityBuilder.createScreenCaptureFromPath(ReportManager.relative(config.artifactDir(), screenshot)).build());
                s.report.info("<a href='" + ReportManager.relative(config.artifactDir(), trace) + "'>Download Playwright trace</a> · <a href='" + ReportManager.relative(config.artifactDir(), video) + "'>Watch recording</a>");
                s.report.info("<a href='" + ReportManager.relative(config.artifactDir(), s.directory.resolve("console.log")) + "'>Console log</a> · <a href='" + ReportManager.relative(config.artifactDir(), s.directory.resolve("network.log")) + "'>Network log</a>");
            } else {
                try (var paths = Files.walk(s.directory)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
            }
        } catch (Exception exception) {
            artifactError = exception;
            s.report.warning("Evidence capture failed: " + exception.getMessage());
            if (failed) s.report.fail(result.getThrowable());
        } finally {
            try { s.close(); } finally { sessions.remove(); }
        }
        if (artifactError != null) throw artifactError; // Do not silently report successful capture.
    }
    @AfterSuite(alwaysRun = true) public void finishReport() { ReportManager.flush(); }
}
