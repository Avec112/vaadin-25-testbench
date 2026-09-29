package com.example.application.it.pages;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.component.datepicker.testbench.DatePickerElement;
import com.vaadin.flow.component.dialog.testbench.DialogElement;
import com.vaadin.flow.component.html.testbench.SpanElement;
import com.vaadin.flow.component.textfield.testbench.IntegerFieldElement;
import com.vaadin.flow.component.textfield.testbench.TextAreaElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.testbench.HasElementQuery;
import com.vaadin.testbench.TestBenchElement;
import java.time.LocalDate;

/** Page object for the case form, including its reject dialog. */
public class CasePage {

    private final HasElementQuery page;

    public CasePage(HasElementQuery page) {
        this.page = page;
    }

    public void fill(String title, String categoryLabel, int amount, LocalDate requestedDate) {
        setTitle(title);
        page.$(ComboBoxElement.class).id("category").selectByText(categoryLabel);
        page.$(IntegerFieldElement.class).id("amount").setValue(String.valueOf(amount));
        page.$(DatePickerElement.class).id("requested-date").setDate(requestedDate);
    }

    public void setTitle(String title) {
        page.$(TextFieldElement.class).id("title").setValue(title);
    }

    public String status() {
        return page.$(SpanElement.class).id("status").getText();
    }

    public String titleErrorMessage() {
        return errorMessageOf(page.$(TextFieldElement.class).id("title"));
    }

    public void save() {
        click("save");
    }

    public void submit() {
        click("submit");
    }

    public void startReview() {
        click("start-review");
    }

    public void approve() {
        click("approve");
    }

    public void reject() {
        click("reject");
    }

    /** Confirms the reject dialog, optionally typing a reason first. */
    public void confirmReject(String reasonOrNull) {
        if (reasonOrNull != null) {
            page.$(TextAreaElement.class).id("rejection-reason").setValue(reasonOrNull);
        }
        click("confirm-reject");
    }

    public boolean isRejectDialogOpen() {
        return page.$(DialogElement.class).withId("reject-dialog").all().stream().anyMatch(DialogElement::isOpen);
    }

    public String rejectionReasonErrorMessage() {
        return errorMessageOf(page.$(TextAreaElement.class).id("rejection-reason"));
    }

    private void click(String buttonId) {
        page.$(ButtonElement.class).id(buttonId).click();
    }

    /** Vaadin fields expose their validation state as the "invalid" and "errorMessage" properties. */
    private static String errorMessageOf(TestBenchElement field) {
        return Boolean.TRUE.equals(field.getPropertyBoolean("invalid")) ? field.getPropertyString("errorMessage") : "";
    }
}
