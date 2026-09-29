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
