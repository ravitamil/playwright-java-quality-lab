package dev.ravitamil.framework;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import java.nio.file.Path;

public final class ReportManager {
    private static ExtentReports report;
    private ReportManager() { }
    public static synchronized void start(TestConfig config) {
        report = new ExtentReports();
        ExtentSparkReporter spark = new ExtentSparkReporter(config.artifactDir().resolve("extent-report.html").toString());
        spark.config().setDocumentTitle("Playwright Java Quality Lab");
        spark.config().setReportName("Ravikumar Tamilmani · UI & API evidence");
        spark.config().setTheme(Theme.STANDARD);
        report.attachReporter(spark);
        report.setSystemInfo("Browser", config.browser());
        report.setSystemInfo("Java", System.getProperty("java.version"));
        report.setSystemInfo("Artifact policy", config.artifacts());
        report.setSystemInfo("Application", "Local synthetic banking fixture");
    }
    public static synchronized ExtentTest test(String name, String description) { return report.createTest(name, description); }
    public static synchronized void flush() { if (report != null) report.flush(); }
    public static String relative(Path root, Path file) { return root.toAbsolutePath().relativize(file.toAbsolutePath()).toString().replace('\\', '/'); }
}
