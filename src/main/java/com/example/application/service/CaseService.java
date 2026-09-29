package com.example.application.service;

import com.example.application.domain.Case;
import com.example.application.domain.CaseAction;
import com.example.application.domain.CaseActions;
import com.example.application.domain.CaseCategory;
import com.example.application.domain.CaseStatus;
import com.example.application.domain.Role;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * In-memory case store. Every change is checked against {@link CaseActions}, so the UI cannot bypass the workflow
 * rules. Callers always get copies; changing a returned case does not change the store.
 */
@Service
public class CaseService {

    private final Map<Long, Case> cases = new LinkedHashMap<>();
    private long nextId;

    public CaseService() {
        reset();
    }

    /**
     * Restores the seed data. Browserless tests call this before each test so every test starts from the same state.
     */
    public synchronized void reset() {
        cases.clear();
        nextId = 1;
        LocalDate today = LocalDate.now();
        store(new Case("Laptop for new developer", CaseCategory.PURCHASE, 18000, today.plusDays(14), false,
                CaseStatus.DRAFT));
        store(new Case("Conference trip to Oslo", CaseCategory.TRAVEL, 9500, today.plusDays(30), true,
                CaseStatus.DRAFT));
        store(new Case("Vaadin training course", CaseCategory.TRAINING, 12000, today.plusDays(21), false,
                CaseStatus.SUBMITTED));
        store(new Case("Office chairs", CaseCategory.PURCHASE, 7400, today.plusDays(10), true,
                CaseStatus.SUBMITTED));
        store(new Case("Customer visit in Bergen", CaseCategory.TRAVEL, 4300, today.plusDays(7), false,
                CaseStatus.IN_REVIEW));
        store(new Case("Security certification", CaseCategory.TRAINING, 22000, today.plusDays(45), false,
                CaseStatus.IN_REVIEW));
        store(new Case("Monitor upgrade", CaseCategory.PURCHASE, 3100, today.minusDays(20), false,
                CaseStatus.APPROVED));
        Case rejected = new Case("Team offsite travel", CaseCategory.TRAVEL, 15800, today.minusDays(5), false,
                CaseStatus.REJECTED);
        rejected.setRejectionReason("Budget exceeded");
        store(rejected);
    }

    public synchronized List<Case> findAll() {
        return cases.values().stream().map(Case::copy).toList();
    }

    public synchronized Optional<Case> findById(long id) {
        return Optional.ofNullable(cases.get(id)).map(Case::copy);
    }

    public synchronized Case save(Case aCase, Role role) {
        CaseStatus storedStatus = aCase.isNew() ? CaseStatus.DRAFT : existing(aCase.getId()).getStatus();
        requireAllowed(CaseAction.SAVE, storedStatus, role);
        Case toStore = aCase.copy();
        toStore.setStatus(storedStatus);
        return store(toStore).copy();
    }

    public synchronized void delete(long id, Role role) {
        requireAllowed(CaseAction.DELETE, existing(id).getStatus(), role);
        cases.remove(id);
    }

    public synchronized Case apply(long id, CaseAction action, Role role) {
        CaseStatus target = switch (action) {
            case SUBMIT -> CaseStatus.SUBMITTED;
            case START_REVIEW -> CaseStatus.IN_REVIEW;
            case APPROVE -> CaseStatus.APPROVED;
            default -> throw new IllegalArgumentException(action + " is not a status transition");
        };
        Case stored = existing(id);
        requireAllowed(action, stored.getStatus(), role);
        stored.setStatus(target);
        return stored.copy();
    }

    public synchronized Case reject(long id, String reason, Role role) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        Case stored = existing(id);
        requireAllowed(CaseAction.REJECT, stored.getStatus(), role);
        stored.setStatus(CaseStatus.REJECTED);
        stored.setRejectionReason(reason.strip());
        return stored.copy();
    }

    private Case existing(long id) {
        Case stored = cases.get(id);
        if (stored == null) {
            throw new NoSuchElementException("No case with id " + id);
        }
        return stored;
    }

    private static void requireAllowed(CaseAction action, CaseStatus status, Role role) {
        if (!CaseActions.allowed(status, role).contains(action)) {
            throw new IllegalStateException(action + " is not allowed for " + role + " on a " + status + " case");
        }
    }

    private Case store(Case aCase) {
        if (aCase.isNew()) {
            aCase.setId(nextId++);
        }
        cases.put(aCase.getId(), aCase);
        return aCase;
    }
}
