package com.laundryhub.domain.entity;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.enums.OrderStatus;
import com.laundryhub.domain.enums.PayableType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "laundry_orders")
@Getter
@Setter
@NoArgsConstructor
public class LaundryOrder implements Payable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.RECEIVED;

    @Column(nullable = false)
    private boolean express;

    @Column(name = "total_weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalWeightKg = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // deleting an order deletes its items; removing an item from the list deletes that row
    // BatchSize: a page of orders loads all their items in one IN (...) query instead of one query per order (N+1)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LaundryOrderItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // keeps both sides of the 1:N in sync so the FK order_id is set on save
    public void addItem(LaundryOrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void clearItems() {
        items.forEach(item -> item.setOrder(null));
        items.clear();
    }

    @Override
    public BigDecimal getPayableAmount() {
        return totalAmount;
    }

    @Override
    public PayableType getPayableType() {
        return PayableType.LAUNDRY_ORDER;
    }

    @Override
    public Long getOwnerUserId() {
        return user.getId();
    }
}