package com.delivery.sales.repository;

import com.delivery.order.entity.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** Read-only projection access over orders for the commercial sales lens (no writes). */
public interface SalesOrderSearchRepository extends Repository<Order, UUID>, JpaSpecificationExecutor<Order> {
}
