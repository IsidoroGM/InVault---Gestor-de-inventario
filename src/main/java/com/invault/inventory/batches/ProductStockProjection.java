package com.invault.inventory.batches;

import java.math.BigDecimal;

public interface ProductStockProjection {

    Long getProductId();

    BigDecimal getTotalStock();
}
