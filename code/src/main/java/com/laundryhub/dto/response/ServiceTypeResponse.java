package com.laundryhub.dto.response;

import java.math.BigDecimal;

public record ServiceTypeResponse(Long id, String name, BigDecimal pricePerKg, BigDecimal expressSurcharge,
                                  boolean active) {
}
