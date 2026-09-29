package com.example.application.views;

import com.example.application.domain.Case;
import com.example.application.domain.Role;
import com.example.application.service.CaseService;
import com.example.application.service.CurrentUser;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.notification.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base class for browserless UI tests. The views run with their real Spring beans, but without a browser or a
 * server, so the tests are fast. Every test starts from the seed data, acting as a submitter.
 */
@SpringBootTest
@ViewPackages(packages = "com.example.application.views")
public abstract class AbstractViewTest extends SpringBrowserlessTest {

    @Autowired
    protected CaseService caseService;

    @Autowired
    private CurrentUser currentUser;

    @BeforeEach
    protected void startFromKnownState() {
        caseService.reset();
        actAs(Role.SUBMITTER);
    }

    protected void actAs(Role role) {
        currentUser.setRole(role);
    }

    protected long caseIdByTitle(String title) {
        return caseService.findAll().stream()
                .filter(aCase -> aCase.getTitle().equals(title))
                .map(Case::getId)
                .findFirst()
                .orElseThrow();
    }

    protected String lastNotificationText() {
        return test($(Notification.class).last()).getText();
    }

    protected boolean isNotificationOpen() {
        return $(Notification.class).exists();
    }
}
