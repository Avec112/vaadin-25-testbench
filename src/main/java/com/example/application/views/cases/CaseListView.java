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
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.Locale;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

@PageTitle("Cases")
@Route("")
@Menu(order = 0, icon = LineAwesomeIconUrl.CLIPBOARD_LIST_SOLID)
public class CaseListView extends VerticalLayout implements BeforeEnterObserver {

    private final TextField titleFilter = new TextField();
    private final Select<CaseStatus> statusFilter = new Select<>();
    private final Button newCase = new Button("New case", event -> UI.getCurrent().navigate("case/new"));
    private final CurrentUser currentUser;
    private final GridListDataView<Case> dataView;

    public CaseListView(CaseService caseService, CurrentUser currentUser) {
        this.currentUser = currentUser;
        setSizeFull();

        newCase.setId("new-case");
        newCase.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        updateNewCaseVisibility();

        Grid<Case> grid = new Grid<>();
        grid.setId("cases-grid");
        grid.addColumn(Case::getTitle).setKey("title").setHeader("Title")
                .setComparator(Case::getTitle).setFlexGrow(2);
        grid.addColumn(aCase -> aCase.getCategory().getLabel()).setKey("category").setHeader("Category")
                .setComparator(Case::getCategory);
        grid.addColumn(Case::getAmount).setKey("amount").setHeader("Amount").setComparator(Case::getAmount);
        grid.addColumn(Case::getRequestedDate).setKey("requestedDate").setHeader("Requested date")
                .setComparator(Case::getRequestedDate);
        grid.addColumn(aCase -> aCase.getStatus().getLabel()).setKey("status")
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

        add(newCase, new HorizontalLayout(titleFilter, statusFilter), grid);
    }

    // Only the visibility of the "new case" button is refreshed on each navigation, because the role may have
    // changed since the view was last shown. The grid and filters are set up once in the constructor.
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        updateNewCaseVisibility();
    }

    private void updateNewCaseVisibility() {
        newCase.setVisible(currentUser.getRole() == Role.SUBMITTER);
    }

    private boolean matchesFilters(Case aCase) {
        String titleText = titleFilter.getValue().strip().toLowerCase(Locale.ROOT);
        boolean titleMatches = titleText.isEmpty() || aCase.getTitle().toLowerCase(Locale.ROOT).contains(titleText);
        CaseStatus status = statusFilter.getValue();
        boolean statusMatches = status == null || aCase.getStatus() == status;
        return titleMatches && statusMatches;
    }
}
