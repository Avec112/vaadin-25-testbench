package com.example.application.domain;

import java.time.LocalDate;

/**
 * A case as edited in the form. Validation lives in the form's Binder (see CaseView) and the workflow rules in
 * {@link CaseActions}; this class only holds data.
 */
public class Case {

    private Long id;
    private String title = "";
    private CaseCategory category;
    private Integer amount;
    private LocalDate requestedDate;
    private String description = "";
    private boolean urgent;
    private CaseStatus status = CaseStatus.DRAFT;
    private String rejectionReason;

    public Case() {
    }

    public Case(String title, CaseCategory category, Integer amount, LocalDate requestedDate, boolean urgent,
            CaseStatus status) {
        this.title = title;
        this.category = category;
        this.amount = amount;
        this.requestedDate = requestedDate;
        this.urgent = urgent;
        this.status = status;
    }

    public Case copy() {
        Case copy = new Case(title, category, amount, requestedDate, urgent, status);
        copy.id = id;
        copy.description = description;
        copy.rejectionReason = rejectionReason;
        return copy;
    }

    public boolean isNew() {
        return id == null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public CaseCategory getCategory() {
        return category;
    }

    public void setCategory(CaseCategory category) {
        this.category = category;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDate requestedDate) {
        this.requestedDate = requestedDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
