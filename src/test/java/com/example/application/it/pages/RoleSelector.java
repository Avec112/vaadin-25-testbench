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
