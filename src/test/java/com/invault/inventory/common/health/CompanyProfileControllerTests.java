package com.invault.inventory.common.health;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class CompanyProfileControllerTests {
    @Test
    void exposesOnlyTheDeploymentDisplayNameWithNoCache() {
        var response = new CompanyProfileController("Almacén José & Hijos").profile();
        assertThat(response.getBody().name()).isEqualTo("Almacén José & Hijos");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
