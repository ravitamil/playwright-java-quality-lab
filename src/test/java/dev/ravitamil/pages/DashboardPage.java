package dev.ravitamil.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public final class DashboardPage {
    private final Page page;
    public DashboardPage(Page page) { this.page = page; }
    public Locator balance() { return page.getByTestId("balance"); }
    public Locator status() { return page.locator("#transfer-status"); }
    public void transfer(String recipient, String amount) {
        page.getByLabel("Recipient", new Page.GetByLabelOptions().setExact(true)).selectOption(recipient);
        page.getByLabel("Amount (GBP)").fill(amount);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send transfer")).click();
    }
    public void signOut() { page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign out")).click(); }
}
