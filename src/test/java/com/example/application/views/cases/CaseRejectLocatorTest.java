package com.example.application.views.cases;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.domain.Case;
import com.example.application.domain.CaseStatus;
import com.example.application.domain.Role;
import com.example.application.service.CaseService;
import com.example.application.service.CurrentUser;
import com.vaadin.browserless.BrowserlessApplicationContext;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SpringBrowserlessApplicationContext;
import com.vaadin.flow.component.dialog.DialogLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * The test {@code CaseViewTest.Workflow#rejectWithReason}, rewritten with the locator API that browserless-test added
 * in 1.1 (beta in Vaadin 25.2). Read the two side by side; they check exactly the same thing.
 *
 * <p>What is different from the other browserless tests:
 * <ul>
 *   <li><b>No base class.</b> The test does not extend {@code SpringBrowserlessTest}. It creates the browserless
 *       application from the Spring context itself and closes it after each test. {@code app.newUser()} is one user
 *       session, {@code newWindow()} is one browser tab of that user. Several users or tabs can be opened in the same
 *       test, which the base-class style cannot do.</li>
 *   <li><b>Find and act in one step.</b> A locator such as {@code window.findButton()} both finds the component and
 *       acts on it: {@code window.findButton().withText("Reject").click()} replaces
 *       {@code test($(Button.class).id("reject")).click()}, so the {@code click(id)} helper is not needed.</li>
 *   <li><b>Find by what the user sees.</b> Components are found by button text and field label, like a user would,
 *       instead of by ids that exist only for tests. Ids still work: {@code withId("reject")}.</li>
 *   <li><b>Scoping.</b> {@code inside(dialog)} limits a search to the dialog. The page and the dialog both have a
 *       "Reject" button, so without it the search would be ambiguous and fail.</li>
 * </ul>
 *
 * <p>A locator remembers the component it found the first time it is used; create a new locator (or call
 * {@code invalidate()}) after the page has changed. The {@code $(...)}/{@code test(...)} API used by the other tests is
 * not deprecated and keeps working.
 */
@SpringBootTest
@DisplayName("Case form, written with locators")
class CaseRejectLocatorTest {

    @Autowired
    ApplicationContext springContext;

    @Autowired
    CaseService caseService;

    @Autowired
    CurrentUser currentUser;

    BrowserlessApplicationContext app;
    BrowserlessUIContext window;

    // What SpringBrowserlessTest and AbstractViewTest do for the other tests.
    @BeforeEach
    void openWindow() {
        caseService.reset();
        app = SpringBrowserlessApplicationContext.create(springContext, "com.example.application.views");
        window = app.newUser().newWindow();
    }

    @AfterEach
    void closeApplication() {
        app.close();
    }

    @Test
    @DisplayName("rejecting with a reason stores the reason")
    void rejectWithReason() {
        currentUser.setRole(Role.CASE_HANDLER);
        window.navigate("case/" + caseIdByTitle("Security certification"), CaseView.class);

        // Old: click("reject"), which is test($(Button.class).id("reject")).click()
        window.findButton().withText("Reject").click();
        // Old: test($(TextArea.class).id("rejection-reason")).setValue(...)
        DialogLocator dialog = window.findDialog();
        window.findTextArea().withLabel("Reason").inside(dialog).setValue("Not in this year's budget");
        // Old: click("confirm-reject"). The dialog's button has the same text as the page's, hence inside(dialog).
        window.findButton().withText("Reject").inside(dialog).click();

        // Old: test($(Notification.class).last()).getText()
        assertThat(window.findNotification().getText()).isEqualTo("Case rejected");
        // Old: $(Dialog.class).withId("reject-dialog").exists(). A new locator, because the dialog has closed.
        assertThat(window.findDialog().exists()).isFalse();
        Case rejected = caseService.findById(caseIdByTitle("Security certification")).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(CaseStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Not in this year's budget");
    }

    long caseIdByTitle(String title) {
        return caseService.findAll().stream()
                .filter(aCase -> aCase.getTitle().equals(title))
                .map(Case::getId)
                .findFirst()
                .orElseThrow();
    }
}
