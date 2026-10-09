package com.laundryhub.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;

// BatchSize: service types referenced by many order items are loaded together, not one query each
@BatchSize(size = 50)
@Entity
@Table(name = "service_types")
@Getter
@Setter
@NoArgsConstructor
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "price_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerKg;

    @Column(name = "express_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal expressSurcharge = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}