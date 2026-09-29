package com.example.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.it.pages.CaseListPage;
import com.example.application.it.pages.RoleSelector;
import com.vaadin.testbench.BrowserTest;

/** End-to-end: the case list in a real browser. The details are covered by CaseListViewTest. */
class CaseListIT extends AbstractIT {

    @BrowserTest
    void filteringFindsASeedCase() {
        open("");
        CaseListPage list = new CaseListPage(this);

        list.filterByTitle("Monitor upgrade");

        assertThat(list.rowCount()).isEqualTo(1);
        assertThat(list.statusOfRow(0)).isEqualTo("Approved");
    }

    @BrowserTest
    void newCaseButtonDisappearsForCaseHandler() {
        open("");
        CaseListPage list = new CaseListPage(this);
        assertThat(list.isNewCaseButtonVisible()).isTrue();

        new RoleSelector(this).actAs("Case handler");

        assertThat(list.isNewCaseButtonVisible()).isFalse();
    }
}
