package com.example.application.views.cases;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.application.domain.Case;
import com.example.application.domain.CaseStatus;
import com.example.application.domain.Role;
import com.example.application.views.AbstractViewTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
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
        void statusFilterShowsOnlyThatStatus() {
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

    @SuppressWarnings("unchecked")
    private Select<CaseStatus> statusFilter() {
        return $(Select.class).id("status-filter");
    }
}
