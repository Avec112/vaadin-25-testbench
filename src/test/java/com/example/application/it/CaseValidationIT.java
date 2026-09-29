package com.example.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.it.pages.CasePage;
import com.vaadin.testbench.BrowserTest;
import java.time.LocalDate;

/**
 * End-to-end: one validation rule, to prove error messages are actually rendered in the browser. Every rule is
 * covered in CaseViewTest.Validation.
 */
class CaseValidationIT extends AbstractIT {

    @BrowserTest
    void tooShortTitleShowsAnErrorInTheBrowser() {
        open("case/new");
        CasePage page = new CasePage(this);

        page.fill("abc", "Purchase", 1000, LocalDate.now().plusDays(3));
        page.save();

        assertThat(page.titleErrorMessage()).isEqualTo("Title must be 5-100 characters");
    }
}
