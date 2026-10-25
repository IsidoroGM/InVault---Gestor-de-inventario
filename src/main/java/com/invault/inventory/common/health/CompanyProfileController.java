package com.invault.inventory.common.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Deployment metadata only: company identity never selects a database from the browser. */
@RestController
public class CompanyProfileController {
    private final String companyName;

    public CompanyProfileController(@Value("${INVAULT_COMPANY_NAME:InVault}") String companyName) {
        this.companyName = companyName;
    }

    @GetMapping("/api/company")
    public ResponseEntity<CompanyProfile> profile() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CompanyProfile(companyName));
    }

    public record CompanyProfile(String name) {}
}
