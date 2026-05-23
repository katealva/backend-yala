package com.yala.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.auction.Auction;
import com.yala.auction.AuctionRepository;
import com.yala.auction.AuctionStatus;
import com.yala.exception.ResourceNotFoundException;
import com.yala.listing.Listing;
import com.yala.listing.ListingRepository;
import com.yala.listing.ListingStatus;
import com.yala.notification.NotificationService;
import com.yala.notification.NotificationType;
import com.yala.order.Order;
import com.yala.order.OrderRepository;
import com.yala.user.Role;
import com.yala.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventListenersTest {

    @Mock private NotificationService notificationService;
    @Mock private AuctionRepository auctionRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ListingRepository listingRepository;

    @InjectMocks
    private EventListeners eventListeners;

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
        NewBidEvent event = new NewBidEvent(this, 100L, 250f, 5L, 6L);

        eventListeners.onNewBid(event);

        verify(notificationService).createNotification(
                eq(5L),
                eq(NotificationType.BID_OUTBID),
                org.mockito.ArgumentMatchers.contains("250"));
    }

    @Test
    void shouldNotInvokeNotificationServiceWhenOnNewBidHasNoPreviousBidder() {
        NewBidEvent event = new NewBidEvent(this, 100L, 250f, null, 6L);

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

        eventListeners.onAuctionFinished(new AuctionFinishedEvent(this, 100L));

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

        eventListeners.onAuctionFinished(new AuctionFinishedEvent(this, 100L));

        verify(orderRepository, never()).save(any());
        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenAuctionFinishedAuctionMissing() {
        when(auctionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                eventListeners.onAuctionFinished(new AuctionFinishedEvent(this, 404L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldNotifyBuyerWhenOnOrderConfirmedInvoked() {
        eventListeners.onOrderConfirmed(new OrderConfirmedEvent(this, 50L, 10L, 20L));

        verify(notificationService).createNotification(
                eq(10L), eq(NotificationType.SALE_CONFIRMED),
                org.mockito.ArgumentMatchers.contains("confirmed"));
    }
}
