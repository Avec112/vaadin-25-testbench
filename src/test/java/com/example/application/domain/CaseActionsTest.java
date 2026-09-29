package com.example.application.domain;

import static com.example.application.domain.CaseAction.APPROVE;
import static com.example.application.domain.CaseAction.DELETE;
import static com.example.application.domain.CaseAction.REJECT;
import static com.example.application.domain.CaseAction.SAVE;
import static com.example.application.domain.CaseAction.START_REVIEW;
import static com.example.application.domain.CaseAction.SUBMIT;
import static com.example.application.domain.CaseStatus.APPROVED;
import static com.example.application.domain.CaseStatus.DRAFT;
import static com.example.application.domain.CaseStatus.IN_REVIEW;
import static com.example.application.domain.CaseStatus.REJECTED;
import static com.example.application.domain.CaseStatus.SUBMITTED;
import static com.example.application.domain.Role.CASE_HANDLER;
import static com.example.application.domain.Role.SUBMITTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Level 1 of the test pyramid: the workflow rules as plain unit tests, no UI involved. The UI tests do not repeat
 * these rules; they only check that the UI shows what these rules say.
 */
@DisplayName("Case actions")
class CaseActionsTest {

    static Stream<Arguments> rules() {
        return Stream.of(
                arguments(DRAFT, SUBMITTER, EnumSet.of(SAVE, SUBMIT, DELETE)),
                arguments(SUBMITTED, SUBMITTER, EnumSet.noneOf(CaseAction.class)),
                arguments(IN_REVIEW, SUBMITTER, EnumSet.noneOf(CaseAction.class)),
                arguments(APPROVED, SUBMITTER, EnumSet.noneOf(CaseAction.class)),
                arguments(REJECTED, SUBMITTER, EnumSet.noneOf(CaseAction.class)),
                arguments(DRAFT, CASE_HANDLER, EnumSet.noneOf(CaseAction.class)),
                arguments(SUBMITTED, CASE_HANDLER, EnumSet.of(START_REVIEW)),
                arguments(IN_REVIEW, CASE_HANDLER, EnumSet.of(APPROVE, REJECT)),
                arguments(APPROVED, CASE_HANDLER, EnumSet.noneOf(CaseAction.class)),
                arguments(REJECTED, CASE_HANDLER, EnumSet.noneOf(CaseAction.class)));
    }

    @ParameterizedTest(name = "{1} on a {0} case may {2}")
    @MethodSource("rules")
    void allowsExactlyTheseActions(CaseStatus status, Role role, Set<CaseAction> expected) {
        assertThat(CaseActions.allowed(status, role)).isEqualTo(expected);
    }

    @Test
    @DisplayName("the rule table covers every status and role combination")
    void everyCombinationIsCovered() {
        assertThat(rules()).hasSize(CaseStatus.values().length * Role.values().length);
    }

    @ParameterizedTest(name = "a {0} case")
    @EnumSource(CaseStatus.class)
    @DisplayName("only a submitter may edit, and only a draft")
    void onlySubmitterMayEditDraft(CaseStatus status) {
        assertThat(CaseActions.isEditable(status, SUBMITTER)).isEqualTo(status == DRAFT);
        assertThat(CaseActions.isEditable(status, CASE_HANDLER)).isFalse();
    }

    @Test
    @DisplayName("a copy is independent of the original")
    void copyIsIndependent() {
        Case original = new Case("Laptop for new developer", CaseCategory.PURCHASE, 18000,
                LocalDate.of(2030, 1, 1), false, DRAFT);
        original.setId(1L);

        Case copy = original.copy();
        copy.setTitle("Changed");

        assertThat(original.getTitle()).isEqualTo("Laptop for new developer");
        assertThat(copy.getId()).isEqualTo(1L);
    }
}
