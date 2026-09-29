package com.example.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.it.pages.CaseListPage;
import com.example.application.it.pages.CasePage;
import com.example.application.it.pages.RoleSelector;
import com.vaadin.testbench.BrowserTest;
import java.time.LocalDate;

/** End-to-end: rejecting needs a reason, and the dialog enforces it in the browser. */
class CaseRejectIT extends AbstractIT {

    @BrowserTest
    void rejectingRequiresAReason() {
        String title = uniqueTitle("E2E reject");
        CaseListPage list = new CaseListPage(this);
        CasePage inReview = createCaseInReview(list, title);

        inReview.reject();
        inReview.confirmReject(null);

        assertThat(inReview.isRejectDialogOpen()).isTrue();
        assertThat(inReview.rejectionReasonErrorMessage()).isEqualTo("A reason is required");

        inReview.confirmReject("Not in this year's budget");

        list.filterByTitle(title);
        assertThat(list.statusOfRow(0)).isEqualTo("Rejected");
    }

    /** Creates and submits a case as submitter, then starts its review as case handler and opens it. */
    private CasePage createCaseInReview(CaseListPage list, String title) {
        open("");
        CasePage newCase = list.clickNewCase();
        newCase.fill(title, "Training", 8000, LocalDate.now().plusDays(20));
        newCase.submit();

        new RoleSelector(this).actAs("Case handler");
        list.filterByTitle(title);
        list.openRow(0).startReview();

        list.filterByTitle(title);
        return list.openRow(0);
    }
}
