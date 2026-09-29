# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

A learning/demo project showing how to UI-test a Vaadin Flow application with Vaadin TestBench, at two levels:

- **Browserless UI unit tests** (`browserless-test-spring`, base class `SpringBrowserlessTest`) — run in the JVM, no browser, no license needed.
- **End-to-end browser tests** (`vaadin-testbench-junit6`, Selenium, base class `BrowserTestBase`) — run against the started application; needs a Vaadin Pro license.

The audience is developers who have good unit/integration test coverage but no UI tests yet, and want to learn what to verify and at which level. Tests are meant to be read and copied, so they should be clear and demonstrate one pattern at a time.

## Language

All code, identifiers, comments, commit messages, UI text and documentation in this repository must be in **English**. Never Norwegian, even though the conversation with the user may be in Norwegian.

## Commands

The project uses the system-installed Maven (the Maven wrapper was removed on purpose — do not re-add `mvnw`).

```bash
mvn package                            # build (runs unit tests, builds frontend)
mvn spring-boot:run                    # run in dev mode on http://localhost:8080 (also the default goal: `mvn`)
mvn test                               # unit + browserless UI unit tests
mvn test -Dtest=SomeTest               # single test class
mvn test -Dtest=SomeTest#someMethod    # single test method
mvn verify -Pit                        # starts the app and runs end-to-end tests (*IT) via failsafe
mvn verify -Pit -Dit.test=SomeIT       # single end-to-end test class
```

## Versions and dependencies

- Java 25, Spring Boot 4.1.x, Vaadin **25.2.8** (pinned deliberately; start.vaadin.com generated 25.3.0). Do not upgrade Vaadin unless asked.
- Browserless UI tests use `com.vaadin:browserless-test-spring` **1.1.2** (Apache 2.0, built for Vaadin 25.2). It is not in the 25.2 BOM, so its version is pinned in the `browserless-test.version` property; bump it together with Vaadin (from 25.3 its version equals the Vaadin version). Do not use `vaadin-testbench-unit-*`: its `SpringUIUnitTest`/`UIUnitTest` are deprecated for removal in favor of `com.vaadin.browserless`.
- Browserless queries (`$(...)`) only match effectively visible components.
- End-to-end tests use `com.vaadin:vaadin-testbench-junit6:${vaadin.version}` (TestBench core + all element classes). TestBench is commercial and needs a Vaadin Pro license: `~/.vaadin/proKey` (created by logging in when the first end-to-end test run opens the vaadin.com login page; TestBench is test-scoped, so dev mode shows no prompt) or `~/.vaadin/offlineKey` (downloaded from vaadin.com, needed for non-interactive runs such as CI).

## Structure notes

- Root package is `com.example.application` (the Maven groupId is `io.github.avec112`). If the package is ever renamed, update `vaadin.allowed-packages` in `src/main/resources/application.properties` as well.
- `MainLayout` is the `@Layout` for all views; the side navigation is built from `MenuConfiguration`, so views appear in the menu via `@Menu`.
- `src/main/bundles/` holds the Vaadin-generated frontend dev/prod bundles and is committed intentionally (Vaadin recommends this so others do not have to rebuild it). Files under `src/main/frontend/generated/` are generated and ignored.
- The application does not use Spring Security. The role is chosen in the top bar and stored by `CurrentUser` in the
  `VaadinSession`; views only depend on `CurrentUser`, so it could be backed by Spring Security later.
- Workflow rules live in `domain/CaseActions` (which actions a role may take in which status). `CaseService` enforces
  them; views use them to decide which buttons to show. Tests derive expected buttons from `CaseActions` instead of
  repeating the rules.
- Test layout: `domain`/`service` plain JUnit; `views` browserless tests extending `AbstractViewTest`
  (resets `CaseService` seed data before each test), except `CaseRejectLocatorTest`, which deliberately uses the
  locator API (`SpringBrowserlessApplicationContext` + `BrowserlessUIContext`, no base class) as a side-by-side
  comparison with `CaseViewTest`; `it` end-to-end tests extending `AbstractIT`, using page objects
  in `it/pages`. End-to-end tests create their own data with unique titles because the app keeps running between
  tests.
- The `it` Maven profile starts the app on port 8081 with `--vaadin.launch-browser=false`; `-Dheadless=false` shows
  the browser.
