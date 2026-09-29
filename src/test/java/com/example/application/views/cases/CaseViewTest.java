package com.example.application.views.cases;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.example.application.domain.Case;
import com.example.application.domain.CaseAction;
import com.example.application.domain.CaseActions;
import com.example.application.domain.CaseCategory;
import com.example.application.domain.CaseStatus;
import com.example.application.domain.Role;
import com.example.application.views.AbstractViewTest;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Browserless tests for the case form. The workflow rules themselves are tested in CaseActionsTest; these tests
 * check that the form shows what the rules say, validates input and performs the actions.
 */
@DisplayName("Case form")
class CaseViewTest extends AbstractViewTest {

    /** Button id per action, so expectations can be derived from CaseActions instead of repeating the rules. */
    static final Map<CaseAction, String> BUTTON_IDS = Map.of(
            CaseAction.SAVE, "save",
            CaseAction.SUBMIT, "submit",
            CaseAction.DELETE, "delete",
            CaseAction.START_REVIEW, "start-review",
            CaseAction.APPROVE, "approve",
            CaseAction.REJECT, "reject");

    /** A seed case for every status. */
    static final Map<CaseStatus, String> SEED_CASE_BY_STATUS = Map.of(
            CaseStatus.DRAFT, "Laptop for new developer",
            CaseStatus.SUBMITTED, "Vaadin training course",
            CaseStatus.IN_REVIEW, "Customer visit in Bergen",
            CaseStatus.APPROVED, "Monitor upgrade",
            CaseStatus.REJECTED, "Team offsite travel");

    static final int SEED_SIZE = 8;

    @Nested
    @DisplayName("when the page is presented")
    class InitialState {

        @Test
        @DisplayName("a new case is empty, editable and can be saved or submitted")
        void newCase() {
            openNewCase();

            // 1. Right page
            assertThat(heading()).isEqualTo("New case");
            // 2. Content
            assertThat(titleField().getValue()).isEmpty();
            assertThat(categoryField().getValue()).isNull();
            assertThat(amountField().getValue()).isNull();
            assertThat(requestedDateField().getValue()).isNull();
            assertThat(descriptionField().getValue()).isEmpty();
            assertThat(urgentField().getValue()).isFalse();
            assertThat(statusText()).isEqualTo("Draft");
            // 3. Editability
            assertThat(formFields()).noneMatch(HasValue::isReadOnly);
            // 4. Actions (a new case cannot be deleted yet)
            assertThat(visibleActionButtons()).containsExactlyInAnyOrder("save", "submit");
            assertThat(isVisible("back")).isTrue();
            // 5. No errors at start
            assertThat(invalidFields()).isEmpty();
            assertThat(isNotificationOpen()).isFalse();
        }

        @Test
        @DisplayName("a draft shows its values and can be saved, submitted or deleted")
        void draftCase() {
            openCase("Laptop for new developer");

            assertThat(heading()).isEqualTo("Case " + caseIdByTitle("Laptop for new developer"));
            assertThat(titleField().getValue()).isEqualTo("Laptop for new developer");
            assertThat(categoryField().getValue()).isEqualTo(CaseCategory.PURCHASE);
            assertThat(amountField().getValue()).isEqualTo(18000);
            assertThat(requestedDateField().getValue()).isEqualTo(LocalDate.now().plusDays(14));
            assertThat(urgentField().getValue()).isFalse();
            assertThat(statusText()).isEqualTo("Draft");
            assertThat(formFields()).noneMatch(HasValue::isReadOnly);
            assertThat(visibleActionButtons()).containsExactlyInAnyOrder("save", "submit", "delete");
            assertThat(invalidFields()).isEmpty();
        }

        @Test
        @DisplayName("a submitted case is read-only for the submitter")
        void submittedCaseIsReadOnlyForSubmitter() {
            openCase("Vaadin training course");

            assertThat(statusText()).isEqualTo("Submitted");
            assertThat(formFields()).allMatch(HasValue::isReadOnly);
            assertThat(visibleActionButtons()).isEmpty();
            assertThat(isVisible("back")).isTrue();
        }

        @Test
        @DisplayName("a draft is read-only for a case handler")
        void draftIsReadOnlyForCaseHandler() {
            actAs(Role.CASE_HANDLER);
            openCase("Laptop for new developer");

            assertThat(formFields()).allMatch(HasValue::isReadOnly);
            assertThat(visibleActionButtons()).isEmpty();
        }
    }

    @Nested
    @DisplayName("visible buttons")
    class ButtonVisibility {

