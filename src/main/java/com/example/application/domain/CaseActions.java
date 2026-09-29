package com.example.application.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The single source of truth for what a role may do with a case in a given status. Views ask it which buttons to
 * show; CaseService asks it before changing anything.
 */
public final class CaseActions {

    private CaseActions() {
    }

    public static Set<CaseAction> allowed(CaseStatus status, Role role) {
        EnumSet<CaseAction> actions = switch (role) {
            case SUBMITTER -> status == CaseStatus.DRAFT
                    ? EnumSet.of(CaseAction.SAVE, CaseAction.SUBMIT, CaseAction.DELETE)
                    : EnumSet.noneOf(CaseAction.class);
            case CASE_HANDLER -> switch (status) {
                case SUBMITTED -> EnumSet.of(CaseAction.START_REVIEW);
                case IN_REVIEW -> EnumSet.of(CaseAction.APPROVE, CaseAction.REJECT);
                default -> EnumSet.noneOf(CaseAction.class);
            };
        };
        return Collections.unmodifiableSet(actions);
    }

    public static boolean isEditable(CaseStatus status, Role role) {
        return role == Role.SUBMITTER && status == CaseStatus.DRAFT;
    }
}
