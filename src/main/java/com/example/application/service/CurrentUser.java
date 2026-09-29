package com.example.application.service;

import com.example.application.domain.Role;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

/**
 * Knows which role the current user acts as. The role is kept in the VaadinSession, so every browser session has
 * its own. In a real application this would be backed by Spring Security; views only depend on this class, so
 * replacing the implementation does not affect them.
 */
@Component
public class CurrentUser {

    public Role getRole() {
        VaadinSession session = VaadinSession.getCurrent();
        Role role = session == null ? null : session.getAttribute(Role.class);
        return role == null ? Role.SUBMITTER : role;
    }

    public void setRole(Role role) {
        VaadinSession.getCurrent().setAttribute(Role.class, role);
    }
}
