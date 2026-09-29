package com.example.application.domain;

public enum CaseCategory {
    PURCHASE("Purchase"),
    TRAVEL("Travel"),
    TRAINING("Training");

    private final String label;

    CaseCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
