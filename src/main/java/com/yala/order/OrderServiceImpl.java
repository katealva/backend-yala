package com.yala.order;

import com.yala.event.OrderConfirmedEvent;
import com.yala.exceptions.OrderNotConfirmableException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.listing.Listing;
import com.yala.listing.ListingMode;
import com.yala.listing.ListingRepository;
import com.yala.listing.ListingStatus;
import com.yala.order.dto.CreateOrderRequest;
import com.yala.order.dto.OrderResponse;
import com.yala.user.User;
import com.yala.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrderResponse create(CreateOrderRequest request, String buyerEmail) {
        User buyer = userRepository.findByEmail(buyerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Listing listing = listingRepository.findById(request.listingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing not found with id: " + request.listingId()));

        if (listing.getMode() != ListingMode.FIXED) {
            throw new OrderNotConfirmableException(
                    "Direct purchase is only allowed on FIXED listings");
        }
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new OrderNotConfirmableException(
                    "Listing " + listing.getId() + " is no longer available");
        }
        if (listing.getSeller().getId().equals(buyer.getId())) {
            throw new OrderNotConfirmableException("A seller cannot buy their own listing");
        }

        Order order = orderRepository.save(Order.builder()
                .amount(listing.getFixedPrice())
                .status(OrderStatus.PENDING)
                .listing(listing)
                .buyer(buyer)
                .seller(listing.getSeller())
                .build());

        listing.setStatus(ListingStatus.SOLD);
        listingRepository.save(listing);

        log.info("Order {} created by buyer {} for listing {}",
                order.getId(), buyer.getEmail(), listing.getId());
        return OrderResponse.from(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findByBuyer(String buyerEmail, Pageable pageable) {
        User buyer = userRepository.findByEmail(buyerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return orderRepository.findByBuyerId(buyer.getId(), pageable).map(OrderResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id, String requesterEmail) {
        Order order = findOrThrow(id);
        ensureBuyerOrSeller(order, requesterEmail);
        return OrderResponse.from(order);
    }

    @Override
    @Transactional
    public OrderResponse confirm(Long id, String sellerEmail) {
        Order order = findOrThrow(id);
        if (!order.getSeller().getEmail().equals(sellerEmail)) {
            throw new UnauthorizedException("Only the seller of this order can confirm it");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderNotConfirmableException(
                    "Order " + order.getId() + " is not pending and cannot be confirmed");
        }

        order.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderConfirmedEvent(
                saved.getId(), saved.getBuyer().getId(), saved.getSeller().getId()));

        log.info("Order {} confirmed by seller {}", saved.getId(), sellerEmail);
        return OrderResponse.from(saved);
    }

    @Override
    @Transactional
    public OrderResponse cancel(Long id, String requesterEmail) {
        Order order = findOrThrow(id);
        boolean isBuyer = order.getBuyer().getEmail().equals(requesterEmail);
        boolean isSeller = order.getSeller().getEmail().equals(requesterEmail);
        if (!isBuyer && !isSeller) {
            throw new UnauthorizedException("Only the buyer or seller can cancel this order");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderNotConfirmableException(
                    "Order " + order.getId() + " is not pending and cannot be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        Listing listing = order.getListing();
        listing.setStatus(ListingStatus.ACTIVE);
        listingRepository.save(listing);

        log.info("Order {} cancelled by {}", saved.getId(), requesterEmail);
        return OrderResponse.from(saved);
    }

    private Order findOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + id));
    }

    private void ensureBuyerOrSeller(Order order, String email) {
        boolean isBuyer = order.getBuyer().getEmail().equals(email);
        boolean isSeller = order.getSeller().getEmail().equals(email);
        if (!isBuyer && !isSeller) {
            throw new UnauthorizedException(
                    "Only the buyer or seller of this order can access it");
        }
    }
}
