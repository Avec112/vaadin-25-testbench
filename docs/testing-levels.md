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
| Domain (`CaseActionsTest`, `CaseServiceTest`) | 28 | 0.02 s |
| Browserless UI (`CaseListViewTest`, `CaseViewTest`) | 66 | 4.7 s |
| End-to-end (`*IT`) | 5 | 13.9 s |

`mvn test` took 7.8 s wall-clock; `mvn verify -Pit` took 26.3 s (including starting the app and the browser).
Measured on Linux 7.1 (Ubuntu-based desktop) on 2026-09-29.

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
