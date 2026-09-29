package com.example.application.service;

import static com.example.application.domain.Role.CASE_HANDLER;
import static com.example.application.domain.Role.SUBMITTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.application.domain.Case;
import com.example.application.domain.CaseAction;
import com.example.application.domain.CaseCategory;
import com.example.application.domain.CaseStatus;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Case service")
class CaseServiceTest {

    private final CaseService service = new CaseService();

    @Test
    @DisplayName("seed data has eight cases covering every status")
    void seedDataCoversEveryStatus() {
        assertThat(service.findAll()).hasSize(8);
        assertThat(service.findAll()).extracting(Case::getStatus).contains(CaseStatus.values());
    }

    @Test
    @DisplayName("saving a new case assigns an id and keeps it as a draft")
    void saveNewCase() {
        Case saved = service.save(newCase(), SUBMITTER);

        assertThat(saved.getId()).isEqualTo(9L);
        assertThat(service.findById(9L)).get().extracting(Case::getStatus).isEqualTo(CaseStatus.DRAFT);
    }

    @Test
    @DisplayName("a case handler may not create cases")
    void caseHandlerMayNotSave() {
        assertThatThrownBy(() -> service.save(newCase(), CASE_HANDLER)).isInstanceOf(IllegalStateException.class);
        assertThat(service.findAll()).hasSize(8);
    }

    @Test
    @DisplayName("submitting a draft makes it submitted")
    void submitDraft() {
        Case submitted = service.apply(1L, CaseAction.SUBMIT, SUBMITTER);

        assertThat(submitted.getStatus()).isEqualTo(CaseStatus.SUBMITTED);
    }

    @Test
    @DisplayName("a disallowed action is refused and changes nothing")
    void disallowedActionIsRefused() {
        assertThatThrownBy(() -> service.apply(1L, CaseAction.APPROVE, CASE_HANDLER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.findById(1L)).get().extracting(Case::getStatus).isEqualTo(CaseStatus.DRAFT);
    }

    @Test
    @DisplayName("apply only handles status transitions")
    void applyRejectsNonTransitionActions() {
        assertThatThrownBy(() -> service.apply(1L, CaseAction.DELETE, SUBMITTER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejecting requires a reason and stores it")
    void rejectNeedsReason() {
        assertThatThrownBy(() -> service.reject(5L, "   ", CASE_HANDLER))
                .isInstanceOf(IllegalArgumentException.class);

        Case rejected = service.reject(5L, "  Not in budget  ", CASE_HANDLER);

        assertThat(rejected.getStatus()).isEqualTo(CaseStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Not in budget");
    }

    @Test
    @DisplayName("only drafts can be deleted")
    void deleteOnlyDrafts() {
        service.delete(1L, SUBMITTER);
        assertThat(service.findById(1L)).isEmpty();

        assertThatThrownBy(() -> service.delete(3L, SUBMITTER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an unknown id is reported as not found")
    void unknownId() {
        assertThat(service.findById(999L)).isEmpty();
        assertThatThrownBy(() -> service.apply(999L, CaseAction.SUBMIT, SUBMITTER))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("returned cases are copies")
    void returnsCopies() {
        service.findById(1L).orElseThrow().setTitle("Changed outside the service");

        assertThat(service.findById(1L)).get().extracting(Case::getTitle).isEqualTo("Laptop for new developer");
    }

    @Test
    @DisplayName("reset restores the seed data")
    void resetRestoresSeedData() {
        service.delete(1L, SUBMITTER);
        service.save(newCase(), SUBMITTER);

        service.reset();

        assertThat(service.findAll()).hasSize(8);
        assertThat(service.findById(1L)).isPresent();
        assertThat(service.save(newCase(), SUBMITTER).getId()).isEqualTo(9L);
    }

    private static Case newCase() {
        return new Case("Printer for the office", CaseCategory.PURCHASE, 2500, LocalDate.now().plusDays(7),
                false, CaseStatus.DRAFT);
    }
}
