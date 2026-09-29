package com.example.application.views.cases;

import com.example.application.domain.Case;
import com.example.application.domain.CaseAction;
import com.example.application.domain.CaseActions;
import com.example.application.domain.CaseCategory;
import com.example.application.domain.Role;
import com.example.application.service.CaseService;
import com.example.application.service.CurrentUser;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.IntegerRangeValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Shows one case. Which fields are editable and which buttons are visible is decided by {@link CaseActions} for the
 * case's status and the current role.
 */
@Route("case/:caseId")
public class CaseView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private static final String NEW = "new";

    private final CaseService caseService;
    private final CurrentUser currentUser;

    private final H2 heading = new H2();
    private final Span status = new Span();
    private final TextField title = new TextField("Title");
    private final ComboBox<CaseCategory> category = new ComboBox<>("Category");
    private final IntegerField amount = new IntegerField("Amount");
    private final DatePicker requestedDate = new DatePicker("Requested date");
    private final TextArea description = new TextArea("Description");
    private final Checkbox urgent = new Checkbox("Urgent");

    private final Button save = new Button("Save", event -> save());
    private final Button submit = new Button("Submit", event -> submit());
    private final Button delete = new Button("Delete", event -> confirmDelete());
    private final Button startReview = new Button("Start review", event -> startReview());
    private final Button approve = new Button("Approve", event -> approve());
    private final Button reject = new Button("Reject", event -> openRejectDialog());
    private final Button back = new Button("Back", event -> backToList());

    private final Binder<Case> binder = new Binder<>();
    private Case current;

    public CaseView(CaseService caseService, CurrentUser currentUser) {
        this.caseService = caseService;
        this.currentUser = currentUser;

        heading.setId("case-heading");
        status.setId("status");
        title.setId("title");
        category.setId("category");
        category.setItems(CaseCategory.values());
        category.setItemLabelGenerator(CaseCategory::getLabel);
        amount.setId("amount");
        requestedDate.setId("requested-date");
        description.setId("description");
        urgent.setId("urgent");

        save.setId("save");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setId("submit");
        delete.setId("delete");
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        startReview.setId("start-review");
        startReview.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        approve.setId("approve");
        approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        reject.setId("reject");
        reject.addThemeVariants(ButtonVariant.LUMO_ERROR);
        back.setId("back");

        bindFields();

        FormLayout form = new FormLayout(title, category, amount, requestedDate, description, urgent);
        form.setColspan(description, 2);
        HorizontalLayout actions = new HorizontalLayout(save, submit, delete, startReview, approve, reject, back);
        add(heading, status, form, actions);
    }

    private void bindFields() {
        // asRequired() comes first, so an empty field gets exactly one, predictable message.
        binder.forField(title)
                .asRequired("Title is required")
                .withValidator(value -> !value.isBlank(), "Title is required")
                .withValidator(value -> value.strip().length() >= 5 && value.strip().length() <= 100,
                        "Title must be 5-100 characters")
                .bind(Case::getTitle, (aCase, value) -> aCase.setTitle(value.strip()));
        binder.forField(category)
                .asRequired("Category is required")
                .bind(Case::getCategory, Case::setCategory);
        binder.forField(amount)
                .asRequired("Amount is required")
                .withValidator(new IntegerRangeValidator("Amount must be between 1 and 50000", 1, 50000))
                .bind(Case::getAmount, Case::setAmount);
        binder.forField(requestedDate)
                .asRequired("Requested date is required")
                .withValidator(date -> !date.isBefore(LocalDate.now()), "Requested date cannot be in the past")
                .bind(Case::getRequestedDate, Case::setRequestedDate);
        binder.forField(description)
                .withValidator(new StringLengthValidator("Description must be at most 1000 characters", 0, 1000))
                .bind(Case::getDescription, Case::setDescription);
        binder.forField(urgent)
                .bind(Case::isUrgent, Case::setUrgent);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String caseId = event.getRouteParameters().get("caseId").orElse("");
        Optional<Case> found = NEW.equals(caseId)
                ? Optional.of(new Case())
                : parseId(caseId).flatMap(caseService::findById);
        if (found.isEmpty()) {
            Notification.show("Case not found").addThemeVariants(NotificationVariant.LUMO_ERROR);
            event.forwardTo(CaseListView.class);
            return;
        }
        current = found.get();
        populate();
    }

    @Override
    public String getPageTitle() {
        return current == null || current.isNew() ? "New case" : "Case " + current.getId();
    }

    private void populate() {
        Role role = currentUser.getRole();
        heading.setText(getPageTitle());
        status.setText(current.getStatus().getLabel());
        binder.readBean(current);
        binder.setReadOnly(!CaseActions.isEditable(current.getStatus(), role));

        Set<CaseAction> allowed = CaseActions.allowed(current.getStatus(), role);
        save.setVisible(allowed.contains(CaseAction.SAVE));
        submit.setVisible(allowed.contains(CaseAction.SUBMIT));
        delete.setVisible(allowed.contains(CaseAction.DELETE) && !current.isNew());
        startReview.setVisible(allowed.contains(CaseAction.START_REVIEW));
        approve.setVisible(allowed.contains(CaseAction.APPROVE));
        reject.setVisible(allowed.contains(CaseAction.REJECT));
    }

    private void save() {
        if (binder.writeBeanIfValid(current)) {
            runAction(() -> caseService.save(current, currentUser.getRole()), "Case saved");
        }
    }

    private void submit() {
        if (binder.writeBeanIfValid(current)) {
            runAction(() -> {
                Case saved = caseService.save(current, currentUser.getRole());
                caseService.apply(saved.getId(), CaseAction.SUBMIT, currentUser.getRole());
            }, "Case submitted");
        }
    }

    private void startReview() {
        runAction(() -> caseService.apply(current.getId(), CaseAction.START_REVIEW, currentUser.getRole()),
                "Review started");
    }

    private void approve() {
        runAction(() -> caseService.apply(current.getId(), CaseAction.APPROVE, currentUser.getRole()),
                "Case approved");
    }

    private void openRejectDialog() {
        new RejectDialog(reason -> runAction(
                () -> caseService.reject(current.getId(), reason, currentUser.getRole()), "Case rejected"))
                .open();
    }

    private void confirmDelete() {
        ConfirmDialog dialog = new ConfirmDialog("Delete case?", "The case will be permanently deleted.", "Delete",
                event -> runAction(() -> caseService.delete(current.getId(), currentUser.getRole()),
                        "Case deleted"));
        dialog.setId("delete-confirm");
        dialog.setCancelable(true);
        dialog.setConfirmButtonTheme("error primary");
        dialog.open();
    }

    /**
     * Runs a service call, reports success and returns to the list. If the rules no longer allow the action (for
     * example because someone else changed the case meanwhile), the view is reloaded with the current state instead.
     */
    private void runAction(Runnable action, String successMessage) {
        try {
            action.run();
        } catch (IllegalStateException | NoSuchElementException e) {
            Notification.show("This action is no longer allowed").addThemeVariants(NotificationVariant.LUMO_ERROR);
            UI.getCurrent().refreshCurrentRoute(false);
            return;
        }
        Notification.show(successMessage).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        backToList();
    }

    private void backToList() {
        UI.getCurrent().navigate(CaseListView.class);
    }

    private static Optional<Long> parseId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
