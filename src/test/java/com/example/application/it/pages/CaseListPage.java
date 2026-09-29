package com.example.application.it.pages;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.grid.testbench.GridElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.testbench.HasElementQuery;

/**
 * Page object for the case list. Tests talk to this class in terms of what a user does; only this class knows ids
 * and column positions.
 */
public class CaseListPage {

    private static final int TITLE_COLUMN = 0;
    private static final int STATUS_COLUMN = 4;

    private final HasElementQuery page;

    public CaseListPage(HasElementQuery page) {
        this.page = page;
    }

    public void filterByTitle(String text) {
        page.$(TextFieldElement.class).id("title-filter").setValue(text);
    }

    public int rowCount() {
        return grid().getRowCount();
    }

    public String titleOfRow(int row) {
        return grid().getCell(row, TITLE_COLUMN).getText();
    }

    public String statusOfRow(int row) {
        return grid().getCell(row, STATUS_COLUMN).getText();
    }

    public boolean isNewCaseButtonVisible() {
        // Unlike browserless queries, the browser's DOM still contains components hidden with setVisible(false),
        // so check whether the button is actually displayed.
        return page.$(ButtonElement.class).withId("new-case").all().stream().anyMatch(ButtonElement::isDisplayed);
    }

    public CasePage clickNewCase() {
        page.$(ButtonElement.class).id("new-case").click();
        return new CasePage(page);
    }

    public CasePage openRow(int row) {
        grid().getCell(row, TITLE_COLUMN).click();
        return new CasePage(page);
    }

    private GridElement grid() {
        return page.$(GridElement.class).id("cases-grid");
    }
}
