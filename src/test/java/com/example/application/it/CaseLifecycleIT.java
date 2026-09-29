package com.example.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.it.pages.CaseListPage;
import com.example.application.it.pages.CasePage;
import com.example.application.it.pages.RoleSelector;
import com.vaadin.testbench.BrowserTest;
import java.time.LocalDate;

/**
 * End-to-end: the happy path through the whole workflow, as two users would click through it. The individual rules
 * are covered lower in the pyramid (CaseActionsTest, CaseViewTest).
 */
class CaseLifecycleIT extends AbstractIT {

    @BrowserTest
    void caseGoesFromDraftToApproved() {
        String title = uniqueTitle("E2E lifecycle");
        open("");
        CaseListPage list = new CaseListPage(this);

        CasePage newCase = list.clickNewCase();
        newCase.fill(title, "Travel", 5000, LocalDate.now().plusDays(10));
        newCase.submit();
        assertThat(firstNotificationText()).isEqualTo("Case submitted");

        new RoleSelector(this).actAs("Case handler");
        list.filterByTitle(title);
        assertThat(list.statusOfRow(0)).isEqualTo("Submitted");
        list.openRow(0).startReview();

        list.filterByTitle(title);
        CasePage inReview = list.openRow(0);
        assertThat(inReview.status()).isEqualTo("In review");
        inReview.approve();

        list.filterByTitle(title);
        assertThat(list.rowCount()).isEqualTo(1);
        assertThat(list.statusOfRow(0)).isEqualTo("Approved");
    }
}
