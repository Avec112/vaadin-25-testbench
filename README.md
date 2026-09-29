# vaadin-25-testbench

A demo of UI testing a Vaadin Flow application at three levels: domain unit tests, browserless UI tests and
end-to-end browser tests with Vaadin TestBench. The application is a small case-handling workflow: cases go from
Draft to Submitted, In review and Approved or Rejected, and what a user may do depends on the status and on the role
chosen in the top bar ("Act as").

- [What to check when a page is presented](docs/ui-test-checklist.md)
- [Which level should a UI test be on?](docs/testing-levels.md)

## Requirements

- Java 25 and Maven
- For end-to-end tests: a Vaadin Pro license (`~/.vaadin/proKey` or `~/.vaadin/offlineKey`).
- The first end-to-end run opens a browser so you can log in to vaadin.com, which creates `proKey`.
- Non-interactive environments such as CI need `offlineKey`, downloaded from vaadin.com.
- For end-to-end tests: Chrome or Chromium. Selenium Manager uses the installed browser and downloads a matching
  driver, or downloads Chrome for Testing if no browser is found.
- If Chromium is installed as a snap and fails with "DevToolsActivePort file doesn't exist", see the guarded
  workaround in `AbstractIT`.

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
- `src/test/java/.../views` — browserless UI tests (`SpringBrowserlessTest`); `CaseRejectLocatorTest` shows one of
  them rewritten with the newer locator API (`findButton().withText("Reject").click()`)
- `src/test/java/.../it` — end-to-end tests (`BrowserTestBase`) and page objects
