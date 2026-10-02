package dev.ravitamil.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public final class LoginPage {
    private final Page page;
    public LoginPage(Page page) { this.page = page; }
    public void signIn(String email, String password) {
        page.getByLabel("Email address").fill(email);
        page.getByLabel("Password", new Page.GetByLabelOptions().setExact(true)).fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in").setExact(true)).click();
    }
    public DashboardPage signInAsDemoUser() {
        signIn("qa@example.test", "Demo123!");
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Hello, Alex."))).isVisible();
        return new DashboardPage(page);
    }
}
