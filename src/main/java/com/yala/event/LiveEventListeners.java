package com.yala.event;

import com.yala.dto.live.LiveUpdateMessage;
import com.yala.dto.live.ResponseLiveAuctionDTO;
import com.yala.dto.live.ResponseLiveCommentDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.model.LiveAuction;
import com.yala.model.LiveAuctionStatus;
import com.yala.model.LiveComment;
import com.yala.model.NotificationType;
import com.yala.model.Order;
import com.yala.model.OrderStatus;
import com.yala.repository.LiveAuctionRepository;
import com.yala.repository.LiveBidRepository;
import com.yala.repository.LiveCommentRepository;
import com.yala.repository.OrderRepository;
import com.yala.repository.UserRepository;
import com.yala.service.EmailService;
import com.yala.service.LiveMapper;
import com.yala.service.NotificationService;
import java.time.LocalDateTime;
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
 * Asynchronous reactions to live-stream domain events: broadcasts state to the live's
 * WebSocket topics and, when a flash auction is sold, materializes the winning order with a
 * 48h payment deadline and emails the winner and the seller. Runs after the originating
 * transaction commits, each in its own transaction (mirrors {@link EventListeners}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveEventListeners {

    private static final long PAYMENT_WINDOW_HOURS = 48;

    private final LiveAuctionRepository liveAuctionRepository;
    private final LiveBidRepository liveBidRepository;
    private final LiveCommentRepository liveCommentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final LiveMapper liveMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.web-url:http://localhost:5173}")
    private String webUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLiveAuctionStarted(LiveAuctionStartedEvent event) {
        LiveAuction auction = loadAuction(event.liveAuctionId());
        broadcastAuction(auction, "AUCTION_STARTED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNewLiveBid(NewLiveBidEvent event) {
        LiveAuction auction = loadAuction(event.liveAuctionId());

        if (event.previousBidderId() != null) {
            notificationService.createNotification(
                    event.previousBidderId(),
                    NotificationType.BID_OUTBID,
                    "Te superaron en el live. Precio actual: " + event.newBidAmount());
            userRepository.findById(event.previousBidderId()).ifPresent(prev ->
                    emailService.sendBidOutbid(
                            prev.getEmail(),
                            prev.getName(),
                            auction.getTitle(),
                            event.newBidAmount(),
                            webUrl + "/live/" + auction.getLiveStream().getId(),
                            auction.getId()));
        }
        // Surface every bid in the live chat so all viewers see it (ephemeral, not persisted).
        userRepository.findById(event.currentBidderId()).ifPresent(bidder -> {
            ResponseLiveCommentDTO chatMsg = new ResponseLiveCommentDTO(
                    -event.bidId(),
                    "pujó S/. " + formatAmount(event.newBidAmount()),
                    LocalDateTime.now(),
                    bidder.getName(),
                    bidder.getId());
            messagingTemplate.convertAndSend(
                    "/topic/live/" + auction.getLiveStream().getId() + "/chat", chatMsg);
        });
        broadcastAuction(auction, "BID");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLiveAuctionClosed(LiveAuctionClosedEvent event) {
        LiveAuction auction = loadAuction(event.liveAuctionId());

        // Mensaje amigable en el chat, distinto según por qué se cerró la subasta.
        sendSystemChat(auction.getLiveStream().getId(), closeChatMessage(auction, event.reason()));

        if (auction.getStatus() == LiveAuctionStatus.SOLD && auction.getWinner() != null) {
            var seller = auction.getLiveStream().getSeller();
            var winner = auction.getWinner();
            float amount = auction.getWinningAmount() != null
                    ? auction.getWinningAmount()
                    : auction.getCurrentPrice();

            Order order = orderRepository.save(Order.builder()
                    .amount(amount)
                    .status(OrderStatus.PENDING)
                    .paymentDeadline(LocalDateTime.now().plusHours(PAYMENT_WINDOW_HOURS))
                    .liveAuction(auction)
                    .buyer(winner)
                    .seller(seller)
                    .build());

            notificationService.createNotification(winner.getId(), NotificationType.AUCTION_WON,
                    "¡Ganaste " + auction.getTitle() + "! Tienes 48 horas para pagar.");
            notificationService.createNotification(seller.getId(), NotificationType.SALE_CONFIRMED,
                    "Vendiste " + auction.getTitle() + " en tu live.");

            // Winner pays via Mercado Pago (the checkout route creates the preference and redirects).
            String payUrl = webUrl + "/checkout?orderId=" + order.getId();
            String sellerUrl = webUrl + "/seller";
            emailService.sendAuctionWon(winner.getEmail(), winner.getName(),
                    auction.getTitle(), amount, payUrl, order.getId());
            emailService.sendSaleConfirmed(seller.getEmail(), seller.getName(),
                    auction.getTitle(), winner.getName(), amount, sellerUrl, order.getId());

            log.info("Flash auction {} sold; order {} created for winner {} (deadline {})",
                    auction.getId(), order.getId(), winner.getEmail(), order.getPaymentDeadline());
        } else {
            log.info("Flash auction {} closed deserted (no bids)", auction.getId());
        }
        broadcastAuction(auction, "AUCTION_CLOSED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLiveEnded(LiveEndedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/live/" + event.liveStreamId(),
                new LiveUpdateMessage("LIVE_ENDED", null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLiveComment(LiveCommentEvent event) {
        LiveComment comment = liveCommentRepository.findById(event.commentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comment not found with id: " + event.commentId()));
        messagingTemplate.convertAndSend(
                "/topic/live/" + comment.getLiveStream().getId() + "/chat",
                liveMapper.toCommentDto(comment));
    }

    private void broadcastAuction(LiveAuction auction, String type) {
        ResponseLiveAuctionDTO dto = liveMapper.toAuctionDto(
                auction, liveBidRepository.countByLiveAuctionId(auction.getId()));
        messagingTemplate.convertAndSend(
                "/topic/live/" + auction.getLiveStream().getId(),
                new LiveUpdateMessage(type, dto));
    }

    /** Money for chat text: drop the trailing ".0" when the amount is a whole number. */
    private String formatAmount(Float amount) {
        if (amount == null) return "0";
        return amount == Math.floor(amount) ? String.valueOf(amount.longValue()) : String.valueOf(amount);
    }

    /** Texto del mensaje de cierre, distinto por motivo (máximo alcanzado / manual / desierta). */
    private String closeChatMessage(LiveAuction auction, LiveAuctionCloseReason reason) {
        boolean sold = auction.getStatus() == LiveAuctionStatus.SOLD && auction.getWinner() != null;
        if (!sold) {
            return "La subasta \"" + auction.getTitle() + "\" se cerró sin ofertas.";
        }
        String winner = auction.getWinner().getName();
        Float amount = auction.getWinningAmount() != null
                ? auction.getWinningAmount() : auction.getCurrentPrice();
        if (reason == LiveAuctionCloseReason.MAX_REACHED) {
            return "🏆 ¡Se alcanzó el monto máximo (S/. " + formatAmount(amount) + ")! La subasta \""
                    + auction.getTitle() + "\" ha finalizado — " + winner + " es el ganador.";
        }
        return "🔨 El vendedor cerró la subasta \"" + auction.getTitle() + "\". " + winner
                + " ganó con S/. " + formatAmount(amount) + ".";
    }

    /** Publica un mensaje de sistema en el chat del live (centinela id=0/userId=0, autor "Yala"). */
    private void sendSystemChat(Long streamId, String text) {
        messagingTemplate.convertAndSend(
                "/topic/live/" + streamId + "/chat",
                new ResponseLiveCommentDTO(0L, text, LocalDateTime.now(), "Yala", 0L));
    }

    private LiveAuction loadAuction(Long id) {
        return liveAuctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Flash auction not found with id: " + id));
    }
}
