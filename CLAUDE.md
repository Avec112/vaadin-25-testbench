# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

A learning/demo project showing how to UI-test a Vaadin Flow application with Vaadin TestBench, at two levels:

- **Browserless UI unit tests** (`vaadin-testbench-unit-junit6`) — run in the JVM, no browser.
- **End-to-end browser tests** (`vaadin-testbench-core`, Selenium) — run against the started application.

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
- `browserless-test-spring` (what start.vaadin.com generates for 25.3+) does not exist in 25.2; the 25.2 equivalent is `com.vaadin:vaadin-testbench-unit-junit6` (version managed by the Vaadin BOM).
- TestBench is commercial. It needs a Vaadin Pro license: `~/.vaadin/proKey` (created by logging in via the browser prompt in dev mode) or `~/.vaadin/offlineKey` (downloaded from vaadin.com, needed for non-interactive runs such as CI).

## Structure notes

- Root package is `com.example.application` (the Maven groupId is `io.github.avec112`). If the package is ever renamed, update `vaadin.allowed-packages` in `src/main/resources/application.properties` as well.
- `MainLayout` is the `@Layout` for all views; the side navigation is built from `MenuConfiguration`, so views appear in the menu via `@Menu`.
- `src/main/bundles/` holds the Vaadin-generated frontend dev/prod bundles and is committed intentionally (Vaadin recommends this so others do not have to rebuild it). Files under `src/main/frontend/generated/` are generated and ignored.
- The application does not use Spring Security. Roles are simulated in the UI so TestBench is the focus; keep role lookup behind a small service so it could be replaced by Spring Security later.
