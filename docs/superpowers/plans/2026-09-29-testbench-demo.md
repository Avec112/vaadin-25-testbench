# TestBench Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a small case-handling Vaadin app and a test suite that demonstrates UI testing at three levels — domain unit tests, browserless UI tests and end-to-end browser tests — so a team can learn what to verify and at which level.

**Architecture:** Domain rules (`CaseActions`) decide which actions a role may take in a given status; an in-memory `CaseService` enforces them; two Vaadin views (`CaseListView`, `CaseView`) render them, with the role chosen in `MainLayout`. Browserless tests (`SpringBrowserlessTest`) cover each view in depth; a few TestBench end-to-end tests (`BrowserTestBase` + page objects) cover the key flows in a real Chrome.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Vaadin Flow 25.2.8, `com.vaadin:browserless-test-spring:1.1.2`, `com.vaadin:vaadin-testbench-junit6:25.2.8`, JUnit 6, AssertJ, Maven (system `mvn`).

**Spec:** `docs/superpowers/specs/2026-09-29-testbench-demo-design.md`

## Global Constraints

- All code, identifiers, comments, commit messages, UI text and documentation are in English. Never Norwegian.
- Vaadin stays at `25.2.8`, Spring Boot at `4.1.1`, Java `25`. Do not upgrade anything.
- Browserless tests use `com.vaadin:browserless-test-spring` version `1.1.2` via the `browserless-test.version` property. Never use `vaadin-testbench-unit-*` (deprecated for removal).
- End-to-end tests use `com.vaadin:vaadin-testbench-junit6` with version `${vaadin.version}`.
- Use the system `mvn`. Do not add a Maven wrapper.
- Root package stays `com.example.application` (it is listed in `vaadin.allowed-packages`).
- Component ids are exactly: `role-selector`, `cases-grid`, `title-filter`, `status-filter`, `new-case`, `case-heading`, `status`, `title`, `category`, `amount`, `requested-date`, `description`, `urgent`, `save`, `submit`, `delete`, `start-review`, `approve`, `reject`, `back`, `delete-confirm`, `reject-dialog`, `rejection-reason`, `confirm-reject`, `cancel-reject`.
- Tests locate components by id, never by caption text or position (grid rows/columns excepted).
- No `Thread.sleep` in any test.
- UI texts and validation messages are exactly as written in this plan; tests assert on them.
- Browserless queries (`$(...)`) only match effectively visible components: "hidden" is asserted as "no visible component with that id".

## Review Focus

1. A title of only spaces (`"     "`) must be rejected with "Title is required", not saved as a blank title. → Task 4, `CaseViewTest.Validation.rejectsInvalidTitle`.
2. Filter text with different case and surrounding spaces (`"  LAPTOP "`) must still match. → Task 3, `CaseListViewTest.Filtering.titleFilterIgnoresCaseAndSurroundingSpaces`.
3. An action on a case that someone else changed after the page was loaded must show "This action is no longer allowed" and reload the view, not crash or apply the action. → Task 5, `CaseViewTest.StaleAction`.
4. A rejection reason of only spaces must be treated as missing. → Task 5, `CaseViewTest.Workflow.rejectWithBlankReasonIsBlocked`.
5. Malformed or unknown ids in the URL (`case/abc`, `case/-1`, `case/999`) must show "Case not found" and return to the list. → Task 4, `CaseViewTest.NotFound`.

---

## File Structure

```
src/main/java/com/example/application/
  domain/
    Case.java              mutable bean edited by the form; copy()
    CaseCategory.java      PURCHASE / TRAVEL / TRAINING with labels
    CaseStatus.java        DRAFT … REJECTED with labels
    Role.java              SUBMITTER / CASE_HANDLER with labels
    CaseAction.java        SAVE, SUBMIT, DELETE, START_REVIEW, APPROVE, REJECT
    CaseActions.java       allowed(status, role), isEditable(status, role) — the rules
  service/
    CaseService.java       in-memory store, seed data, reset(), rule enforcement
    CurrentUser.java       role stored in the VaadinSession
  views/
    MainLayout.java        (modify) adds the role selector
    cases/
      CaseListView.java    grid with filters, sorting, "New case"
      CaseView.java        form, binder, status/role-driven buttons, actions
      RejectDialog.java    dialog asking for a rejection reason
  (delete) views/helloworld/HelloWorldView.java, views/about/AboutView.java

src/test/java/com/example/application/
  domain/CaseActionsTest.java
  service/CaseServiceTest.java
  views/AbstractViewTest.java
  views/cases/CaseListViewTest.java
  views/cases/CaseViewTest.java
  it/AbstractIT.java
  it/pages/CaseListPage.java
  it/pages/CasePage.java
  it/pages/RoleSelector.java
  it/CaseListIT.java
  it/CaseLifecycleIT.java
  it/CaseRejectIT.java
  it/CaseValidationIT.java

docs/ui-test-checklist.md
docs/testing-levels.md
README.md (rewrite), CLAUDE.md (update), pom.xml (modify)
```

---

### Task 1: Domain model and rules

**Files:**
- Create: `src/main/java/com/example/application/domain/CaseCategory.java`
- Create: `src/main/java/com/example/application/domain/CaseStatus.java`
- Create: `src/main/java/com/example/application/domain/Role.java`
- Create: `src/main/java/com/example/application/domain/CaseAction.java`
- Create: `src/main/java/com/example/application/domain/Case.java`
- Create: `src/main/java/com/example/application/domain/CaseActions.java`
- Test: `src/test/java/com/example/application/domain/CaseActionsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum CaseCategory { PURCHASE, TRAVEL, TRAINING; String getLabel() }` — labels "Purchase", "Travel", "Training".
  - `enum CaseStatus { DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED; String getLabel() }` — labels "Draft", "Submitted", "In review", "Approved", "Rejected".
  - `enum Role { SUBMITTER, CASE_HANDLER; String getLabel() }` — labels "Submitter", "Case handler".
  - `enum CaseAction { SAVE, SUBMIT, DELETE, START_REVIEW, APPROVE, REJECT }`.
  - `class Case` — no-arg constructor (status `DRAFT`, `title` and `description` `""`); `Case(String title, CaseCategory category, Integer amount, LocalDate requestedDate, boolean urgent, CaseStatus status)`; getters/setters for `Long id`, `String title`, `CaseCategory category`, `Integer amount`, `LocalDate requestedDate`, `String description`, `boolean urgent` (`isUrgent`/`setUrgent`), `CaseStatus status`, `String rejectionReason`; `boolean isNew()`; `Case copy()`.
  - `final class CaseActions { static Set<CaseAction> allowed(CaseStatus, Role); static boolean isEditable(CaseStatus, Role) }`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/example/application/domain/CaseActionsTest.java`:

```java
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
                arguments(DRAFT, SUBMITTER, Set.of(SAVE, SUBMIT, DELETE)),
                arguments(SUBMITTED, SUBMITTER, Set.<CaseAction>of()),
                arguments(IN_REVIEW, SUBMITTER, Set.<CaseAction>of()),
                arguments(APPROVED, SUBMITTER, Set.<CaseAction>of()),
                arguments(REJECTED, SUBMITTER, Set.<CaseAction>of()),
                arguments(DRAFT, CASE_HANDLER, Set.<CaseAction>of()),
                arguments(SUBMITTED, CASE_HANDLER, Set.of(START_REVIEW)),
                arguments(IN_REVIEW, CASE_HANDLER, Set.of(APPROVE, REJECT)),
                arguments(APPROVED, CASE_HANDLER, Set.<CaseAction>of()),
                arguments(REJECTED, CASE_HANDLER, Set.<CaseAction>of()));
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CaseActionsTest`
Expected: FAIL — compilation errors, `package com.example.application.domain does not exist`.

- [ ] **Step 3: Write the implementation**

`src/main/java/com/example/application/domain/CaseCategory.java`:

```java
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
```

`src/main/java/com/example/application/domain/CaseStatus.java`:

```java
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
```

`src/main/java/com/example/application/domain/Role.java`:

```java
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
```

`src/main/java/com/example/application/domain/CaseAction.java`:

```java
package com.example.application.domain;

