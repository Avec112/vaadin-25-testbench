package com.example.application.views.cases;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.function.Consumer;

/** Asks for the reason before a case is rejected. A reason is required. */
class RejectDialog extends Dialog {

    RejectDialog(Consumer<String> onReject) {
        setId("reject-dialog");
        setHeaderTitle("Reject case");

        TextArea reason = new TextArea("Reason");
        reason.setId("rejection-reason");
        reason.setRequiredIndicatorVisible(true);
        // The dialog decides when the field is invalid, not the component's built-in validation.
        reason.setManualValidation(true);
        reason.setWidthFull();
        reason.addValueChangeListener(event -> reason.setInvalid(false));

        Button confirm = new Button("Reject", event -> {
            if (reason.getValue().isBlank()) {
                reason.setErrorMessage("A reason is required");
                reason.setInvalid(true);
                return;
            }
            close();
            onReject.accept(reason.getValue().strip());
        });
        confirm.setId("confirm-reject");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancel = new Button("Cancel", event -> close());
        cancel.setId("cancel-reject");

        add(reason);
        getFooter().add(cancel, confirm);
    }
}
