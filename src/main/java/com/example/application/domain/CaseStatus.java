package com.example.application.domain;

public enum CaseStatus {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    IN_REVIEW("In review"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String label;

    CaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
