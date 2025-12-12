package com.bootsandcats.oauth2.security;

import com.bootsandcats.oauth2.model.DenyRuleEntity;

/** Thrown when a login is denied by policy (e.g., deny list). */
public class LoginDeniedException extends RuntimeException {

    private final DenyRuleEntity denyRule;

    public LoginDeniedException(String message, DenyRuleEntity denyRule) {
        super(message);
        this.denyRule = denyRule;
    }

    public DenyRuleEntity getDenyRule() {
        return denyRule;
    }
}
