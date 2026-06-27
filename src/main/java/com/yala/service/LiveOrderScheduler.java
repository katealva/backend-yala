package com.yala.service;

import com.yala.model.NotificationType;
import com.yala.model.Order;
import com.yala.model.OrderStatus;
import com.yala.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels live-auction orders that were not paid within their 48h window. Only orders with a
 * {@code paymentDeadline} are affected (i.e. flash-auction wins), so regular orders keep their
 * previous behaviour. The won auction is effectively annulled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveOrderScheduler {

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    @Transactional
    public void cancelUnpaidLiveOrders() {
        List<Order> expired = orderRepository.findByStatusAndPaymentDeadlineBefore(
                OrderStatus.PENDING, LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }
        for (Order order : expired) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            notificationService.createNotification(order.getBuyer().getId(),
                    NotificationType.SALE_CONFIRMED,
                    "Tu compra de \"" + order.itemTitle() + "\" se anuló por falta de pago.");
            notificationService.createNotification(order.getSeller().getId(),
                    NotificationType.SALE_CONFIRMED,
                    "La venta de \"" + order.itemTitle() + "\" se anuló: el comprador no pagó a tiempo.");
            log.info("Order {} auto-cancelled (payment deadline {} passed)",
                    order.getId(), order.getPaymentDeadline());
        }
        log.info("LiveOrderScheduler cancelled {} unpaid order(s)", expired.size());
    }
}
