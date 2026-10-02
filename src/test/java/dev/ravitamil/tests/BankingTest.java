package dev.ravitamil.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import dev.ravitamil.framework.BaseTest;
import dev.ravitamil.pages.*;
import java.nio.file.Files;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.testng.Assert.*;

public class BankingTest extends BaseTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test(groups = {"ui", "smoke"}, description = "Sign in and transfer money; verify UI, API balance and transaction record")
    public void transferUpdatesUiAndApi() throws Exception {
        var s = session();
        var dashboard = new LoginPage(s.page).signInAsDemoUser();
        s.step("Send £125.50 to Jamie Lee", () -> dashboard.transfer("Jamie Lee", "125.50"));
        assertThat(dashboard.status()).hasText("Transfer completed");
        assertThat(dashboard.balance()).hasText("£2,374.50");
        var response = s.context.request().get("/api/account");
        assertEquals(response.status(), 200);
        var account = JSON.readTree(response.body());
        assertEquals(account.path("balance").decimalValue().toPlainString(), "2374.50");
        assertEquals(account.path("transactions").get(0).path("recipient").asText(), "Jamie Lee");
    }
    @DataProvider(name = "invalidCredentials", parallel = true)
    public Object[][] invalidCredentials() { return new Object[][] {{"qa@example.test", "wrong-password"}, {"unknown@example.test", "Demo123!"}}; }
    @Test(groups = "ui", dataProvider = "invalidCredentials", description = "Reject invalid credentials without exposing account data")
    public void invalidCredentialsShowAccessibleError(String email, String password) {
        var page = session().page;
        new LoginPage(page).signIn(email, password);
        assertThat(page.getByRole(AriaRole.ALERT)).hasText("Invalid demo credentials");
        assertThat(page.getByTestId("balance")).isHidden();
    }
    @Test(groups = "ui", description = "Insufficient funds preserves balance and creates no transaction")
    public void insufficientFundsDoesNotChangeBalance() throws Exception {
        var s = session();
        var dashboard = new LoginPage(s.page).signInAsDemoUser();
        dashboard.transfer("Sam Patel", "3000");
        assertThat(dashboard.status()).hasText("Insufficient funds");
        assertThat(dashboard.balance()).hasText("£2,500.00");
        assertEquals(JSON.readTree(s.context.request().get("/api/account").body()).path("transactions").size(), 0);
    }
    @Test(groups = {"api", "smoke"}, description = "Protected account endpoint rejects anonymous requests")
    public void apiRejectsAnonymousAccountAccess() {
        var response = session().context.request().get("/api/account");
        assertEquals(response.status(), 401);
        assertTrue(response.text().contains("Sign in required"));
    }
    @Test(groups = "api", description = "API idempotency prevents double debit and rejects key reuse with changed payload")
    public void transferApiIsIdempotent() throws Exception {
        var api = session().context.request();
        assertEquals(api.post("/api/login", RequestOptions.create().setData(Map.of("email", "qa@example.test", "password", "Demo123!"))).status(), 200);
        var options = RequestOptions.create().setHeader("Idempotency-Key", "repeat-test").setData(Map.of("recipient", "Jamie Lee", "amount", "100.00"));
        assertEquals(api.post("/api/transfer", options).status(), 201);
        assertEquals(api.post("/api/transfer", options).status(), 200);
        assertEquals(api.post("/api/transfer", RequestOptions.create().setHeader("Idempotency-Key", "repeat-test").setData(Map.of("recipient", "Jamie Lee", "amount", "200"))).status(), 409);
        var account = JSON.readTree(api.get("/api/account").body());
        assertEquals(account.path("balance").asDouble(), 2400.0);
        assertEquals(account.path("transactions").size(), 1);
    }
    @Test(groups = "api", description = "Reject precision loss and nonpositive API amounts")
    public void apiValidatesCurrencyPrecision() throws Exception {
        var api = session().context.request();
        api.post("/api/login", RequestOptions.create().setData(Map.of("email", "qa@example.test", "password", "Demo123!")));
        for (String amount : new String[] {"1.001", "0", "-10", "NaN"}) {
            assertEquals(api.post("/api/transfer", RequestOptions.create().setHeader("Idempotency-Key", "invalid-" + amount).setData(Map.of("recipient", "Sam Patel", "amount", amount))).status(), 422, amount);
        }
        assertEquals(JSON.readTree(api.get("/api/account").body()).path("balance").asDouble(), 2500.0);
    }
    @Test(groups = {"ui", "network"}, description = "Mock a service outage; prove failure messaging and recovery using real API")
    public void mockedOutageRecoversWithoutDebit() {
        var s = session();
        var dashboard = new LoginPage(s.page).signInAsDemoUser();
        s.page.route("**/api/transfer", route -> route.fulfill(new Route.FulfillOptions().setStatus(503).setContentType("application/json").setBody("{\"error\":\"Service unavailable. Try again.\"}")));
        dashboard.transfer("Sam Patel", "50");
        assertThat(dashboard.status()).hasText("Service unavailable. Try again.");
        assertThat(dashboard.balance()).hasText("£2,500.00");
        s.page.unroute("**/api/transfer");
        dashboard.transfer("Sam Patel", "50");
        assertThat(dashboard.status()).hasText("Transfer completed");
        assertThat(dashboard.balance()).hasText("£2,450.00");
    }
    @Test(groups = {"ui", "auth"}, description = "Reuse cookie storage state in a new context without repeating login")
    public void authenticationStorageStateCanBeReused() {
        var s = session();
        new LoginPage(s.page).signInAsDemoUser();
        String state = s.context.storageState(); // In memory only: never publish real authentication state.
        try (var reused = s.browser.newContext(new Browser.NewContextOptions().setBaseURL(s.app.baseUrl()).setStorageState(state))) {
            var page = reused.newPage();
            page.navigate("/");
            assertThat(page.getByTestId("balance")).hasText("£2,500.00");
            assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in").setExact(true))).isHidden();
        }
    }
    @Test(groups = {"ui", "auth"}, description = "Logout invalidates server session, not just the visible UI")
    public void logoutRevokesApiSession() {
        var s = session();
        new LoginPage(s.page).signInAsDemoUser().signOut();
        assertThat(s.page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in").setExact(true))).isVisible();
        assertEquals(s.context.request().get("/api/account").status(), 401);
    }
    @Test(groups = {"ui", "files"}, description = "Download actual CSV and verify transfer contents; preview a synthetic uploaded receipt")
    public void statementDownloadAndReceiptUpload() throws Exception {
        var s = session();
        var dashboard = new LoginPage(s.page).signInAsDemoUser();
        dashboard.transfer("Jamie Lee", "42.25");
        assertThat(dashboard.status()).hasText("Transfer completed");
        Download download = s.page.waitForDownload(() -> s.page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Download statement")).click());
        var csv = s.directory.resolve("statement.csv");
        download.saveAs(csv);
        assertEquals(download.suggestedFilename(), "statement.csv");
        assertTrue(Files.readString(csv).contains("Jamie Lee,42.25,TX-"));
        s.page.getByLabel("Attach a receipt (local preview)").setInputFiles(new FilePayload("receipt.txt", "text/plain", "Synthetic receipt only".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(s.page.locator("#upload-status")).hasText("Receipt attached: receipt.txt");
    }
    @Test(groups = {"ui", "responsive"}, description = "Mobile viewport has no horizontal overflow and supports transfers")
    public void mobileLayoutSupportsTransfer() {
        var s = session();
        s.page.setViewportSize(390, 844);
        var dashboard = new LoginPage(s.page).signInAsDemoUser();
        dashboard.transfer("Sam Patel", "25");
        assertThat(dashboard.status()).hasText("Transfer completed");
        assertThat(dashboard.balance()).hasText("£2,475.00");
        assertEquals(s.page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), true);
    }
    @Test(groups = {"ui", "accessibility"}, description = "Keyboard-only sign-in uses correctly labelled inputs and focus order")
    public void keyboardOnlyLogin() {
        var page = session().page;
        page.getByLabel("Email address").focus();
        page.keyboard().type("qa@example.test");
        page.keyboard().press("Tab");
        assertThat(page.getByLabel("Password", new Page.GetByLabelOptions().setExact(true))).isFocused();
        page.keyboard().type("Demo123!");
        page.keyboard().press("Tab");
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in").setExact(true))).isFocused();
        page.keyboard().press("Enter");
        assertThat(page.getByTestId("balance")).hasText("£2,500.00");
    }
}
