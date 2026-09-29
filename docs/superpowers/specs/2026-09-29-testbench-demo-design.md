# TestBench Demo — Design

Date: 2026-09-29
Status: Draft for review

## 1. Goal

A small Vaadin Flow application plus a test suite that teaches a team how to UI-test Vaadin with TestBench and at which level to do it.

The audience is the author and colleagues. They have good unit and integration test coverage but no UI tests, and their UIs consist of:

- forms with various components and validation, with a status-driven workflow where status and role decide which buttons are visible;
- grids with column filters and sorting, on small data sets.

The most important takeaway is **what to verify when a page is presented**, and **how to split UI tests between the browserless and browser level**.

### Success criteria

- `mvn test` runs domain tests and browserless UI unit tests, all green.
- `mvn verify -Pit` starts the application and runs the end-to-end browser tests headless, all green.
- The same key scenarios exist at domain, UI unit and end-to-end level, so they can be compared side by side.
- `docs/ui-test-checklist.md` and `docs/testing-levels.md` exist; the latter contains measured run times.
- All code, comments, UI text and documentation are in English.

### Out of scope

Spring Security, a database, CI setup, i18n, cross-browser testing, Vaadin Copilot. The role lookup is designed so Spring Security could replace it later.

### Prerequisite (manual, by the user)

TestBench needs a Vaadin Pro license. `~/.vaadin/` currently only holds `userKey`. The user logs in once (browser prompt in dev mode) to create `~/.vaadin/proKey`, or downloads an `offlineKey`.

## 2. Test strategy: pyramid with overlap

| Level | Tooling | Covers | Speed |
|---|---|---|---|
| Domain | plain JUnit | status/role rules, state transitions | milliseconds |
| UI unit (browserless) | `SpringBrowserlessTest` (`browserless-test-spring`) | every view: initial state, button visibility per status×role, each validation rule, filtering, sorting, dialogs, navigation | fast, no browser |
| End-to-end | TestBench + Selenium (`vaadin-testbench-junit6`) | a few key user flows, overlapping with UI unit tests | slow, real browser |

UI unit tests verify that **the UI reflects the rules**; they do not re-test the rules themselves. End-to-end tests verify **whole flows**, not each rule.

## 3. Application

Root package stays `com.example.application` (kept in sync with `vaadin.allowed-packages`). The generated `HelloWorldView` and `AboutView` are removed.

### 3.1 Domain (`...application.domain`)

