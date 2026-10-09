package com.laundryhub.repository;

import com.laundryhub.domain.entity.LaundryOrder;
import com.laundryhub.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LaundryOrderRepository extends JpaRepository<LaundryOrder, Long> {

    // customer's own orders (paged + sorted by the Pageable passed in)
    Page<LaundryOrder> findByUserId(Long userId, Pageable pageable);

    Page<LaundryOrder> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    // staff board: all orders, optionally filtered by status
    Page<LaundryOrder> findByStatus(OrderStatus status, Pageable pageable);

    // order detail: load items and their service types in one query (avoids N+1)
    @EntityGraph(attributePaths = {"items", "items.serviceType"})
    Optional<LaundryOrder> findWithItemsById(Long id);
}