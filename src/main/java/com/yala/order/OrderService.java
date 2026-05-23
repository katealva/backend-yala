package com.yala.order;

import com.yala.order.dto.CreateOrderRequest;
import com.yala.order.dto.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse create(CreateOrderRequest request, String buyerEmail);

    Page<OrderResponse> findByBuyer(String buyerEmail, Pageable pageable);

    OrderResponse findById(Long id, String requesterEmail);

    OrderResponse confirm(Long id, String sellerEmail);

    OrderResponse cancel(Long id, String requesterEmail);
}