- `Case`: `id` (Long), `title`, `category`, `amount` (`Integer`, whole currency units), `requestedDate` (`LocalDate`), `description`, `urgent` (`boolean`), `status`, `rejectionReason`. A plain mutable bean with a `copy()` method; views always edit a copy.
- Validation rules (enforced by the form's `Binder`, see §3.3), with the exact messages tests assert on:
  - `title`: required ("Title is required"), 5–100 characters ("Title must be 5-100 characters")
  - `category`: required ("Category is required")
  - `amount`: required ("Amount is required"), 1–50 000 ("Amount must be between 1 and 50000")
  - `requestedDate`: required ("Requested date is required"), not in the past ("Requested date cannot be in the past")
  - `description`: optional, at most 1000 characters ("Description must be at most 1000 characters")
- `CaseCategory`: `PURCHASE`, `TRAVEL`, `TRAINING`.
- `CaseStatus`: `DRAFT → SUBMITTED → IN_REVIEW → APPROVED | REJECTED`.
- `Role`: `SUBMITTER`, `CASE_HANDLER`.
- `CaseAction`: `SAVE`, `SUBMIT`, `DELETE`, `START_REVIEW`, `APPROVE`, `REJECT`.
- `CaseActions`: single source of truth for `Set<CaseAction> allowed(CaseStatus status, Role role)`:

| Status | SUBMITTER | CASE_HANDLER |
|---|---|---|
| DRAFT (incl. new) | SAVE, SUBMIT, DELETE* | — |
| SUBMITTED | — | START_REVIEW |
| IN_REVIEW | — | APPROVE, REJECT |
| APPROVED / REJECTED | — | — |

\* DELETE is not offered for an unsaved new case.

A case is **editable** only when the role is `SUBMITTER` and the status is `DRAFT`; otherwise the form is read-only.

### 3.2 Services (`...application.service`)

- `CaseService` (singleton): in-memory store with deterministic seed data of 8 cases covering every status and every category, with distinct titles, amounts and dates so filter and sort assertions are exact. DRAFT seed cases have future `requestedDate`s. Methods: `findAll()`, `findById(id)`, `save(case)`, `delete(id)`, `apply(id, action, role)` (and `reject(id, reason, role)`), and `reset()` which restores the seed data. `apply` checks `CaseActions` and throws `IllegalStateException` for a disallowed action, so the UI cannot bypass the rules.
- `CurrentUser` (singleton Spring bean): stores the selected `Role` as an attribute of the current `VaadinSession`, default `SUBMITTER`. Being a singleton, it can be injected into views and tests alike. It is the only place views ask for the role — a later Spring Security implementation replaces this class only.

### 3.3 Views (`...application.views`)

**`MainLayout`** — existing layout, plus a `Select<Role>` with id `role-selector` ("Act as") in the navbar. Changing it updates `CurrentUser` and refreshes the current route (`UI.refreshCurrentRoute`), so the visible view is rebuilt for the new role.

**`CaseListView`** — route `""`, menu title "Cases".
- `Grid<Case>` id `cases-grid`, columns: title, category, amount, requested date, status, urgent. All columns sortable.
- Header filter row: `TextField` id `title-filter` (contains, case-insensitive) and `Select<CaseStatus>` id `status-filter` (empty = all). Filters combine.
- `Button` "New case", id `new-case`, visible only for `SUBMITTER`; navigates to `case/new`.
- Clicking a row navigates to `case/<id>`.

**`CaseView`** — route `case/:caseId` (`new` or a numeric id), not in the menu.
- Fields: `title`, `category` (`ComboBox`), `amount` (`IntegerField`), `requested-date` (`DatePicker`), `description` (`TextArea`), `urgent` (`Checkbox`). A read-only status indicator, id `status`.
- Bound with an explicit `Binder<Case>` validator chain (`asRequired(...)` first, then range/length validators). Bean validation annotations are not used: a blank title would violate both `@NotBlank` and `@Size`, and which message is shown would be nondeterministic. Fields are read-only unless editable (§3.1).
- Buttons with ids `save`, `submit`, `delete`, `start-review`, `approve`, `reject`, plus `back` (always visible). Visibility comes from `CaseActions.allowed(...)`; hidden buttons are not rendered as visible, not merely disabled.
- **Save**: validates; on errors, fields show messages and nothing is stored. On success: notification "Case saved" and navigation to the list.
- **Submit**: validates and saves, then moves to `SUBMITTED`; notification "Case submitted"; back to list.
- **Delete**: opens a `ConfirmDialog` (id `delete-confirm`). Cancel keeps the case; confirm deletes it, shows "Case deleted", back to list.
- **Start review / Approve**: status transition, notification, back to list.
- **Reject**: opens `RejectDialog` (`Dialog`, id `reject-dialog`) with `TextArea` id `rejection-reason` (required) and buttons `confirm-reject` / `cancel-reject`. Confirm with an empty reason marks the field invalid and keeps the dialog open. With a reason: status `REJECTED`, notification "Case rejected", back to list.
- Unknown or non-numeric id: notification "Case not found" and navigation to the list.
- Status transition failures from `CaseService` (e.g. a stale view after a role switch) show an error notification and reload the view.

### 3.4 Component ids

Every component a test touches has a stable id (listed above). Tests locate components by id — never by caption text or position. This is one of the conventions the demo teaches.

## 4. Browserless UI unit tests

Dependency: `com.vaadin:browserless-test-spring:1.1.2` (test scope, Apache 2.0, built against Vaadin 25.2). It replaces `vaadin-testbench-unit-junit6`, whose `SpringUIUnitTest` is deprecated for removal since TestBench 10.1 in favor of the `com.vaadin.browserless` package. It is not managed by the Vaadin 25.2 BOM, so its version is set explicitly in a `browserless-test.version` property. From Vaadin 25.3 it follows the Vaadin version.

Browserless tests need **no** Vaadin license; only the end-to-end tests do.

- `AbstractViewTest extends SpringBrowserlessTest`, annotated `@SpringBootTest` and `@ViewPackages(packages = "com.example.application.views")`: `@BeforeEach` calls `caseService.reset()` and sets the role to `SUBMITTER`; helper `actAs(Role)`.
- Components are found with `$(Type.class).id("...")` and driven with `test(component)`. Queries only match **effectively visible** components, so "button is hidden" is asserted as "no visible button with that id exists".
- JUnit `@Nested` classes and `@DisplayName` keep the reports readable.

**`CaseActionsTest`** (plain JUnit, domain) — `@ParameterizedTest` over every status × role, asserting the exact allowed set. Plus transition tests on `CaseService.apply` (disallowed action throws).

**`CaseListViewTest`**
- `InitialState`: expected columns, row count equals seed size, filters empty, `new-case` visible for SUBMITTER and hidden for CASE_HANDLER.
- `Filtering`: by title, by status, both combined, clearing a filter restores all rows.
- `Sorting`: amount and requested date, ascending and descending.
- `Navigation`: clicking a row opens `CaseView` for that case; `new-case` opens an empty form.

**`CaseViewTest`**
- `InitialState`: applies the checklist (§6) to a new case, a DRAFT case and a read-only case.
- `ButtonVisibility`: parameterized over status × role; asserts exactly the expected buttons are visible and all others are not.
- `Validation`: one test per rule (required fields, title length, amount bounds, past date); each asserts the field is invalid, the error message, and that nothing was stored.
- `Workflow`: submit, start review, approve; reject via dialog (empty reason blocked, with reason succeeds); delete via confirm dialog (cancel and confirm); notifications and resulting navigation.
- `RoleSwitch`: switching role in `role-selector` rebuilds the view with the new button set.
- `NotFound`: unknown id redirects to the list with a notification.

## 5. End-to-end browser tests

Dependency added: `com.vaadin:vaadin-testbench-junit6:${vaadin.version}` (test scope). It aggregates `vaadin-testbench-core-junit6` and all component element classes.

### 5.1 Running

- Test classes are named `*IT` and run by failsafe in the existing `it` Maven profile (`mvn verify -Pit`); surefire does not pick them up.
- The `it` profile starts the app on **port 8081** with `--vaadin.launch-browser=false`, so a dev server on 8080 can keep running and no browser tab pops up. The port is passed to the tests as a system property.
- Browser: Chrome. Google Chrome is not installed locally (only snap Chromium, which works poorly with chromedriver), so Selenium Manager downloads Chrome for Testing and the matching driver on first run.
- Headless by default; `-Dheadless=false` shows the browser, useful for demos.

### 5.2 Structure

- `AbstractIT extends BrowserTestBase implements DriverSupplier`: test methods use `@BrowserTest`; `createDriver()` builds a `ChromeDriver` (headless unless `-Dheadless=false`); `open(path)` loads a route on the test port. `BrowserTestBase` registers TestBench's `ScreenshotOnFailureExtension`, which writes to `error-screenshots/` (already git-ignored).
- Page objects in `...it.pages`, built on TestBench element classes (`TextFieldElement`, `GridElement`, `ButtonElement`, `SelectElement`, `ComboBoxElement`, `DatePickerElement`, `DialogElement`, `ConfirmDialogElement`, `NotificationElement`), locating components by the ids in §3.4:
  - `CaseListPage`: filter by title/status, open case by title, click "New case", read row statuses.
  - `CasePage`: fill fields, click actions, read status and field error messages, handle reject/delete dialogs.
  - `RoleSelector`: switch role.

### 5.3 Test data

The application runs as a separate process, so `reset()` is not reachable and no test-only endpoint is added. Each end-to-end test creates its own case with a unique title and filters the list on it. Tests are therefore order-independent and the pattern also works against a real test environment.

### 5.4 Scenarios

1. `CaseListIT`: the list loads; "New case" disappears after switching to CASE_HANDLER.
2. `CaseLifecycleIT`: create → submit → switch role → start review → approve → list shows APPROVED.
3. `CaseRejectIT`: rejecting without a reason is blocked in the dialog; with a reason the case becomes REJECTED.
4. `CaseValidationIT`: one validation rule, proving the error message is rendered in the browser.

### 5.5 Conventions taught

- No `Thread.sleep`; TestBench waits for Vaadin to finish server round trips.
- Test flows, not rules; the rules are covered lower in the pyramid.
- Page objects hide page structure; tests read as user actions.

## 6. Initial-state checklist (`docs/ui-test-checklist.md`)

When a page is presented, verify:

1. **Right page** — expected view, title and route.
2. **Content** — fields hold the expected values (or are empty for "new").
3. **Editability** — read-only vs editable matches status and role.
4. **Actions** — which buttons are visible *and* enabled, and which are hidden.
5. **No errors at start** — no field is invalid, no notification is open.
6. **Focus** — where the cursor is, when relevant.

Tests implementing the checklist reference this document.

## 7. Documentation

- `docs/ui-test-checklist.md` — §6, with links to the tests that apply it.
- `docs/testing-levels.md` — one scenario shown at all three levels side by side; measured run time per suite; what each level catches and misses; the recommended split. Written after implementation so the numbers are real.
- `README.md` — replaced with a short project description, how to run the app and each test suite, and links to the two documents above.
- `CLAUDE.md` — updated with the new structure and commands.
