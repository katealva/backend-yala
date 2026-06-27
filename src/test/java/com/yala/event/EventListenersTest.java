package com.yala.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yala.model.Auction;
import com.yala.repository.AuctionRepository;
import com.yala.model.AuctionStatus;
import com.yala.service.EmailService;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.model.Listing;
import com.yala.repository.ListingRepository;
import com.yala.model.ListingStatus;
import com.yala.service.NotificationService;
import com.yala.model.NotificationType;
import com.yala.model.Order;
import com.yala.repository.OrderRepository;
import com.yala.model.Role;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventListenersTest {

    @Mock private NotificationService notificationService;
    @Mock private AuctionRepository auctionRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private EventListeners eventListeners;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eventListeners, "baseUrl", "http://test.yala");
    }

    private Auction emptyActiveAuction() {
        return Auction.builder()
                .id(100L).currentPrice(250f)
                .status(AuctionStatus.ACTIVE)
                .listing(listing(seller()))
                .bids(Collections.emptyList())
                .build();
    }

    private User seller() {
        return User.builder().id(1L).email("seller@yala.pe").role(Role.SELLER).build();
    }

    private User winner() {
        return User.builder().id(2L).email("winner@yala.pe").role(Role.USER).build();
    }

    private Listing listing(User sellerUser) {
        return Listing.builder()
                .id(10L).title("Pikachu Illustrator")
                .status(ListingStatus.ACTIVE).seller(sellerUser).build();
    }

    @Test
    void shouldNotifyPreviousBidderWhenOnNewBidHasPreviousBidder() {
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(emptyActiveAuction()));
        NewBidEvent event = new NewBidEvent(100L, 250f, 5L, 6L);

        eventListeners.onNewBid(event);

        verify(notificationService).createNotification(
                eq(5L),
                eq(NotificationType.BID_OUTBID),
                org.mockito.ArgumentMatchers.contains("250"));
    }

    @Test
    void shouldNotInvokeNotificationServiceWhenOnNewBidHasNoPreviousBidder() {
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(emptyActiveAuction()));
        NewBidEvent event = new NewBidEvent(100L, 250f, null, 6L);

        eventListeners.onNewBid(event);

        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void shouldMaterializeOrderAndNotifyWhenOnAuctionFinishedHasWinner() {
        User seller = seller();
        User w = winner();
        Listing listing = listing(seller);
        Auction auction = Auction.builder()
                .id(100L).listing(listing).currentPrice(500f)
                .status(AuctionStatus.FINISHED).winner(w).build();
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(999L);
            return o;
        });

        eventListeners.onAuctionFinished(new AuctionFinishedEvent(100L));

        verify(orderRepository).save(any(Order.class));
        verify(listingRepository).save(listing);
        verify(notificationService).createNotification(
                eq(w.getId()), eq(NotificationType.AUCTION_WON),
                org.mockito.ArgumentMatchers.contains("Pikachu"));
        verify(notificationService).createNotification(
                eq(seller.getId()), eq(NotificationType.SALE_CONFIRMED),
                any());
    }

    @Test
    void shouldSkipOrderCreationWhenAuctionFinishedHasNoWinner() {
        Auction auction = Auction.builder()
                .id(100L).listing(listing(seller())).currentPrice(500f)
                .status(AuctionStatus.FINISHED).winner(null).build();
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(auction));

        eventListeners.onAuctionFinished(new AuctionFinishedEvent(100L));

        verify(orderRepository, never()).save(any());
        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenAuctionFinishedAuctionMissing() {
        when(auctionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                eventListeners.onAuctionFinished(new AuctionFinishedEvent(404L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldNotifyBuyerAndSellerWhenOnOrderConfirmedInvoked() {
        eventListeners.onOrderConfirmed(new OrderConfirmedEvent(50L, 10L, 20L));

        verify(notificationService).createNotification(
                eq(10L), eq(NotificationType.SALE_CONFIRMED),
                org.mockito.ArgumentMatchers.contains("pago"));
        verify(notificationService).createNotification(
                eq(20L), eq(NotificationType.SALE_CONFIRMED),
                org.mockito.ArgumentMatchers.contains("entrega"));
    }

    @Test
    void shouldSendOutbidEmailWhenPreviousBidderExists() {
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(emptyActiveAuction()));
        User previous = User.builder()
                .id(5L).name("Ada Lovelace").email("ada@yala.pe").role(Role.USER).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(previous));

        eventListeners.onNewBid(new NewBidEvent(100L, 250f, 5L, 6L));

        verify(emailService).sendBidOutbid(
                eq("ada@yala.pe"),
                eq("Ada Lovelace"),
                eq("Pikachu Illustrator"),
                eq(250f),
                contains("/auctions/100"),
                eq(100L));
    }

    @Test
    void shouldNotSendOutbidEmailWhenNoPreviousBidder() {
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(emptyActiveAuction()));

        eventListeners.onNewBid(new NewBidEvent(100L, 250f, null, 6L));

        verifyNoInteractions(emailService);
    }

    @Test
    void shouldSendWonAndConfirmedEmailsWhenAuctionFinishesWithWinner() {
        User seller = seller();
        User w = winner();
        Listing listing = listing(seller);
        Auction auction = Auction.builder()
                .id(100L).listing(listing).currentPrice(500f)
                .status(AuctionStatus.FINISHED).winner(w).build();
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(999L);
            return o;
        });

        eventListeners.onAuctionFinished(new AuctionFinishedEvent(100L));

        verify(emailService).sendAuctionWon(
                eq(w.getEmail()),
                any(),
                eq("Pikachu Illustrator"),
                eq(500f),
                contains("/orders/999"),
                eq(999L));
        verify(emailService).sendSaleConfirmed(
                eq(seller.getEmail()),
                any(),
                eq("Pikachu Illustrator"),
                any(),
                eq(500f),
                contains("/orders/999"),
                eq(999L));
    }

    @Test
    void shouldSendOrderConfirmedEmailWhenSellerConfirms() {
        User buyer = User.builder()
                .id(10L).name("Comprador Pe").email("buyer@yala.pe").role(Role.USER).build();
        Order order = Order.builder()
                .id(50L).amount(750f).listing(listing(seller())).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findById(10L)).thenReturn(Optional.of(buyer));

        eventListeners.onOrderConfirmed(new OrderConfirmedEvent(50L, 10L, 20L));

        verify(emailService).sendOrderConfirmed(
                eq("buyer@yala.pe"),
                eq("Comprador Pe"),
                eq("Pikachu Illustrator"),
                eq(750f),
                contains("/orders/50"),
                eq(50L));
    }
}
