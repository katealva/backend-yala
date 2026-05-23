package com.yala.event;

import com.yala.auction.Auction;
import com.yala.auction.AuctionRepository;
import com.yala.exception.ResourceNotFoundException;
import com.yala.listing.Listing;
import com.yala.listing.ListingRepository;
import com.yala.listing.ListingStatus;
import com.yala.notification.NotificationService;
import com.yala.notification.NotificationType;
import com.yala.order.Order;
import com.yala.order.OrderRepository;
import com.yala.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Asynchronous reactions to domain events:
 * <ul>
 *   <li>{@link NewBidEvent} → notifies the previous (outbid) bidder.</li>
 *   <li>{@link AuctionFinishedEvent} → materializes the winning order and
 *       notifies winner/seller. Runs after the transaction that closed the
 *       auction commits, in its own transaction.</li>
 *   <li>{@link OrderConfirmedEvent} → notifies the buyer that the seller
 *       confirmed the sale.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventListeners {

    private final NotificationService notificationService;
    private final AuctionRepository auctionRepository;
    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;

    @Async
    @EventListener
    public void onNewBid(NewBidEvent event) {
        log.info("Handling NewBidEvent for auction {} amount {}",
                event.auctionId(), event.newBidAmount());
        if (event.previousBidderId() != null) {
            notificationService.createNotification(
                    event.previousBidderId(),
                    NotificationType.BID_OUTBID,
                    "You have been outbid! Current price: " + event.newBidAmount());
        }
        // TODO: WebSocket broadcast cuando WebSocketConfig esté disponible (punto 14)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuctionFinished(AuctionFinishedEvent event) {
        log.info("Handling AuctionFinishedEvent for auction {}", event.auctionId());
        Auction auction = auctionRepository.findById(event.auctionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Auction not found with id: " + event.auctionId()));

        if (auction.getWinner() == null) {
            log.info("Auction {} closed without a winner; skipping order creation",
                    auction.getId());
            return;
        }

        Listing listing = auction.getListing();
        Order order = orderRepository.save(Order.builder()
                .amount(auction.getCurrentPrice())
                .status(OrderStatus.PENDING)
                .listing(listing)
                .buyer(auction.getWinner())
                .seller(listing.getSeller())
                .build());

        listing.setStatus(ListingStatus.SOLD);
        listingRepository.save(listing);

        notificationService.createNotification(
                auction.getWinner().getId(),
                NotificationType.AUCTION_WON,
                "Congrats! You won " + listing.getTitle());
        notificationService.createNotification(
                listing.getSeller().getId(),
                NotificationType.SALE_CONFIRMED,
                "Your auction was won.");

        log.info("Auction {} order {} materialized for winner {}",
                auction.getId(), order.getId(), auction.getWinner().getEmail());
    }

    @Async
    @EventListener
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Handling OrderConfirmedEvent for order {}", event.orderId());
        notificationService.createNotification(
                event.buyerId(),
                NotificationType.SALE_CONFIRMED,
                "Your order has been confirmed by the seller.");
    }
}