public enum CaseAction {
    SAVE,
    SUBMIT,
    DELETE,
    START_REVIEW,
    APPROVE,
    REJECT
}
```

`src/main/java/com/example/application/domain/Case.java`:

```java
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
```

`src/main/java/com/example/application/domain/CaseActions.java`:

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CaseActionsTest`
Expected: PASS, `Tests run: 17, Failures: 0, Errors: 0` (10 rule rows + 1 coverage check + 5 statuses + 1 copy test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/application/domain src/test/java/com/example/application/domain
git commit -m "Add case domain model and workflow rules"
```

---

### Task 2: CaseService

**Files:**
- Create: `src/main/java/com/example/application/service/CaseService.java`
- Test: `src/test/java/com/example/application/service/CaseServiceTest.java`

**Interfaces:**
- Consumes: everything from Task 1.
- Produces: `@Service class CaseService`:
  - `void reset()` — restores the 8 seed cases with ids 1–8 in this order:

    | id | title | category | amount | requestedDate | urgent | status |
    |---|---|---|---|---|---|---|
    | 1 | Laptop for new developer | PURCHASE | 18000 | today+14 | false | DRAFT |
    | 2 | Conference trip to Oslo | TRAVEL | 9500 | today+30 | true | DRAFT |
    | 3 | Vaadin training course | TRAINING | 12000 | today+21 | false | SUBMITTED |
    | 4 | Office chairs | PURCHASE | 7400 | today+10 | true | SUBMITTED |
    | 5 | Customer visit in Bergen | TRAVEL | 4300 | today+7 | false | IN_REVIEW |
    | 6 | Security certification | TRAINING | 22000 | today+45 | false | IN_REVIEW |
    | 7 | Monitor upgrade | PURCHASE | 3100 | today-20 | false | APPROVED |
    | 8 | Team offsite travel | TRAVEL | 15800 | today-5 | false | REJECTED (reason "Budget exceeded") |

  - `List<Case> findAll()` — copies, in id order.
  - `Optional<Case> findById(long id)` — a copy.
  - `Case save(Case aCase, Role role)` — requires `SAVE` allowed for the stored status (or `DRAFT` for a new case); assigns an id to a new case; returns a copy. Keeps the stored status.
  - `void delete(long id, Role role)` — requires `DELETE`.
  - `Case apply(long id, CaseAction action, Role role)` — for `SUBMIT` → `SUBMITTED`, `START_REVIEW` → `IN_REVIEW`, `APPROVE` → `APPROVED`; any other action throws `IllegalArgumentException`.
  - `Case reject(long id, String reason, Role role)` — requires `REJECT`; blank reason throws `IllegalArgumentException`; stores the stripped reason.
  - Disallowed action → `IllegalStateException`. Unknown id → `NoSuchElementException`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/example/application/service/CaseServiceTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CaseServiceTest`
Expected: FAIL — compilation error, `cannot find symbol: class CaseService`.

- [ ] **Step 3: Write the implementation**

`src/main/java/com/example/application/service/CaseService.java`:

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CaseServiceTest`
Expected: PASS, 11 tests, 0 failures, 0 errors.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/application/service src/test/java/com/example/application/service
git commit -m "Add in-memory CaseService with seed data and rule enforcement"
```

---

### Task 3: Browserless test setup and the case list

**Files:**
- Modify: `pom.xml` (replace `vaadin-testbench-unit-junit6` with `browserless-test-spring`, add property)
- Delete: `src/main/java/com/example/application/views/helloworld/HelloWorldView.java`
- Delete: `src/main/java/com/example/application/views/about/AboutView.java`
- Delete: `src/main/resources/META-INF/resources/images/empty-plant.png` (only if Step 3's grep shows it is used by nothing else)
- Create: `src/main/java/com/example/application/service/CurrentUser.java`
- Create: `src/main/java/com/example/application/views/cases/CaseListView.java`
- Create: `src/test/java/com/example/application/views/AbstractViewTest.java`
- Test: `src/test/java/com/example/application/views/cases/CaseListViewTest.java`

**Interfaces:**
- Consumes: Task 1 domain, Task 2 `CaseService` (`reset()`, `findAll()`).
- Produces:
  - `@Component class CurrentUser { Role getRole(); void setRole(Role role) }` — default `SUBMITTER`, stored as a `VaadinSession` attribute.
  - `CaseListView` — route `""`, grid id `cases-grid` with column keys (in order) `title`, `category`, `amount`, `requestedDate`, `status`, `urgent`; header texts "Title", "Category", "Amount", "Requested date", "Status", "Urgent"; filters `title-filter` (`TextField`) and `status-filter` (`Select<CaseStatus>`) in a second header row; button `new-case`. Row click navigates to `case/<id>`; `new-case` navigates to `case/new`.
  - `abstract class AbstractViewTest extends SpringBrowserlessTest` with `protected CaseService caseService`, `protected void actAs(Role)`, `protected long caseIdByTitle(String)`, `protected String lastNotificationText()`, `protected boolean isNotificationOpen()`.

- [ ] **Step 1: Swap the browserless test dependency**

In `pom.xml`, inside `<properties>`, after `<vaadin.version>25.2.8</vaadin.version>` add:

```xml
        <!-- Not managed by the Vaadin 25.2 BOM. Bump together with Vaadin; from 25.3 this equals the Vaadin version. -->
        <browserless-test.version>1.1.2</browserless-test.version>
```

Replace this dependency:

```xml
        <dependency>
            <groupId>com.vaadin</groupId>
            <artifactId>vaadin-testbench-unit-junit6</artifactId>
            <scope>test</scope>
        </dependency>
```

with:

```xml
        <dependency>
            <groupId>com.vaadin</groupId>
            <artifactId>browserless-test-spring</artifactId>
            <version>${browserless-test.version}</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Write the failing tests**

Create `src/test/java/com/example/application/views/AbstractViewTest.java`:

```java
package com.example.application.views;

import com.example.application.domain.Case;
import com.example.application.domain.Role;
import com.example.application.service.CaseService;
import com.example.application.service.CurrentUser;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.notification.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base class for browserless UI tests. The views run with their real Spring beans, but without a browser or a
 * server, so the tests are fast. Every test starts from the seed data, acting as a submitter.
 */
@SpringBootTest
@ViewPackages(packages = "com.example.application.views")
public abstract class AbstractViewTest extends SpringBrowserlessTest {

    @Autowired
    protected CaseService caseService;

    @Autowired
    private CurrentUser currentUser;

    @BeforeEach
    void startFromKnownState() {
        caseService.reset();
        actAs(Role.SUBMITTER);
    }

    protected void actAs(Role role) {
        currentUser.setRole(role);
    }

    protected long caseIdByTitle(String title) {
        return caseService.findAll().stream()
                .filter(aCase -> aCase.getTitle().equals(title))
                .map(Case::getId)
                .findFirst()
                .orElseThrow();
    }

    protected String lastNotificationText() {
        return test($(Notification.class).last()).getText();
    }

    protected boolean isNotificationOpen() {
        return $(Notification.class).exists();
    }
}
```

Create `src/test/java/com/example/application/views/cases/CaseListViewTest.java`:

```java
package com.example.application.views.cases;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.domain.Case;
import com.example.application.domain.CaseStatus;
import com.example.application.domain.Role;
import com.example.application.views.AbstractViewTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.SortDirection;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Browserless tests for the case list. Each nested class is one aspect of the page; see
 * docs/ui-test-checklist.md for what "InitialState" checks and why.
 */
@DisplayName("Case list")
class CaseListViewTest extends AbstractViewTest {

    private static final int SEED_SIZE = 8;
    private static final int TITLE = 0;
    private static final int AMOUNT = 2;
    private static final int STATUS = 4;

    @Nested
    @DisplayName("when the page is presented")
    class InitialState {

        @Test
        @DisplayName("shows every case with the expected columns")
        void showsAllCasesWithExpectedColumns() {
            navigate(CaseListView.class);

            assertThat(test(grid()).size()).isEqualTo(SEED_SIZE);
            assertThat(grid().getColumns()).extracting(Grid.Column::getHeaderText)
                    .containsExactly("Title", "Category", "Amount", "Requested date", "Status", "Urgent");
        }

        @Test
        @DisplayName("every column is sortable")
        void everyColumnIsSortable() {
            navigate(CaseListView.class);

            IntStream.range(0, grid().getColumns().size())
                    .forEach(column -> assertThat(test(grid()).isColumnSortable(column)).isTrue());
        }

        @Test
        @DisplayName("filters are empty")
        void filtersAreEmpty() {
            navigate(CaseListView.class);

            assertThat(titleFilter().getValue()).isEmpty();
            assertThat(statusFilter().getValue()).isNull();
        }

        @Test
        @DisplayName("a submitter sees the New case button")
        void submitterSeesNewCase() {
            navigate(CaseListView.class);

            assertThat($(Button.class).withId("new-case").exists()).isTrue();
        }

        @Test
        @DisplayName("a case handler does not see the New case button")
        void caseHandlerDoesNotSeeNewCase() {
            actAs(Role.CASE_HANDLER);
            navigate(CaseListView.class);

            // Browserless queries only find visible components, so "hidden" means "not found".
            assertThat($(Button.class).withId("new-case").exists()).isFalse();
        }

        @Test
        @DisplayName("no notification is open")
        void noNotification() {
            navigate(CaseListView.class);

            assertThat(isNotificationOpen()).isFalse();
        }
    }

    @Nested
    @DisplayName("filtering")
    class Filtering {

        @BeforeEach
        void openList() {
            navigate(CaseListView.class);
        }

        @Test
        @DisplayName("by title ignores case and surrounding spaces")
        void titleFilterIgnoresCaseAndSurroundingSpaces() {
            test(titleFilter()).setValue("  LAPTOP ");

            assertThat(test(grid()).size()).isEqualTo(1);
            assertThat(test(grid()).getCellText(0, TITLE)).isEqualTo("Laptop for new developer");
        }

        @Test
        @DisplayName("by status shows only that status")
        void statusFilter() {
            test(statusFilter()).selectItem("Draft");

            assertThat(test(grid()).size()).isEqualTo(2);
            IntStream.range(0, 2).forEach(row -> assertThat(test(grid()).getCellText(row, STATUS)).isEqualTo("Draft"));
        }

        @Test
        @DisplayName("title and status filters combine")
        void filtersCombine() {
            test(titleFilter()).setValue("chair");
            test(statusFilter()).selectItem("Submitted");
            assertThat(test(grid()).size()).isEqualTo(1);

            test(statusFilter()).selectItem("Draft");
            assertThat(test(grid()).size()).isZero();
        }

        @Test
        @DisplayName("clearing the title filter shows every case again")
        void clearingTitleFilterRestoresAllRows() {
            test(titleFilter()).setValue("laptop");
            test(titleFilter()).setValue("");

            assertThat(test(grid()).size()).isEqualTo(SEED_SIZE);
        }
    }

    @Nested
    @DisplayName("sorting")
    class Sorting {

        @BeforeEach
        void openList() {
            navigate(CaseListView.class);
        }

        @Test
        @DisplayName("by amount, ascending")
        void amountAscending() {
            test(grid()).sortByColumn("amount", SortDirection.ASCENDING);

            assertThat(test(grid()).getCellText(0, AMOUNT)).isEqualTo("3100");
            assertThat(test(grid()).getCellText(SEED_SIZE - 1, AMOUNT)).isEqualTo("22000");
        }

        @Test
        @DisplayName("by amount, descending")
        void amountDescending() {
            test(grid()).sortByColumn("amount", SortDirection.DESCENDING);

            assertThat(test(grid()).getCellText(0, AMOUNT)).isEqualTo("22000");
            assertThat(test(grid()).getCellText(SEED_SIZE - 1, AMOUNT)).isEqualTo("3100");
        }

        @Test
        @DisplayName("by requested date, ascending")
        void requestedDateAscending() {
            test(grid()).sortByColumn("requestedDate", SortDirection.ASCENDING);

            assertThat(test(grid()).getCellText(0, TITLE)).isEqualTo("Monitor upgrade");
            assertThat(test(grid()).getCellText(SEED_SIZE - 1, TITLE)).isEqualTo("Security certification");
        }

        @Test
        @DisplayName("by requested date, descending")
        void requestedDateDescending() {
            test(grid()).sortByColumn("requestedDate", SortDirection.DESCENDING);

            assertThat(test(grid()).getCellText(0, TITLE)).isEqualTo("Security certification");
            assertThat(test(grid()).getCellText(SEED_SIZE - 1, TITLE)).isEqualTo("Monitor upgrade");
        }
    }

    @SuppressWarnings("unchecked")
    private Grid<Case> grid() {
        return $(Grid.class).id("cases-grid");
    }

    private TextField titleFilter() {
        return $(TextField.class).id("title-filter");
    }

    @SuppressWarnings("unchecked")
    private Select<CaseStatus> statusFilter() {
        return $(Select.class).id("status-filter");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn test -Dtest=CaseListViewTest`
Expected: FAIL — compilation errors: `cannot find symbol: class CurrentUser` and `class CaseListView`.

Also check what uses the About view's image before deleting it:

Run: `grep -rn "empty-plant" src/`
Expected: only `src/main/java/com/example/application/views/about/AboutView.java`.

- [ ] **Step 4: Write the implementation**

Delete the generated views (both use route `""` or are no longer wanted):

```bash
git rm -r src/main/java/com/example/application/views/helloworld src/main/java/com/example/application/views/about
git rm src/main/resources/META-INF/resources/images/empty-plant.png
```

(Skip the second `git rm` if Step 3's grep found other users of the image.)

`src/main/java/com/example/application/service/CurrentUser.java`:

```java
package com.example.application.service;

import com.example.application.domain.Role;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

/**
 * Knows which role the current user acts as. The role is kept in the VaadinSession, so every browser session has
 * its own. In a real application this would be backed by Spring Security; views only depend on this class, so
 * replacing the implementation does not affect them.
 */
@Component
public class CurrentUser {

    public Role getRole() {
        VaadinSession session = VaadinSession.getCurrent();
        Role role = session == null ? null : session.getAttribute(Role.class);
        return role == null ? Role.SUBMITTER : role;
    }

    public void setRole(Role role) {
        VaadinSession.getCurrent().setAttribute(Role.class, role);
    }
}
```

`src/main/java/com/example/application/views/cases/CaseListView.java`:

```java
package com.example.application.views.cases;

import com.example.application.domain.Case;
import com.example.application.domain.CaseStatus;
import com.example.application.domain.Role;
import com.example.application.service.CaseService;
import com.example.application.service.CurrentUser;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridListDataView;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.Locale;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

@PageTitle("Cases")
@Route("")
@Menu(order = 0, icon = LineAwesomeIconUrl.CLIPBOARD_LIST_SOLID)
public class CaseListView extends VerticalLayout {

    private final TextField titleFilter = new TextField();
    private final Select<CaseStatus> statusFilter = new Select<>();
    private final GridListDataView<Case> dataView;

    public CaseListView(CaseService caseService, CurrentUser currentUser) {
        setSizeFull();

        Button newCase = new Button("New case", event -> UI.getCurrent().navigate("case/new"));
        newCase.setId("new-case");
        newCase.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newCase.setVisible(currentUser.getRole() == Role.SUBMITTER);

        Grid<Case> grid = new Grid<>();
        grid.setId("cases-grid");
        Grid.Column<Case> titleColumn = grid.addColumn(Case::getTitle).setKey("title").setHeader("Title")
                .setComparator(Case::getTitle).setFlexGrow(2);
        grid.addColumn(aCase -> aCase.getCategory().getLabel()).setKey("category").setHeader("Category")
                .setComparator(Case::getCategory);
        grid.addColumn(Case::getAmount).setKey("amount").setHeader("Amount").setComparator(Case::getAmount);
        grid.addColumn(Case::getRequestedDate).setKey("requestedDate").setHeader("Requested date")
                .setComparator(Case::getRequestedDate);
        Grid.Column<Case> statusColumn = grid.addColumn(aCase -> aCase.getStatus().getLabel()).setKey("status")
                .setHeader("Status").setComparator(Case::getStatus);
        grid.addColumn(aCase -> aCase.isUrgent() ? "Yes" : "").setKey("urgent").setHeader("Urgent")
                .setComparator(Case::isUrgent);
        grid.addItemClickListener(event -> UI.getCurrent().navigate("case/" + event.getItem().getId()));

        dataView = grid.setItems(caseService.findAll());
        dataView.setFilter(this::matchesFilters);

        titleFilter.setId("title-filter");
        titleFilter.setPlaceholder("Filter");
        titleFilter.setClearButtonVisible(true);
        titleFilter.addValueChangeListener(event -> dataView.refreshAll());

        statusFilter.setId("status-filter");
        statusFilter.setItems(CaseStatus.values());
        statusFilter.setItemLabelGenerator(status -> status == null ? "All" : status.getLabel());
        statusFilter.setEmptySelectionAllowed(true);
        statusFilter.setEmptySelectionCaption("All");
        statusFilter.addValueChangeListener(event -> dataView.refreshAll());

        HeaderRow filterRow = grid.appendHeaderRow();
        filterRow.getCell(titleColumn).setComponent(titleFilter);
        filterRow.getCell(statusColumn).setComponent(statusFilter);

        add(newCase, grid);
    }

    private boolean matchesFilters(Case aCase) {
        String titleText = titleFilter.getValue().strip().toLowerCase(Locale.ROOT);
        boolean titleMatches = titleText.isEmpty() || aCase.getTitle().toLowerCase(Locale.ROOT).contains(titleText);
        CaseStatus status = statusFilter.getValue();
        boolean statusMatches = status == null || aCase.getStatus() == status;
        return titleMatches && statusMatches;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=CaseListViewTest`
Expected: PASS, 14 tests, 0 failures, 0 errors.

If `$(TextField.class).id("title-filter")` throws `NoSuchElementException` (header-row components not found by the browserless locator), move the two filters out of the header row into a `HorizontalLayout` placed between `newCase` and `grid` (`add(newCase, new HorizontalLayout(titleFilter, statusFilter), grid)`, remove the `HeaderRow` lines), rerun, and note the change in the commit message. Do not change the tests.

If `@Nested` classes fail with Spring or Vaadin setup errors (for example "no current UI"), report it instead of restructuring the tests.

- [ ] **Step 6: Run the whole suite**

Run: `mvn test`
Expected: PASS, all tests from Tasks 1–3, 0 failures, 0 errors.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/java src/test/java
git commit -m "Add case list view with filters and sorting, and browserless test setup"
```

---

### Task 4: Case form — initial state, validation, save/submit, not found

**Files:**
- Create: `src/main/java/com/example/application/views/cases/CaseView.java`
- Test: `src/test/java/com/example/application/views/cases/CaseViewTest.java`
- Modify: `src/test/java/com/example/application/views/cases/CaseListViewTest.java` (add `Navigation`)

**Interfaces:**
- Consumes: Task 1 domain, Task 2 `CaseService` (`findById`, `save`, `apply`), Task 3 `CurrentUser`, `CaseListView`, `AbstractViewTest`.
- Produces: `CaseView` — route `case/:caseId` (`new` or numeric id), `HasDynamicTitle` returning "New case" or "Case <id>"; heading `H2` id `case-heading` with the same text; status `Span` id `status`; fields `title` (`TextField`), `category` (`ComboBox<CaseCategory>`), `amount` (`IntegerField`), `requested-date` (`DatePicker`), `description` (`TextArea`), `urgent` (`Checkbox`); buttons `save`, `submit`, `delete`, `start-review`, `approve`, `reject`, `back`. In this task `delete`, `start-review`, `approve`, `reject` only get visibility; Task 5 adds their click handling. Private method `runAction(Runnable action, String successMessage)` — Task 5 reuses it.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/example/application/views/cases/CaseViewTest.java`:

```java
package com.example.application.views.cases;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.example.application.domain.Case;
import com.example.application.domain.CaseAction;
import com.example.application.domain.CaseActions;
import com.example.application.domain.CaseCategory;
import com.example.application.domain.CaseStatus;
import com.example.application.domain.Role;
import com.example.application.views.AbstractViewTest;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Browserless tests for the case form. The workflow rules themselves are tested in CaseActionsTest; these tests
 * check that the form shows what the rules say, validates input and performs the actions.
 */
@DisplayName("Case form")
class CaseViewTest extends AbstractViewTest {

    /** Button id per action, so expectations can be derived from CaseActions instead of repeating the rules. */
    static final Map<CaseAction, String> BUTTON_IDS = Map.of(
            CaseAction.SAVE, "save",
            CaseAction.SUBMIT, "submit",
            CaseAction.DELETE, "delete",
            CaseAction.START_REVIEW, "start-review",
            CaseAction.APPROVE, "approve",
            CaseAction.REJECT, "reject");

    /** A seed case for every status. */
    static final Map<CaseStatus, String> SEED_CASE_BY_STATUS = Map.of(
            CaseStatus.DRAFT, "Laptop for new developer",
            CaseStatus.SUBMITTED, "Vaadin training course",
            CaseStatus.IN_REVIEW, "Customer visit in Bergen",
            CaseStatus.APPROVED, "Monitor upgrade",
            CaseStatus.REJECTED, "Team offsite travel");

    static final int SEED_SIZE = 8;

    @Nested
    @DisplayName("when the page is presented")
    class InitialState {

        @Test
        @DisplayName("a new case is empty, editable and can be saved or submitted")
        void newCase() {
            openNewCase();

            // 1. Right page
            assertThat(heading()).isEqualTo("New case");
            // 2. Content
            assertThat(titleField().getValue()).isEmpty();
            assertThat(categoryField().getValue()).isNull();
            assertThat(amountField().getValue()).isNull();
            assertThat(requestedDateField().getValue()).isNull();
            assertThat(descriptionField().getValue()).isEmpty();
            assertThat(urgentField().getValue()).isFalse();
            assertThat(statusText()).isEqualTo("Draft");
            // 3. Editability
            assertThat(formFields()).noneMatch(HasValue::isReadOnly);
            // 4. Actions (a new case cannot be deleted yet)
            assertThat(visibleActionButtons()).containsExactlyInAnyOrder("save", "submit");
            assertThat(isVisible("back")).isTrue();
            // 5. No errors at start
            assertThat(invalidFields()).isEmpty();
            assertThat(isNotificationOpen()).isFalse();
        }

        @Test
        @DisplayName("a draft shows its values and can be saved, submitted or deleted")
        void draftCase() {
            openCase("Laptop for new developer");

            assertThat(heading()).isEqualTo("Case " + caseIdByTitle("Laptop for new developer"));
            assertThat(titleField().getValue()).isEqualTo("Laptop for new developer");
            assertThat(categoryField().getValue()).isEqualTo(CaseCategory.PURCHASE);
            assertThat(amountField().getValue()).isEqualTo(18000);
            assertThat(requestedDateField().getValue()).isEqualTo(LocalDate.now().plusDays(14));
            assertThat(urgentField().getValue()).isFalse();
            assertThat(statusText()).isEqualTo("Draft");
            assertThat(formFields()).noneMatch(HasValue::isReadOnly);
            assertThat(visibleActionButtons()).containsExactlyInAnyOrder("save", "submit", "delete");
            assertThat(invalidFields()).isEmpty();
        }

        @Test
        @DisplayName("a submitted case is read-only for the submitter")
        void submittedCaseIsReadOnlyForSubmitter() {
            openCase("Vaadin training course");

            assertThat(statusText()).isEqualTo("Submitted");
            assertThat(formFields()).allMatch(HasValue::isReadOnly);
            assertThat(visibleActionButtons()).isEmpty();
            assertThat(isVisible("back")).isTrue();
        }

        @Test
        @DisplayName("a draft is read-only for a case handler")
        void draftIsReadOnlyForCaseHandler() {
            actAs(Role.CASE_HANDLER);
            openCase("Laptop for new developer");

            assertThat(formFields()).allMatch(HasValue::isReadOnly);
            assertThat(visibleActionButtons()).isEmpty();
        }
    }

    @Nested
    @DisplayName("visible buttons")
    class ButtonVisibility {

        @ParameterizedTest(name = "{1} viewing a {0} case")
        @MethodSource("com.example.application.views.cases.CaseViewTest#statusAndRole")
        @DisplayName("are exactly the actions the rules allow")
        void showsExactlyTheAllowedActions(CaseStatus status, Role role) {
            actAs(role);
            openCase(SEED_CASE_BY_STATUS.get(status));

            Set<String> expected = CaseActions.allowed(status, role).stream().map(BUTTON_IDS::get).collect(toSet());
            assertThat(visibleActionButtons()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @BeforeEach
        void openEmptyForm() {
            openNewCase();
        }

        @ParameterizedTest(name = "title \"{0}\"")
        @MethodSource("com.example.application.views.cases.CaseViewTest#invalidTitles")
        @DisplayName("rejects an invalid title")
        void rejectsInvalidTitle(String title, String expectedMessage) {
            fillValidForm();
            test(titleField()).setValue(title);

            click("save");

            assertInvalid(titleField(), expectedMessage);
        }

        @Test
        @DisplayName("requires a category")
        void requiresCategory() {
            fillForm("Printer for the office", null, 2500, LocalDate.now().plusDays(7));

            click("save");

            assertInvalid(categoryField(), "Category is required");
        }

        @Test
        @DisplayName("requires an amount")
        void requiresAmount() {
            fillForm("Printer for the office", "Purchase", null, LocalDate.now().plusDays(7));

            click("save");

            assertInvalid(amountField(), "Amount is required");
        }

        @ParameterizedTest(name = "amount {0}")
        @ValueSource(ints = {0, -5, 50001})
        @DisplayName("rejects an amount outside 1-50000")
        void rejectsAmountOutOfRange(int amount) {
            fillForm("Printer for the office", "Purchase", amount, LocalDate.now().plusDays(7));

            click("save");

            assertInvalid(amountField(), "Amount must be between 1 and 50000");
        }

        @ParameterizedTest(name = "amount {0}")
        @ValueSource(ints = {1, 50000})
        @DisplayName("accepts the amount boundaries")
        void acceptsAmountBoundaries(int amount) {
            fillForm("Printer for the office", "Purchase", amount, LocalDate.now().plusDays(7));

            click("save");

            assertThat(caseService.findAll()).hasSize(SEED_SIZE + 1);
        }

        @Test
        @DisplayName("requires a requested date")
        void requiresRequestedDate() {
            fillForm("Printer for the office", "Purchase", 2500, null);

            click("save");

            assertInvalid(requestedDateField(), "Requested date is required");
        }

        @Test
        @DisplayName("rejects a requested date in the past")
        void rejectsPastDate() {
            fillForm("Printer for the office", "Purchase", 2500, LocalDate.now().minusDays(1));

            click("save");

            assertInvalid(requestedDateField(), "Requested date cannot be in the past");
        }

        @Test
        @DisplayName("accepts today as requested date")
        void acceptsToday() {
            fillForm("Printer for the office", "Purchase", 2500, LocalDate.now());

            click("save");

            assertThat(caseService.findAll()).hasSize(SEED_SIZE + 1);
        }

        @Test
        @DisplayName("rejects a description over 1000 characters")
        void rejectsLongDescription() {
            fillValidForm();
            test(descriptionField()).setValue("x".repeat(1001));

            click("save");

            assertInvalid(descriptionField(), "Description must be at most 1000 characters");
        }

        private void assertInvalid(HasValidation field, String expectedMessage) {
            assertThat(field.isInvalid()).isTrue();
            assertThat(field.getErrorMessage()).isEqualTo(expectedMessage);
            assertThat(caseService.findAll()).hasSize(SEED_SIZE);
            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
        }
    }

    @Nested
    @DisplayName("saving and submitting")
    class Workflow {

        @Test
        @DisplayName("saving a new case stores it as a draft and returns to the list")
        void saveNewCase() {
            openNewCase();
            fillValidForm();
            test(titleField()).setValue("  Printer for the office  ");

            click("save");

            assertThat(lastNotificationText()).isEqualTo("Case saved");
            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            Case saved = caseService.findById(caseIdByTitle("Printer for the office")).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(CaseStatus.DRAFT);
            assertThat(saved.getAmount()).isEqualTo(2500);
        }

        @Test
        @DisplayName("saving an existing draft stores the changes")
        void saveExistingDraft() {
            openCase("Laptop for new developer");
            test(amountField()).setValue(19000);

            click("save");

            assertThat(caseService.findById(caseIdByTitle("Laptop for new developer")))
                    .get().extracting(Case::getAmount).isEqualTo(19000);
        }

        @Test
        @DisplayName("submitting a new case stores it as submitted")
        void submitNewCase() {
            openNewCase();
            fillValidForm();

            click("submit");

            assertThat(lastNotificationText()).isEqualTo("Case submitted");
            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            assertThat(caseService.findById(caseIdByTitle("Printer for the office")))
                    .get().extracting(Case::getStatus).isEqualTo(CaseStatus.SUBMITTED);
        }

        @Test
        @DisplayName("submitting an invalid form stores nothing")
        void submitInvalidForm() {
            openNewCase();

            click("submit");

            assertThat(titleField().isInvalid()).isTrue();
            assertThat(caseService.findAll()).hasSize(SEED_SIZE);
        }

        @Test
        @DisplayName("going back discards unsaved changes")
        void backDiscardsChanges() {
            openCase("Laptop for new developer");
            test(titleField()).setValue("Changed but not saved");

            click("back");

            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            assertThat(caseService.findById(caseIdByTitle("Laptop for new developer"))).isPresent();
        }
    }

    @Nested
    @DisplayName("an unknown case")
    class NotFound {

        @ParameterizedTest(name = "case/{0}")
        @ValueSource(strings = {"999", "abc", "-1"})
        @DisplayName("shows a notification and returns to the list")
        void redirectsToList(String caseId) {
            navigate("case/" + caseId, CaseListView.class);

            assertThat(lastNotificationText()).isEqualTo("Case not found");
        }
    }

    static Stream<Arguments> statusAndRole() {
        return Stream.of(CaseStatus.values())
                .flatMap(status -> Stream.of(Role.values()).map(role -> arguments(status, role)));
    }

    static Stream<Arguments> invalidTitles() {
        return Stream.of(
                arguments("", "Title is required"),
                arguments("     ", "Title is required"),
                arguments("abcd", "Title must be 5-100 characters"),
                arguments("  abc  ", "Title must be 5-100 characters"),
                arguments("x".repeat(101), "Title must be 5-100 characters"));
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    void openNewCase() {
        navigate("case/new", CaseView.class);
    }

    void openCase(String title) {
        navigate("case/" + caseIdByTitle(title), CaseView.class);
    }

    void fillValidForm() {
        fillForm("Printer for the office", "Purchase", 2500, LocalDate.now().plusDays(7));
    }

    /** Fills the form of a new case; null values are left empty. */
    void fillForm(String title, String categoryLabel, Integer amount, LocalDate requestedDate) {
        if (title != null) {
            test(titleField()).setValue(title);
        }
        if (categoryLabel != null) {
            test(categoryField()).selectItem(categoryLabel);
        }
        if (amount != null) {
            test(amountField()).setValue(amount);
        }
        if (requestedDate != null) {
            test(requestedDateField()).setValue(requestedDate);
        }
    }

    void click(String buttonId) {
        test($(Button.class).id(buttonId)).click();
    }

    boolean isVisible(String componentId) {
        return $(Button.class).withId(componentId).exists();
    }

    /** Ids of the visible action buttons. Browserless queries only find visible components. */
    Set<String> visibleActionButtons() {
        return $(Button.class).all().stream()
                .map(button -> button.getId().orElse(""))
                .filter(BUTTON_IDS.values()::contains)
                .collect(toSet());
    }

    String heading() {
        return $(H2.class).id("case-heading").getText();
    }

    String statusText() {
        return $(Span.class).id("status").getText();
    }

    List<AbstractField<?, ?>> formFields() {
        return List.of(titleField(), categoryField(), amountField(), requestedDateField(), descriptionField(),
                urgentField());
    }

    List<HasValidation> invalidFields() {
        return Stream.<HasValidation>of(titleField(), categoryField(), amountField(), requestedDateField(),
                descriptionField()).filter(HasValidation::isInvalid).toList();
    }

    TextField titleField() {
        return $(TextField.class).id("title");
    }

    @SuppressWarnings("unchecked")
    ComboBox<CaseCategory> categoryField() {
        return $(ComboBox.class).id("category");
    }

    IntegerField amountField() {
        return $(IntegerField.class).id("amount");
    }

    DatePicker requestedDateField() {
        return $(DatePicker.class).id("requested-date");
    }

    TextArea descriptionField() {
        return $(TextArea.class).id("description");
    }

    Checkbox urgentField() {
        return $(Checkbox.class).id("urgent");
    }
}
```

Note: `ComboBox`, `IntegerField`, `DatePicker` and `TextField` are `AbstractField` subclasses in Flow; if `formFields()` does not compile because one of them is not an `AbstractField<?, ?>` in 25.2, change its element type to `HasValue<?, ?>` (that is all the tests need).

Add to `CaseListViewTest` the import `import com.vaadin.flow.component.html.H2;` and this nested class (next to the other `@Nested` classes; `CaseView` is in the same package, so it needs no import):

```java
    @Nested
    @DisplayName("navigation")
    class Navigation {

        @Test
        @DisplayName("clicking a row opens that case")
        void clickingRowOpensCase() {
            navigate(CaseListView.class);

            test(grid()).clickRow(0);

            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
            assertThat($(H2.class).id("case-heading").getText())
                    .isEqualTo("Case " + caseIdByTitle("Laptop for new developer"));
        }

        @Test
        @DisplayName("New case opens an empty form")
        void newCaseOpensEmptyForm() {
            navigate(CaseListView.class);

            test($(Button.class).id("new-case")).click();

            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
            assertThat($(H2.class).id("case-heading").getText()).isEqualTo("New case");
        }
    }
```


- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest='CaseViewTest,CaseListViewTest'`
Expected: FAIL — compilation error, `cannot find symbol: class CaseView`.

- [ ] **Step 3: Write the implementation**

`src/main/java/com/example/application/views/cases/CaseView.java`:

```java
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
    private final Button delete = new Button("Delete");
    private final Button startReview = new Button("Start review");
    private final Button approve = new Button("Approve");
    private final Button reject = new Button("Reject");
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest='CaseViewTest,CaseListViewTest'`
Expected: PASS, 0 failures, 0 errors.

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`
Expected: PASS, 0 failures, 0 errors.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/application/views/cases/CaseView.java src/test/java/com/example/application/views/cases
git commit -m "Add case form with validation, save/submit and status-driven buttons"
```

---

### Task 5: Case form — start review, approve, reject, delete, stale actions

**Files:**
- Create: `src/main/java/com/example/application/views/cases/RejectDialog.java`
- Modify: `src/main/java/com/example/application/views/cases/CaseView.java`
- Modify: `src/test/java/com/example/application/views/cases/CaseViewTest.java`

**Interfaces:**
- Consumes: Task 4 `CaseView` (`runAction`, the buttons), Task 2 `CaseService` (`apply`, `reject`, `delete`).
- Produces: `class RejectDialog extends Dialog` (package-private) — constructor `RejectDialog(Consumer<String> onReject)`; ids `reject-dialog`, `rejection-reason`, `confirm-reject`, `cancel-reject`; blank reason → field invalid with "A reason is required", dialog stays open. `CaseView` delete opens a `ConfirmDialog` with id `delete-confirm`. Notifications: "Review started", "Case approved", "Case rejected", "Case deleted".

- [ ] **Step 1: Write the failing tests**

In `CaseViewTest`, add these imports:

```java
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
```

Add these methods to the existing nested class `Workflow`:

```java
        @Test
        @DisplayName("a case handler can start the review of a submitted case")
        void startReview() {
            actAs(Role.CASE_HANDLER);
            openCase("Vaadin training course");

            click("start-review");

            assertThat(lastNotificationText()).isEqualTo("Review started");
            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            assertThat(statusOf("Vaadin training course")).isEqualTo(CaseStatus.IN_REVIEW);
        }

        @Test
        @DisplayName("a case handler can approve a case in review")
        void approve() {
            actAs(Role.CASE_HANDLER);
            openCase("Customer visit in Bergen");

            click("approve");

            assertThat(lastNotificationText()).isEqualTo("Case approved");
            assertThat(statusOf("Customer visit in Bergen")).isEqualTo(CaseStatus.APPROVED);
        }

        @Test
        @DisplayName("rejecting without a reason is blocked in the dialog")
        void rejectWithoutReasonIsBlocked() {
            actAs(Role.CASE_HANDLER);
            openCase("Security certification");

            click("reject");
            click("confirm-reject");

            TextArea reason = $(TextArea.class).id("rejection-reason");
            assertThat(reason.isInvalid()).isTrue();
            assertThat(reason.getErrorMessage()).isEqualTo("A reason is required");
            assertThat($(Dialog.class).withId("reject-dialog").exists()).isTrue();
            assertThat(statusOf("Security certification")).isEqualTo(CaseStatus.IN_REVIEW);
        }

        @Test
        @DisplayName("a reason of only spaces counts as no reason")
        void rejectWithBlankReasonIsBlocked() {
            actAs(Role.CASE_HANDLER);
            openCase("Security certification");

            click("reject");
            test($(TextArea.class).id("rejection-reason")).setValue("   ");
            click("confirm-reject");

            assertThat($(TextArea.class).id("rejection-reason").isInvalid()).isTrue();
            assertThat(statusOf("Security certification")).isEqualTo(CaseStatus.IN_REVIEW);
        }

        @Test
        @DisplayName("rejecting with a reason stores the reason")
        void rejectWithReason() {
            actAs(Role.CASE_HANDLER);
            openCase("Security certification");

            click("reject");
            test($(TextArea.class).id("rejection-reason")).setValue("Not in this year's budget");
            click("confirm-reject");

            assertThat(lastNotificationText()).isEqualTo("Case rejected");
            assertThat($(Dialog.class).withId("reject-dialog").exists()).isFalse();
            Case rejected = caseService.findById(caseIdByTitle("Security certification")).orElseThrow();
            assertThat(rejected.getStatus()).isEqualTo(CaseStatus.REJECTED);
            assertThat(rejected.getRejectionReason()).isEqualTo("Not in this year's budget");
        }

        @Test
        @DisplayName("cancelling the reject dialog changes nothing")
        void cancelReject() {
            actAs(Role.CASE_HANDLER);
            openCase("Security certification");

            click("reject");
            click("cancel-reject");

            assertThat($(Dialog.class).withId("reject-dialog").exists()).isFalse();
            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
            assertThat(statusOf("Security certification")).isEqualTo(CaseStatus.IN_REVIEW);
        }

        @Test
        @DisplayName("cancelling the delete confirmation keeps the case")
        void cancelDelete() {
            openCase("Laptop for new developer");

            click("delete");
            test($(ConfirmDialog.class).id("delete-confirm")).cancel();

            assertThat(caseService.findAll()).hasSize(SEED_SIZE);
            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
        }

        @Test
        @DisplayName("confirming the delete removes the case")
        void confirmDelete() {
            openCase("Laptop for new developer");

            click("delete");
            test($(ConfirmDialog.class).id("delete-confirm")).confirm();

            assertThat(lastNotificationText()).isEqualTo("Case deleted");
            assertThat(getCurrentView()).isInstanceOf(CaseListView.class);
            assertThat(caseService.findAll()).hasSize(SEED_SIZE - 1);
        }
```

Add this nested class to `CaseViewTest` (next to `NotFound`):

```java
    @Nested
    @DisplayName("when the case changed after the page was loaded")
    class StaleAction {

        @Test
        @DisplayName("the action is refused and the page shows the current state")
        void actionOnChangedCaseIsRefused() {
            actAs(Role.CASE_HANDLER);
            openCase("Customer visit in Bergen");
            // Someone else approves the case while this page is open.
            caseService.apply(caseIdByTitle("Customer visit in Bergen"), CaseAction.APPROVE, Role.CASE_HANDLER);

            click("approve");

            assertThat(lastNotificationText()).isEqualTo("This action is no longer allowed");
            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
            assertThat(statusText()).isEqualTo("Approved");
            assertThat(visibleActionButtons()).isEmpty();
        }
    }
```

Add this helper next to the other helpers:

```java
    CaseStatus statusOf(String title) {
        return caseService.findById(caseIdByTitle(title)).orElseThrow().getStatus();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=CaseViewTest`
Expected: FAIL — the new tests fail (for example `startReview` with an assertion on the notification, `rejectWithoutReasonIsBlocked` with `NoSuchElementException` for `confirm-reject`), because the buttons have no click handling yet. Existing tests still pass.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/example/application/views/cases/RejectDialog.java`:

```java
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
```

In `CaseView.java`, add the import:

```java
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
```

Replace the four button fields:

```java
    private final Button delete = new Button("Delete");
    private final Button startReview = new Button("Start review");
    private final Button approve = new Button("Approve");
    private final Button reject = new Button("Reject");
```

with:

```java
    private final Button delete = new Button("Delete", event -> confirmDelete());
    private final Button startReview = new Button("Start review", event -> startReview());
    private final Button approve = new Button("Approve", event -> approve());
    private final Button reject = new Button("Reject", event -> openRejectDialog());
```

Add these methods after `submit()`:

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=CaseViewTest`
Expected: PASS, 0 failures, 0 errors.

If `actionOnChangedCaseIsRefused` fails because `refreshCurrentRoute(false)` does not rebuild the view in the browserless environment, report the failure with the stack trace instead of changing the test.

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`
Expected: PASS, 0 failures, 0 errors.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/application/views/cases src/test/java/com/example/application/views/cases
git commit -m "Add review, approve, reject and delete actions to the case form"
```

---

### Task 6: Role selector

**Files:**
- Modify: `src/main/java/com/example/application/views/MainLayout.java`
- Modify: `src/test/java/com/example/application/views/cases/CaseListViewTest.java`
- Modify: `src/test/java/com/example/application/views/cases/CaseViewTest.java`

**Interfaces:**
- Consumes: Task 3 `CurrentUser`, `CaseListView`; Task 4/5 `CaseView`.
- Produces: `Select<Role>` with id `role-selector` in the navbar, labels "Submitter" / "Case handler", initial value `currentUser.getRole()`. Changing it calls `currentUser.setRole(...)` and `UI.getCurrent().refreshCurrentRoute(false)`.

- [ ] **Step 1: Write the failing tests**

Add to `CaseListViewTest` (`Select`, `Role` and `Button` are already imported):

```java
    @Nested
    @DisplayName("switching role")
    class RoleSwitch {

        @Test
        @DisplayName("to case handler hides the New case button")
        void switchingToCaseHandlerHidesNewCase() {
            navigate(CaseListView.class);

            test(roleSelector()).selectItem("Case handler");

            assertThat($(Button.class).withId("new-case").exists()).isFalse();
        }
    }

    @SuppressWarnings("unchecked")
    private Select<Role> roleSelector() {
        return $(Select.class).id("role-selector");
    }
```

Add to `CaseViewTest` (import `com.vaadin.flow.component.select.Select`):

```java
    @Nested
    @DisplayName("switching role")
    class RoleSwitch {

        @Test
        @DisplayName("the selector starts at the current role")
        void selectorShowsCurrentRole() {
            actAs(Role.CASE_HANDLER);
            openCase("Customer visit in Bergen");

            assertThat(roleSelector().getValue()).isEqualTo(Role.CASE_HANDLER);
        }

        @Test
        @DisplayName("rebuilds the page with the new role's buttons")
        void switchingRoleRebuildsButtons() {
            openCase("Customer visit in Bergen");
            assertThat(visibleActionButtons()).isEmpty();

            test(roleSelector()).selectItem("Case handler");

            assertThat(getCurrentView()).isInstanceOf(CaseView.class);
            assertThat(visibleActionButtons()).containsExactlyInAnyOrder("approve", "reject");
        }
    }
```

and the helper:

```java
    @SuppressWarnings("unchecked")
    Select<Role> roleSelector() {
        return $(Select.class).id("role-selector");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest='CaseListViewTest,CaseViewTest'`
Expected: FAIL — the `RoleSwitch` tests fail with `NoSuchElementException` for `role-selector`.

- [ ] **Step 3: Write the implementation**

In `MainLayout.java` add the imports:

```java
import com.example.application.domain.Role;
import com.example.application.service.CurrentUser;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
```

Add a field and change the constructor:

```java
    private final CurrentUser currentUser;
    private H1 viewTitle;

    public MainLayout(CurrentUser currentUser) {
        this.currentUser = currentUser;
        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }
```

(The existing `private H1 viewTitle;` line stays; only the constructor signature and the `currentUser` field are new.)

Replace `addHeaderContent()` with:

```java
    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menu toggle");

        viewTitle = new H1();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        addToNavbar(true, toggle, viewTitle, createRoleSelector());
    }

    /**
     * Stands in for a login: lets the user choose which role to act as. The current view is rebuilt so it reflects
     * the new role.
     */
    private HorizontalLayout createRoleSelector() {
        Select<Role> roleSelector = new Select<>();
        roleSelector.setId("role-selector");
        roleSelector.setAriaLabel("Act as");
        roleSelector.setItems(Role.values());
        roleSelector.setItemLabelGenerator(Role::getLabel);
        roleSelector.setValue(currentUser.getRole());
        roleSelector.addValueChangeListener(event -> {
            currentUser.setRole(event.getValue());
            UI.getCurrent().refreshCurrentRoute(false);
        });

        HorizontalLayout layout = new HorizontalLayout(new Span("Act as"), roleSelector);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.addClassNames(LumoUtility.Margin.Left.AUTO, LumoUtility.Margin.Right.MEDIUM);
        return layout;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test`
Expected: PASS, 0 failures, 0 errors.

- [ ] **Step 5: Check the app by hand**

Run: `mvn spring-boot:run` (in the background), open http://localhost:8080, and check: the case list shows 8 cases; "Act as" in the top bar switches between Submitter and Case handler; "New case" disappears for Case handler; opening a case shows the expected buttons. Stop the app afterwards.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/application/views/MainLayout.java src/test/java/com/example/application/views/cases
git commit -m "Add role selector to the main layout"
```

---

### Task 7: End-to-end setup, page objects and the list test

**Files:**
- Modify: `pom.xml` (add `vaadin-testbench-junit6`, extend the `it` profile)
- Create: `src/test/java/com/example/application/it/AbstractIT.java`
- Create: `src/test/java/com/example/application/it/pages/CaseListPage.java`
- Create: `src/test/java/com/example/application/it/pages/CasePage.java`
- Create: `src/test/java/com/example/application/it/pages/RoleSelector.java`
- Test: `src/test/java/com/example/application/it/CaseListIT.java`

**Interfaces:**
- Consumes: the running application (Tasks 1–6) and its component ids.
- Produces:
  - `abstract class AbstractIT extends BrowserTestBase implements DriverSupplier` — `protected void open(String route)`, `protected static String uniqueTitle(String prefix)`, `protected String firstNotificationText()`.
  - `CaseListPage(HasElementQuery page)` — `void filterByTitle(String)`, `int rowCount()`, `String titleOfRow(int)`, `String statusOfRow(int)`, `boolean isNewCaseButtonVisible()`, `CasePage clickNewCase()`, `CasePage openRow(int)`.
  - `CasePage(HasElementQuery page)` — `void fill(String title, String categoryLabel, int amount, LocalDate requestedDate)`, `void setTitle(String)`, `String status()`, `String titleErrorMessage()`, `void save()`, `void submit()`, `void startReview()`, `void approve()`, `void reject()`, `void confirmReject(String reasonOrNull)`, `boolean isRejectDialogOpen()`, `String rejectionReasonErrorMessage()`.
  - `RoleSelector(HasElementQuery page)` — `void actAs(String roleLabel)`.

- [ ] **Step 1: Check the license (manual prerequisite)**

Run: `ls ~/.vaadin/proKey ~/.vaadin/offlineKey 2>&1`
Expected: at least one of the two files exists.

If neither exists: STOP and ask the user to log in to vaadin.com once (start the app with `mvn spring-boot:run`, follow the license prompt in the browser) or to download an offline key into `~/.vaadin/offlineKey`. Continue only after the file exists.

- [ ] **Step 2: Add the TestBench dependency and extend the `it` profile**

In `pom.xml`, add after the `browserless-test-spring` dependency:

```xml
        <dependency>
            <groupId>com.vaadin</groupId>
            <artifactId>vaadin-testbench-junit6</artifactId>
            <version>${vaadin.version}</version>
            <scope>test</scope>
        </dependency>
```

In the `it` profile, add a `<properties>` block directly after `<id>it</id>`:

```xml
            <properties>
                <!-- The app under test gets its own port, so a dev server on 8080 can keep running. -->
                <it.port>8081</it.port>
                <!-- Run with -Dheadless=false to watch the browser. -->
                <headless>true</headless>
            </properties>
```

Replace the `start-spring-boot` execution with:

```xml
                            <execution>
                                <id>start-spring-boot</id>
                                <phase>pre-integration-test</phase>
                                <goals>
                                    <goal>start</goal>
                                </goals>
                                <configuration>
                                    <arguments>
                                        <argument>--server.port=${it.port}</argument>
                                        <argument>--vaadin.launch-browser=false</argument>
                                    </arguments>
                                    <maxAttempts>120</maxAttempts>
                                </configuration>
                            </execution>
```

In the `maven-failsafe-plugin` `<configuration>`, add:

```xml
                            <systemPropertyVariables>
                                <it.port>${it.port}</it.port>
                                <headless>${headless}</headless>
                            </systemPropertyVariables>
```

- [ ] **Step 3: Write the base class and page objects**

`src/test/java/com/example/application/it/AbstractIT.java`:

```java
package com.example.application.it;

import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.testbench.BrowserTestBase;
import com.vaadin.testbench.DriverSupplier;
import java.util.UUID;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Base class for end-to-end tests in a real Chrome. The Maven "it" profile starts the application before the tests
 * run: {@code mvn verify -Pit} (add {@code -Dheadless=false} to watch). Selenium Manager downloads Chrome for Testing
 * and a matching driver on the first run. TestBench waits for Vaadin to finish each server round trip, so the tests
 * never sleep. BrowserTestBase takes a screenshot into error-screenshots/ when a test fails and quits the driver
 * afterwards.
 */
public abstract class AbstractIT extends BrowserTestBase implements DriverSupplier {

    private static final int PORT = Integer.getInteger("it.port", 8081);

    @Override
    public WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        if (Boolean.parseBoolean(System.getProperty("headless", "true"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1400,1000");
        return new ChromeDriver(options);
    }

    protected void open(String route) {
        getDriver().get("http://localhost:" + PORT + "/" + route);
    }

    /**
     * The application keeps running between tests, so tests cannot reset its data. Each test creates its own case
     * with a unique title instead, which also keeps the tests independent of each other.
     */
    protected static String uniqueTitle(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    protected String firstNotificationText() {
        return $(NotificationElement.class).waitForFirst().getText();
    }
}
```

`src/test/java/com/example/application/it/pages/RoleSelector.java`:

```java
package com.example.application.it.pages;

import com.vaadin.flow.component.select.testbench.SelectElement;
import com.vaadin.testbench.HasElementQuery;

/** The "Act as" selector in the top bar. */
public class RoleSelector {

    private final HasElementQuery page;

    public RoleSelector(HasElementQuery page) {
        this.page = page;
    }

    public void actAs(String roleLabel) {
        page.$(SelectElement.class).id("role-selector").selectByText(roleLabel);
    }
}
```

`src/test/java/com/example/application/it/pages/CaseListPage.java`:

```java
package com.example.application.it.pages;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.grid.testbench.GridElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.testbench.HasElementQuery;

/**
 * Page object for the case list. Tests talk to this class in terms of what a user does; only this class knows ids
 * and column positions.
 */
public class CaseListPage {

    private static final int TITLE_COLUMN = 0;
    private static final int STATUS_COLUMN = 4;

    private final HasElementQuery page;

    public CaseListPage(HasElementQuery page) {
        this.page = page;
    }

    public void filterByTitle(String text) {
        page.$(TextFieldElement.class).id("title-filter").setValue(text);
    }

    public int rowCount() {
        return grid().getRowCount();
    }

    public String titleOfRow(int row) {
        return grid().getCell(row, TITLE_COLUMN).getText();
    }

    public String statusOfRow(int row) {
        return grid().getCell(row, STATUS_COLUMN).getText();
    }

    public boolean isNewCaseButtonVisible() {
        // Unlike browserless queries, the browser's DOM still contains components hidden with setVisible(false),
        // so check whether the button is actually displayed.
        return page.$(ButtonElement.class).withId("new-case").all().stream().anyMatch(ButtonElement::isDisplayed);
    }

    public CasePage clickNewCase() {
        page.$(ButtonElement.class).id("new-case").click();
        return new CasePage(page);
    }

    public CasePage openRow(int row) {
        grid().getCell(row, TITLE_COLUMN).click();
        return new CasePage(page);
    }

    private GridElement grid() {
        return page.$(GridElement.class).id("cases-grid");
    }
}
```

`src/test/java/com/example/application/it/pages/CasePage.java`:

```java
package com.example.application.it.pages;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.component.datepicker.testbench.DatePickerElement;
import com.vaadin.flow.component.dialog.testbench.DialogElement;
import com.vaadin.flow.component.html.testbench.SpanElement;
import com.vaadin.flow.component.textfield.testbench.IntegerFieldElement;
import com.vaadin.flow.component.textfield.testbench.TextAreaElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.testbench.HasElementQuery;
import com.vaadin.testbench.TestBenchElement;
import java.time.LocalDate;

/** Page object for the case form, including its reject dialog. */
public class CasePage {

    private final HasElementQuery page;

    public CasePage(HasElementQuery page) {
        this.page = page;
    }

    public void fill(String title, String categoryLabel, int amount, LocalDate requestedDate) {
        setTitle(title);
        page.$(ComboBoxElement.class).id("category").selectByText(categoryLabel);
        page.$(IntegerFieldElement.class).id("amount").setValue(String.valueOf(amount));
        page.$(DatePickerElement.class).id("requested-date").setDate(requestedDate);
    }

    public void setTitle(String title) {
        page.$(TextFieldElement.class).id("title").setValue(title);
    }

    public String status() {
        return page.$(SpanElement.class).id("status").getText();
    }

    public String titleErrorMessage() {
        return errorMessageOf(page.$(TextFieldElement.class).id("title"));
    }

    public void save() {
        click("save");
    }

    public void submit() {
        click("submit");
    }

    public void startReview() {
        click("start-review");
    }

    public void approve() {
        click("approve");
    }

    public void reject() {
        click("reject");
    }

    /** Confirms the reject dialog, optionally typing a reason first. */
    public void confirmReject(String reasonOrNull) {
        if (reasonOrNull != null) {
            page.$(TextAreaElement.class).id("rejection-reason").setValue(reasonOrNull);
        }
        click("confirm-reject");
    }

    public boolean isRejectDialogOpen() {
        return page.$(DialogElement.class).withId("reject-dialog").all().stream().anyMatch(DialogElement::isOpen);
    }

    public String rejectionReasonErrorMessage() {
        return errorMessageOf(page.$(TextAreaElement.class).id("rejection-reason"));
    }

    private void click(String buttonId) {
        page.$(ButtonElement.class).id(buttonId).click();
    }

    /** Vaadin fields expose their validation state as the "invalid" and "errorMessage" properties. */
    private static String errorMessageOf(TestBenchElement field) {
        return Boolean.TRUE.equals(field.getPropertyBoolean("invalid")) ? field.getPropertyString("errorMessage") : "";
    }
}
```

- [ ] **Step 4: Write the list test**

`src/test/java/com/example/application/it/CaseListIT.java`:

```java
package com.example.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.it.pages.CaseListPage;
import com.example.application.it.pages.RoleSelector;
import com.vaadin.testbench.BrowserTest;

/** End-to-end: the case list in a real browser. The details are covered by CaseListViewTest. */
class CaseListIT extends AbstractIT {

    @BrowserTest
    void filteringFindsASeedCase() {
        open("");
        CaseListPage list = new CaseListPage(this);

        list.filterByTitle("Monitor upgrade");

        assertThat(list.rowCount()).isEqualTo(1);
        assertThat(list.statusOfRow(0)).isEqualTo("Approved");
    }

    @BrowserTest
    void newCaseButtonDisappearsForCaseHandler() {
        open("");
        CaseListPage list = new CaseListPage(this);
        assertThat(list.isNewCaseButtonVisible()).isTrue();

        new RoleSelector(this).actAs("Case handler");

        assertThat(list.isNewCaseButtonVisible()).isFalse();
    }
}
```

- [ ] **Step 5: Run the end-to-end tests**

Make sure nothing else listens on port 8081 (`ss -ltn | grep 8081` prints nothing).

Run: `mvn verify -Pit -Dit.test=CaseListIT`
Expected: the app starts on port 8081, `CaseListIT` runs 2 tests, `BUILD SUCCESS`. The first run may take longer while Selenium Manager downloads Chrome for Testing.

If a test fails, look at the screenshot in `error-screenshots/` before changing anything. If TestBench reports a license problem, STOP and tell the user (see Step 1).

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/test/java/com/example/application/it
git commit -m "Add end-to-end test setup with page objects and the case list test"
```

---

### Task 8: End-to-end workflow tests

**Files:**
- Test: `src/test/java/com/example/application/it/CaseLifecycleIT.java`
- Test: `src/test/java/com/example/application/it/CaseRejectIT.java`
- Test: `src/test/java/com/example/application/it/CaseValidationIT.java`

**Interfaces:**
- Consumes: Task 7 `AbstractIT`, `CaseListPage`, `CasePage`, `RoleSelector` (exact methods listed in Task 7).
- Produces: nothing used later.

- [ ] **Step 1: Write the tests**

`src/test/java/com/example/application/it/CaseLifecycleIT.java`:

```java
package com.example.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.it.pages.CaseListPage;
import com.example.application.it.pages.CasePage;
import com.example.application.it.pages.RoleSelector;
import com.vaadin.testbench.BrowserTest;
import java.time.LocalDate;

/**
 * End-to-end: the happy path through the whole workflow, as two users would click through it. The individual rules
 * are covered lower in the pyramid (CaseActionsTest, CaseViewTest).
 */
class CaseLifecycleIT extends AbstractIT {

    @BrowserTest
    void caseGoesFromDraftToApproved() {
        String title = uniqueTitle("E2E lifecycle");
        open("");
        CaseListPage list = new CaseListPage(this);

        CasePage newCase = list.clickNewCase();
        newCase.fill(title, "Travel", 5000, LocalDate.now().plusDays(10));
        newCase.submit();
        assertThat(firstNotificationText()).isEqualTo("Case submitted");

        new RoleSelector(this).actAs("Case handler");
        list.filterByTitle(title);
        assertThat(list.statusOfRow(0)).isEqualTo("Submitted");
        list.openRow(0).startReview();

        list.filterByTitle(title);
        CasePage inReview = list.openRow(0);
        assertThat(inReview.status()).isEqualTo("In review");
        inReview.approve();

        list.filterByTitle(title);
        assertThat(list.rowCount()).isEqualTo(1);
        assertThat(list.statusOfRow(0)).isEqualTo("Approved");
    }
}
```

`src/test/java/com/example/application/it/CaseRejectIT.java`:

```java
package com.example.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.it.pages.CaseListPage;
import com.example.application.it.pages.CasePage;
import com.example.application.it.pages.RoleSelector;
import com.vaadin.testbench.BrowserTest;
import java.time.LocalDate;

/** End-to-end: rejecting needs a reason, and the dialog enforces it in the browser. */
class CaseRejectIT extends AbstractIT {

    @BrowserTest
    void rejectingRequiresAReason() {
        String title = uniqueTitle("E2E reject");
        CaseListPage list = new CaseListPage(this);
        CasePage inReview = createCaseInReview(list, title);

        inReview.reject();
        inReview.confirmReject(null);

        assertThat(inReview.isRejectDialogOpen()).isTrue();
        assertThat(inReview.rejectionReasonErrorMessage()).isEqualTo("A reason is required");

        inReview.confirmReject("Not in this year's budget");

        list.filterByTitle(title);
        assertThat(list.statusOfRow(0)).isEqualTo("Rejected");
    }

    /** Creates and submits a case as submitter, then starts its review as case handler and opens it. */
    private CasePage createCaseInReview(CaseListPage list, String title) {
        open("");
        CasePage newCase = list.clickNewCase();
        newCase.fill(title, "Training", 8000, LocalDate.now().plusDays(20));
        newCase.submit();

        new RoleSelector(this).actAs("Case handler");
        list.filterByTitle(title);
        list.openRow(0).startReview();

        list.filterByTitle(title);
        return list.openRow(0);
    }
}
```

`src/test/java/com/example/application/it/CaseValidationIT.java`:

```java
package com.example.application.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.it.pages.CasePage;
import com.vaadin.testbench.BrowserTest;
import java.time.LocalDate;

/**
 * End-to-end: one validation rule, to prove error messages are actually rendered in the browser. Every rule is
 * covered in CaseViewTest.Validation.
 */
class CaseValidationIT extends AbstractIT {

    @BrowserTest
    void tooShortTitleShowsAnErrorInTheBrowser() {
        open("case/new");
        CasePage page = new CasePage(this);

        page.fill("abc", "Purchase", 1000, LocalDate.now().plusDays(3));
        page.save();

        assertThat(page.titleErrorMessage()).isEqualTo("Title must be 5-100 characters");
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `mvn verify -Pit`
Expected: `BUILD SUCCESS`; failsafe reports 5 end-to-end tests (2 + 1 + 1 + 1), 0 failures; surefire's browserless and unit tests also pass.

These tests are written against already-implemented behavior, so they are expected to pass on the first run. If one fails, check `error-screenshots/` first: a failure here means either the page object uses a wrong locator or the app behaves differently in a real browser than in the browserless tests. Fix the page object in the first case; report the second.

- [ ] **Step 3: Watch one run (optional, for the demo)**

Run: `mvn verify -Pit -Dit.test=CaseLifecycleIT -Dheadless=false`
Expected: a Chrome window clicks through the lifecycle; `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/example/application/it
git commit -m "Add end-to-end tests for the case lifecycle, rejection and validation"
```

---

### Task 9: Documentation

**Files:**
- Create: `docs/ui-test-checklist.md`
- Create: `docs/testing-levels.md`
- Modify: `README.md` (rewrite)
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: all tests from Tasks 1–8 (names referenced in the docs).
- Produces: nothing used later.

- [ ] **Step 1: Measure the suites**

Run each and write down the numbers:

```bash
mvn clean test
grep -h -o 'testsuite [^>]*' target/surefire-reports/TEST-*.xml | grep -o 'name="[^"]*"\|tests="[0-9]*"\|time="[0-9.]*"'
mvn verify -Pit
grep -h -o 'testsuite [^>]*' target/failsafe-reports/TEST-*.xml | grep -o 'name="[^"]*"\|tests="[0-9]*"\|time="[0-9.]*"'
```

Sum per level: domain (`CaseActionsTest` + `CaseServiceTest`), browserless (`CaseListViewTest` + `CaseViewTest`, including their nested classes), end-to-end (all `*IT`). Also note the wall-clock "Total time" Maven prints for `mvn test` and for `mvn verify -Pit`.

- [ ] **Step 2: Write `docs/ui-test-checklist.md`**

```markdown
# What to check when a page is presented

Use this checklist whenever a test opens a page, before it does anything else. Most UI bugs we see in practice are
wrong initial state: a button that should be hidden, a field that should be read-only, an error shown too early.

1. **Right page** — the expected view, heading and route.
2. **Content** — fields hold the expected values, or are empty for "new".
3. **Editability** — read-only or editable, as status and role require.
4. **Actions** — which buttons are visible *and* enabled, and which are hidden. Check both directions: a missing
   button and an extra button are both bugs.
5. **No errors at start** — no field is invalid and no notification is open.
6. **Focus** — where the cursor is, when it matters for the user.

## Where the demo applies it

- `CaseViewTest.InitialState` — the checklist, step by step, for a new case, a draft, and read-only cases.
- `CaseViewTest.ButtonVisibility` — step 4 for every status and role combination. The expected buttons are derived
  from `CaseActions`, so the test checks that the UI shows what the rules say without repeating the rules.
- `CaseListViewTest.InitialState` — the checklist for the list: rows, columns, empty filters, "New case" per role.

## Tips

- Find components by id (`$(Button.class).id("save")`), never by caption or position. Captions change; ids are a
  contract between the view and its tests.
- In browserless tests, queries only find visible components, so "hidden" is asserted as "not found". In the browser,
  hidden components are still in the DOM; check `isDisplayed()` instead (see `CaseListPage.isNewCaseButtonVisible`).
```

- [ ] **Step 3: Write `docs/testing-levels.md`**

Use this content, replacing each `<…>` with the numbers from Step 1:

```markdown
# Which level should a UI test be on?

The demo tests the same application at three levels. This page compares them using one scenario — a case handler
approves a case — and the measured run times of each suite.

## One scenario, three levels

| Level | Test | What it proves |
|---|---|---|
| Domain | `CaseActionsTest` — `CASE_HANDLER on a IN_REVIEW case may [APPROVE, REJECT]` | The rule is right. |
| Browserless UI | `CaseViewTest.Workflow.approve` | The Approve button is shown, calls the service, notifies and navigates. |
| End-to-end | `CaseLifecycleIT.caseGoesFromDraftToApproved` | Two users can click through the whole flow in a real browser. |

Read the three side by side: the domain test is one line of data, the browserless test is five lines of user
actions, the end-to-end test needs page objects, a running server and a browser.

## Measured

| Level | Tests | Time (reported by Maven) |
|---|---|---|
| Domain (`CaseActionsTest`, `CaseServiceTest`) | <n> | <s> s |
| Browserless UI (`CaseListViewTest`, `CaseViewTest`) | <n> | <s> s |
| End-to-end (`*IT`) | <n> | <s> s |

`mvn test` took <s> s wall-clock; `mvn verify -Pit` took <s> s (including starting the app and the browser).
Measured on <machine/OS> on <date>.

## What each level catches — and misses

**Domain tests** catch wrong rules. They miss everything about the UI.

**Browserless UI tests** catch: wrong initial state, wrong buttons for a status and role, missing or wrong
validation, broken navigation, dialogs that do not open or close, notifications. They run the real views with the
real Spring beans. They miss: anything the browser does — CSS and layout, client-side behavior of web components,
JavaScript errors, what the user actually sees rendered.

**End-to-end tests** catch: the whole stack working together in a real browser, including rendering and
client-side behavior. They miss nothing in principle, but they are slow, need a running server and a browser, and
break more easily when the page structure changes.

## Recommendation

- Put the rules in the domain and test them there, exhaustively.
- Test every view with browserless tests: the initial-state checklist (`docs/ui-test-checklist.md`), every button
  per status and role, every validation rule, filters, sorting, dialogs. This is where most UI tests belong.
- Keep a small number of end-to-end tests for the critical user flows, with page objects. Do not re-test rules there.
- Browserless tests need no Vaadin license; end-to-end tests with TestBench do.
```

- [ ] **Step 4: Rewrite `README.md`**

```markdown
# vaadin-25-testbench

A demo of UI testing a Vaadin Flow application at three levels: domain unit tests, browserless UI tests and
end-to-end browser tests with Vaadin TestBench. The application is a small case-handling workflow: cases go from
Draft to Submitted, In review and Approved or Rejected, and what a user may do depends on the status and on the role
chosen in the top bar ("Act as").

- [What to check when a page is presented](docs/ui-test-checklist.md)
- [Which level should a UI test be on?](docs/testing-levels.md)

## Requirements

- Java 25 and Maven
- For end-to-end tests: a Vaadin Pro license (`~/.vaadin/proKey` or `~/.vaadin/offlineKey`). Chrome for Testing is
  downloaded automatically on the first run.

## Running

```
mvn spring-boot:run                  # the app on http://localhost:8080
mvn test                             # domain and browserless UI tests
mvn test -Dtest=CaseViewTest         # one test class
mvn verify -Pit                      # also end-to-end tests (starts the app on port 8081)
mvn verify -Pit -Dheadless=false     # watch the browser
```

## Where things are

- `src/main/java/.../domain` — the case model and the workflow rules (`CaseActions`)
- `src/main/java/.../service` — in-memory `CaseService` and `CurrentUser` (the selected role)
- `src/main/java/.../views` — `MainLayout`, `CaseListView`, `CaseView`
- `src/test/java/.../domain`, `.../service` — plain unit tests
- `src/test/java/.../views` — browserless UI tests (`SpringBrowserlessTest`)
- `src/test/java/.../it` — end-to-end tests (`BrowserTestBase`) and page objects
```

- [ ] **Step 5: Update `CLAUDE.md`**

Replace the last bullet of "Structure notes" ("The application does not use Spring Security…") with:

```markdown
- The application does not use Spring Security. The role is chosen in the top bar and stored by `CurrentUser` in the
  `VaadinSession`; views only depend on `CurrentUser`, so it could be backed by Spring Security later.
- Workflow rules live in `domain/CaseActions` (which actions a role may take in which status). `CaseService` enforces
  them; views use them to decide which buttons to show. Tests derive expected buttons from `CaseActions` instead of
  repeating the rules.
- Test layout: `domain`/`service` plain JUnit; `views` browserless tests extending `AbstractViewTest`
  (resets `CaseService` seed data before each test); `it` end-to-end tests extending `AbstractIT`, using page objects
  in `it/pages`. End-to-end tests create their own data with unique titles because the app keeps running between
  tests.
- The `it` Maven profile starts the app on port 8081 with `--vaadin.launch-browser=false`; `-Dheadless=false` shows
  the browser.
```

- [ ] **Step 6: Verify everything once more**

Run: `mvn clean verify -Pit`
Expected: `BUILD SUCCESS`, 0 failures in surefire and failsafe.

Run: `grep -rniE '[æøå]' src docs README.md CLAUDE.md pom.xml`
Expected: no output (no Norwegian characters anywhere).

- [ ] **Step 7: Commit and push**

```bash
git add docs/ui-test-checklist.md docs/testing-levels.md README.md CLAUDE.md
git commit -m "Document the UI test checklist, the testing levels and how to run the demo"
git push
```
