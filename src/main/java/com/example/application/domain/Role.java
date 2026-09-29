package com.example.application.domain;

public enum Role {
    SUBMITTER("Submitter"),
    CASE_HANDLER("Case handler");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
