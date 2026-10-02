package dev.ravitamil.tests;

import dev.ravitamil.framework.BaseTest;
import dev.ravitamil.pages.LoginPage;
import org.testng.annotations.Test;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class FailureEvidenceTest extends BaseTest {
    @Test(groups = "failure-demo", description = "Intentional failing assertion to demonstrate honest failure artifacts; excluded from normal CI")
    public void intentionalBalanceMismatch() {
        var dashboard = new LoginPage(session().page).signInAsDemoUser();
        session().report.info("Intentional failure: actual £2,500.00 is compared with incorrect £9,999.00. This command must exit nonzero.");
        assertThat(dashboard.balance()).hasText("£9,999.00");
    }
}
