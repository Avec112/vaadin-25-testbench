package com.example.application.it;

import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.testbench.BrowserTestBase;
import com.vaadin.testbench.DriverSupplier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        useSnapProfileDirectoryIfNeeded(options);
        return new ChromeDriver(options);
    }

    /**
     * Chromium installed as a snap cannot use the temporary profile directory that ChromeDriver creates in /tmp
     * ("DevToolsActivePort file doesn't exist"). Give it a profile directory inside its own snap storage instead.
     */
    private static void useSnapProfileDirectoryIfNeeded(ChromeOptions options) {
        Path snapStorage = Path.of(System.getProperty("user.home"), "snap", "chromium", "common");
        if (Files.isDirectory(snapStorage)) {
            try {
                Path profile = Files.createTempDirectory(snapStorage, "testbench-profile-");
                options.addArguments("--user-data-dir=" + profile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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
