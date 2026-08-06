package com.invault.inventory.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SecurityDataInitializer implements ApplicationRunner {

    private final SecurityBootstrapService securityBootstrapService;

    public SecurityDataInitializer(SecurityBootstrapService securityBootstrapService) {
        this.securityBootstrapService = securityBootstrapService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        securityBootstrapService.initializeSecurityData();
    }
}

/*
 * SecurityDataInitializer runs the idempotent role and optional administrator
 * setup after Spring has created the application context.
 */
