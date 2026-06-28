package com.yala.event;

import com.yala.model.Auction;
import com.yala.repository.AuctionRepository;
import com.yala.dto.auction.AuctionUpdateMessage;
import com.yala.dto.auction.LatestBidInfo;
import com.yala.model.Bid;
import com.yala.service.EmailService;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.model.Listing;
import com.yala.repository.ListingRepository;
import com.yala.model.ListingStatus;
import com.yala.service.NotificationService;
import com.yala.model.NotificationType;
import com.yala.model.Order;
import com.yala.repository.OrderRepository;
import com.yala.model.OrderStatus;
import com.yala.repository.UserRepository;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${app.web-url:http://localhost:5173}")
    private String webUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNewBid(NewBidEvent event) {
        log.info("Handling NewBidEvent for auction {} amount {}",
                event.auctionId(), event.newBidAmount());
        if (event.previousBidderId() != null) {
            notificationService.createNotification(
                    event.previousBidderId(),
                    NotificationType.BID_OUTBID,
                    "You have been outbid! Current price: " + event.newBidAmount());
        }

        Auction auction = auctionRepository.findById(event.auctionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Auction not found with id: " + event.auctionId()));
        Bid latest = auction.getBids().stream()
                .max(Comparator.comparing(Bid::getPlacedAt))
                .orElse(null);
        LatestBidInfo latestBid = latest == null ? null : new LatestBidInfo(
                latest.getBidder().getName(),
                latest.getAmount(),
                latest.getPlacedAt());
        AuctionUpdateMessage payload = new AuctionUpdateMessage(
                auction.getId(),
                auction.getCurrentPrice(),
                auction.getBids().size(),
                auction.getStatus() != null ? auction.getStatus().name() : null,
                latestBid,
                null);
        messagingTemplate.convertAndSend("/topic/auction/" + auction.getId(), payload);

        if (event.previousBidderId() != null && auction.getListing() != null) {
            userRepository.findById(event.previousBidderId()).ifPresent(prev ->
                    emailService.sendBidOutbid(
                            prev.getEmail(),
                            prev.getName(),
                            auction.getListing().getTitle(),
                            event.newBidAmount(),
                            baseUrl + "/auctions/" + auction.getId(),
                            auction.getId()));
        }
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
            messagingTemplate.convertAndSend(
                    "/topic/auction/" + auction.getId(),
                    new AuctionUpdateMessage(
                            auction.getId(),
                            auction.getCurrentPrice(),
                            auction.getBids().size(),
                            auction.getStatus() != null ? auction.getStatus().name() : null,
                            null,
                            null));
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

        messagingTemplate.convertAndSend(
                "/topic/auction/" + auction.getId(),
                new AuctionUpdateMessage(
                        auction.getId(),
                        auction.getCurrentPrice(),
                        auction.getBids().size(),
                        auction.getStatus() != null ? auction.getStatus().name() : null,
                        null,
                        auction.getWinner().getName()));

        String payUrl = webUrl + "/checkout?orderId=" + order.getId();
        String sellerUrl = webUrl + "/seller";
        emailService.sendAuctionWon(
                auction.getWinner().getEmail(),
                auction.getWinner().getName(),
                listing.getTitle(),
                auction.getCurrentPrice(),
                payUrl,
                order.getId());
        emailService.sendSaleConfirmed(
                listing.getSeller().getEmail(),
                listing.getSeller().getName(),
                listing.getTitle(),
                auction.getWinner().getName(),
                auction.getCurrentPrice(),
                sellerUrl,
                order.getId());

        log.info("Auction {} order {} materialized for winner {}",
                auction.getId(), order.getId(), auction.getWinner().getEmail());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Handling OrderConfirmedEvent for order {}", event.orderId());
        notificationService.createNotification(
                event.buyerId(),
                NotificationType.SALE_CONFIRMED,
                "Tu pago fue confirmado. Coordina la entrega con el vendedor.");
        notificationService.createNotification(
                event.sellerId(),
                NotificationType.SALE_CONFIRMED,
                "El comprador pagó. Coordina la entrega del producto.");

        orderRepository.findById(event.orderId()).ifPresent(order -> {
            String title = order.itemTitle();
            String orderUrl = baseUrl + "/orders/" + order.getId();
            // Confirmation email to the buyer.
            userRepository.findById(event.buyerId()).ifPresent(buyer ->
                    emailService.sendOrderConfirmed(
                            buyer.getEmail(), buyer.getName(), title,
                            order.getAmount(), orderUrl, order.getId()));
            // Confirmation email to the seller so both parties coordinate delivery.
            userRepository.findById(event.sellerId()).ifPresent(seller ->
                    emailService.sendSaleConfirmed(
                            seller.getEmail(), seller.getName(), title,
                            order.getBuyer() != null ? order.getBuyer().getName() : "",
                            order.getAmount(), orderUrl, order.getId()));
        });
    }
}