        @ParameterizedTest(name = "{1} viewing a {0} case")
        @MethodSource("com.example.application.views.cases.CaseViewTest#statusAndRole")
        @DisplayName("are exactly the actions the rules allow")
        void showsExactlyTheAllowedActions(CaseStatus status, Role role) {
            actAs(role);
            openCase(SEED_CASE_BY_STATUS.get(status));

            Set<String> expected = CaseActions.allowed(status, role).stream().map(BUTTON_IDS::get).collect(toSet());
            assertThat(visibleActionButtons()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @BeforeEach
        void openEmptyForm() {
            openNewCase();
        }

        @ParameterizedTest(name = "title \"{0}\"")
        @MethodSource("com.example.application.views.cases.CaseViewTest#invalidTitles")
        @DisplayName("rejects an invalid title")
        void rejectsInvalidTitle(String title, String expectedMessage) {
            fillValidForm();
            test(titleField()).setValue(title);

            click("save");

            assertInvalid(titleField(), expectedMessage);
        }

        @Test
        @DisplayName("requires a category")
        void requiresCategory() {
            fillForm("Printer for the office", null, 2500, LocalDate.now().plusDays(7));

            click("save");

            assertInvalid(categoryField(), "Category is required");
        }

        @Test
        @DisplayName("requires an amount")
        void requiresAmount() {
            fillForm("Printer for the office", "Purchase", null, LocalDate.now().plusDays(7));

            click("save");

            assertInvalid(amountField(), "Amount is required");
        }

        @ParameterizedTest(name = "amount {0}")
        @ValueSource(ints = {0, -5, 50001})
        @DisplayName("rejects an amount outside 1-50000")
        void rejectsAmountOutOfRange(int amount) {
            fillForm("Printer for the office", "Purchase", amount, LocalDate.now().plusDays(7));

            click("save");

            assertInvalid(amountField(), "Amount must be between 1 and 50000");
        }

        @ParameterizedTest(name = "amount {0}")
        @ValueSource(ints = {1, 50000})
        @DisplayName("accepts the amount boundaries")
        void acceptsAmountBoundaries(int amount) {
            fillForm("Printer for the office", "Purchase", amount, LocalDate.now().plusDays(7));

            click("save");

            assertThat(caseService.findAll()).hasSize(SEED_SIZE + 1);
        }

        @Test
        @DisplayName("requires a requested date")
        void requiresRequestedDate() {
            fillForm("Printer for the office", "Purchase", 2500, null);

            click("save");

            assertInvalid(requestedDateField(), "Requested date is required");
        }

        @Test
        @DisplayName("rejects a requested date in the past")
        void rejectsPastDate() {
            fillForm("Printer for the office", "Purchase", 2500, LocalDate.now().minusDays(1));

            click("save");

            assertInvalid(requestedDateField(), "Requested date cannot be in the past");
        }

        @Test
        @DisplayName("accepts today as requested date")
        void acceptsToday() {
            fillForm("Printer for the office", "Purchase", 2500, LocalDate.now());

            click("save");

            assertThat(caseService.findAll()).hasSize(SEED_SIZE + 1);
        }

        @Test
        @DisplayName("rejects a description over 1000 characters")
        void rejectsLongDescription() {
            fillValidForm();
            test(descriptionField()).setValue("x".repeat(1001));

            click("save");

            assertInvalid(descriptionField(), "Description must be at most 1000 characters");
        }

        private void assertInvalid(HasValidation field, String expectedMessage) {
            assertThat(field.isInvalid()).isTrue();
            assertThat(field.getErrorMessage()).isEqualTo(expectedMessage);
            assertThat(caseService.findAll()).hasSize(SEED_SIZE);
            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
        }
    }

    @Nested
    @DisplayName("saving and submitting")
    class Workflow {

        @Test
        @DisplayName("saving a new case stores it as a draft and returns to the list")
        void saveNewCase() {
            openNewCase();
            fillValidForm();
            test(titleField()).setValue("  Printer for the office  ");

            click("save");

            assertThat(lastNotificationText()).isEqualTo("Case saved");
            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            Case saved = caseService.findById(caseIdByTitle("Printer for the office")).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(CaseStatus.DRAFT);
            assertThat(saved.getAmount()).isEqualTo(2500);
        }

        @Test
        @DisplayName("saving an existing draft stores the changes")
        void saveExistingDraft() {
            openCase("Laptop for new developer");
            test(amountField()).setValue(19000);

            click("save");

            assertThat(caseService.findById(caseIdByTitle("Laptop for new developer")))
                    .get().extracting(Case::getAmount).isEqualTo(19000);
        }

        @Test
        @DisplayName("submitting a new case stores it as submitted")
        void submitNewCase() {
            openNewCase();
            fillValidForm();

            click("submit");

            assertThat(lastNotificationText()).isEqualTo("Case submitted");
            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            assertThat(caseService.findById(caseIdByTitle("Printer for the office")))
                    .get().extracting(Case::getStatus).isEqualTo(CaseStatus.SUBMITTED);
        }

        @Test
        @DisplayName("submitting an invalid form stores nothing")
        void submitInvalidForm() {
            openNewCase();

            click("submit");

            assertThat(titleField().isInvalid()).isTrue();
            assertThat(caseService.findAll()).hasSize(SEED_SIZE);
        }

        @Test
        @DisplayName("going back discards unsaved changes")
        void backDiscardsChanges() {
            openCase("Laptop for new developer");
            test(titleField()).setValue("Changed but not saved");

            click("back");

            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            assertThat(caseService.findById(caseIdByTitle("Laptop for new developer"))).isPresent();
        }

        @Test
        @DisplayName("a case handler can start the review of a submitted case")
        void startReview() {
            actAs(Role.CASE_HANDLER);
            openCase("Vaadin training course");

            click("start-review");

            assertThat(lastNotificationText()).isEqualTo("Review started");
            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            assertThat(statusOf("Vaadin training course")).isEqualTo(CaseStatus.IN_REVIEW);
        }

        @Test
        @DisplayName("a case handler can approve a case in review")
        void approve() {
            actAs(Role.CASE_HANDLER);
            openCase("Customer visit in Bergen");

            click("approve");

            assertThat(lastNotificationText()).isEqualTo("Case approved");
            assertThat(statusOf("Customer visit in Bergen")).isEqualTo(CaseStatus.APPROVED);
        }

        @Test
        @DisplayName("rejecting without a reason is blocked in the dialog")
        void rejectWithoutReasonIsBlocked() {
            actAs(Role.CASE_HANDLER);
            openCase("Security certification");

            click("reject");
            click("confirm-reject");

            TextArea reason = $(TextArea.class).id("rejection-reason");
            assertThat(reason.isInvalid()).isTrue();
            assertThat(reason.getErrorMessage()).isEqualTo("A reason is required");
            assertThat($(Dialog.class).withId("reject-dialog").exists()).isTrue();
            assertThat(statusOf("Security certification")).isEqualTo(CaseStatus.IN_REVIEW);
        }

        @Test
        @DisplayName("a reason of only spaces counts as no reason")
        void rejectWithBlankReasonIsBlocked() {
            actAs(Role.CASE_HANDLER);
            openCase("Security certification");

            click("reject");
            test($(TextArea.class).id("rejection-reason")).setValue("   ");
            click("confirm-reject");

            assertThat($(TextArea.class).id("rejection-reason").isInvalid()).isTrue();
            assertThat(statusOf("Security certification")).isEqualTo(CaseStatus.IN_REVIEW);
        }

        @Test
        @DisplayName("rejecting with a reason stores the reason")
        void rejectWithReason() {
            actAs(Role.CASE_HANDLER);
            openCase("Security certification");

            click("reject");
            test($(TextArea.class).id("rejection-reason")).setValue("Not in this year's budget");
            click("confirm-reject");

            assertThat(lastNotificationText()).isEqualTo("Case rejected");
            assertThat($(Dialog.class).withId("reject-dialog").exists()).isFalse();
            Case rejected = caseService.findById(caseIdByTitle("Security certification")).orElseThrow();
            assertThat(rejected.getStatus()).isEqualTo(CaseStatus.REJECTED);
            assertThat(rejected.getRejectionReason()).isEqualTo("Not in this year's budget");
        }

        @Test
        @DisplayName("cancelling the reject dialog changes nothing")
        void cancelReject() {
            actAs(Role.CASE_HANDLER);
            openCase("Security certification");

            click("reject");
            click("cancel-reject");

            assertThat($(Dialog.class).withId("reject-dialog").exists()).isFalse();
            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
            assertThat(statusOf("Security certification")).isEqualTo(CaseStatus.IN_REVIEW);
        }

        @Test
        @DisplayName("cancelling the delete confirmation keeps the case")
        void cancelDelete() {
            openCase("Laptop for new developer");

            click("delete");
            test($(ConfirmDialog.class).id("delete-confirm")).cancel();

            assertThat(caseService.findAll()).hasSize(SEED_SIZE);
            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
        }

        @Test
        @DisplayName("confirming the delete removes the case")
        void confirmDelete() {
            openCase("Laptop for new developer");

            click("delete");
            test($(ConfirmDialog.class).id("delete-confirm")).confirm();

            assertThat(lastNotificationText()).isEqualTo("Case deleted");
            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            assertThat(caseService.findAll()).hasSize(SEED_SIZE - 1);
        }
    }

    @Nested
    @DisplayName("an unknown case")
    class NotFound {

        @ParameterizedTest(name = "case/{0}")
        @ValueSource(strings = {"999", "abc", "-1"})
        @DisplayName("shows a notification and returns to the list")
        void redirectsToList(String caseId) {
            navigate("case/" + caseId, CaseListView.class);

            assertThat(lastNotificationText()).isEqualTo("Case not found");
        }
    }

    @Nested
    @DisplayName("when the case changed after the page was loaded")
    class StaleAction {

        @Test
        @DisplayName("the action is refused and the page shows the current state")
        void actionOnChangedCaseIsRefused() {
            actAs(Role.CASE_HANDLER);
            openCase("Customer visit in Bergen");
            // Someone else approves the case while this page is open.
            caseService.apply(caseIdByTitle("Customer visit in Bergen"), CaseAction.APPROVE, Role.CASE_HANDLER);

            click("approve");

            assertThat(lastNotificationText()).isEqualTo("This action is no longer allowed");
            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
            assertThat(statusText()).isEqualTo("Approved");
            assertThat(visibleActionButtons()).isEmpty();
        }
    }

    @Nested
    @DisplayName("switching role")
    class RoleSwitch {

        @Test
        @DisplayName("the selector starts at the current role")
        void selectorShowsCurrentRole() {
            actAs(Role.CASE_HANDLER);
            openCase("Customer visit in Bergen");

            assertThat(roleSelector().getValue()).isEqualTo(Role.CASE_HANDLER);
        }

        @Test
        @DisplayName("rebuilds the page with the new role's buttons")
        void switchingRoleRebuildsButtons() {
            openCase("Customer visit in Bergen");
            assertThat(visibleActionButtons()).isEmpty();

            test(roleSelector()).selectItem("Case handler");

            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
            assertThat(visibleActionButtons()).containsExactlyInAnyOrder("approve", "reject");
        }
    }

    static Stream<Arguments> statusAndRole() {
        return Stream.of(CaseStatus.values())
                .flatMap(status -> Stream.of(Role.values()).map(role -> arguments(status, role)));
    }

    static Stream<Arguments> invalidTitles() {
        return Stream.of(
                arguments("", "Title is required"),
                arguments("     ", "Title is required"),
                arguments("abcd", "Title must be 5-100 characters"),
                arguments("  abc  ", "Title must be 5-100 characters"),
                arguments("x".repeat(101), "Title must be 5-100 characters"));
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    CaseStatus statusOf(String title) {
        return caseService.findById(caseIdByTitle(title)).orElseThrow().getStatus();
    }

    void openNewCase() {
        navigate("case/new", CaseView.class);
    }

    void openCase(String title) {
        navigate("case/" + caseIdByTitle(title), CaseView.class);
    }

    void fillValidForm() {
        fillForm("Printer for the office", "Purchase", 2500, LocalDate.now().plusDays(7));
    }

    /** Fills the form of a new case; null values are left empty. */
    void fillForm(String title, String categoryLabel, Integer amount, LocalDate requestedDate) {
        if (title != null) {
            test(titleField()).setValue(title);
        }
        if (categoryLabel != null) {
            test(categoryField()).selectItem(categoryLabel);
        }
        if (amount != null) {
            test(amountField()).setValue(amount);
        }
        if (requestedDate != null) {
            test(requestedDateField()).setValue(requestedDate);
        }
    }

    void click(String buttonId) {
        test($(Button.class).id(buttonId)).click();
    }

    boolean isVisible(String componentId) {
        return $(Button.class).withId(componentId).exists();
    }

    /** Ids of the visible action buttons. Browserless queries only find visible components. */
    Set<String> visibleActionButtons() {
        return $(Button.class).all().stream()
                .map(button -> button.getId().orElse(""))
                .filter(BUTTON_IDS.values()::contains)
                .collect(toSet());
    }

    String heading() {
        return $(H2.class).id("case-heading").getText();
    }

    String statusText() {
        return $(Span.class).id("status").getText();
    }

    List<AbstractField<?, ?>> formFields() {
        return List.of(titleField(), categoryField(), amountField(), requestedDateField(), descriptionField(),
                urgentField());
    }

    List<HasValidation> invalidFields() {
        return Stream.<HasValidation>of(titleField(), categoryField(), amountField(), requestedDateField(),
                descriptionField()).filter(HasValidation::isInvalid).toList();
    }

    TextField titleField() {
        return $(TextField.class).id("title");
    }

    @SuppressWarnings("unchecked")
    ComboBox<CaseCategory> categoryField() {
        return $(ComboBox.class).id("category");
    }

    IntegerField amountField() {
        return $(IntegerField.class).id("amount");
    }

    DatePicker requestedDateField() {
        return $(DatePicker.class).id("requested-date");
    }

    TextArea descriptionField() {
        return $(TextArea.class).id("description");
    }

    Checkbox urgentField() {
        return $(Checkbox.class).id("urgent");
    }

    @SuppressWarnings("unchecked")
    Select<Role> roleSelector() {
        return $(Select.class).id("role-selector");
    }
}
