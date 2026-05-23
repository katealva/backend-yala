package com.yala.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.exception.OrderNotConfirmableException;
import com.yala.exception.ResourceNotFoundException;
import com.yala.exception.UnauthorizedException;
import com.yala.listing.Listing;
import com.yala.listing.ListingMode;
import com.yala.listing.ListingRepository;
import com.yala.listing.ListingStatus;
import com.yala.order.dto.CreateOrderRequest;
import com.yala.order.dto.OrderResponse;
import com.yala.user.Role;
import com.yala.user.User;
import com.yala.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User buyer() {
        return User.builder().id(1L).name("Ada").email("ada@yala.pe").role(Role.USER).build();
    }

    private User seller() {
        return User.builder().id(2L).name("Bob").email("bob@yala.pe").role(Role.SELLER)
                .isVerifiedSeller(true).build();
    }

    private Listing fixedListing(ListingStatus status) {
        return Listing.builder()
                .id(10L).title("Charizard").mode(ListingMode.FIXED).fixedPrice(250f)
                .condition("USED").status(status).seller(seller()).build();
    }

    private Listing auctionListing() {
        return Listing.builder()
                .id(11L).title("Pikachu Illustrator").mode(ListingMode.AUCTION)
                .condition("NEW").status(ListingStatus.ACTIVE).seller(seller()).build();
    }

    @Test
    void shouldCreateOrderWhenListingIsFixedAndActive() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(buyer()));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(fixedListing(ListingStatus.ACTIVE)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(99L);
            return o;
        });

        OrderResponse response = orderService.create(new CreateOrderRequest(10L), "ada@yala.pe");

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.amount()).isEqualTo(250f);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(listingRepository).save(any(Listing.class));
    }

    @Test
    void shouldThrowOrderNotConfirmableExceptionWhenListingIsAuction() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(buyer()));
        when(listingRepository.findById(11L)).thenReturn(Optional.of(auctionListing()));

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(11L), "ada@yala.pe"))
                .isInstanceOf(OrderNotConfirmableException.class)
                .hasMessageContaining("FIXED");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowOrderNotConfirmableExceptionWhenListingIsSold() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(buyer()));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(fixedListing(ListingStatus.SOLD)));

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(10L), "ada@yala.pe"))
                .isInstanceOf(OrderNotConfirmableException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void shouldThrowOrderNotConfirmableExceptionWhenBuyerIsAlsoSeller() {
        User selfSeller = seller();
        when(userRepository.findByEmail("bob@yala.pe")).thenReturn(Optional.of(selfSeller));
        Listing listing = fixedListing(ListingStatus.ACTIVE);
        listing.setSeller(selfSeller);
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(10L), "bob@yala.pe"))
                .isInstanceOf(OrderNotConfirmableException.class)
                .hasMessageContaining("cannot buy their own");
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenListingDoesNotExist() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(buyer()));
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(404L), "ada@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldConfirmOrderWhenSellerInvokesAndStatusIsPending() {
        Order order = Order.builder()
                .id(50L).amount(250f).status(OrderStatus.PENDING)
                .listing(fixedListing(ListingStatus.SOLD))
                .buyer(buyer()).seller(seller()).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.confirm(50L, "bob@yala.pe");

        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenNonSellerConfirms() {
        Order order = Order.builder()
                .id(50L).amount(250f).status(OrderStatus.PENDING)
                .listing(fixedListing(ListingStatus.SOLD))
                .buyer(buyer()).seller(seller()).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirm(50L, "ada@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void shouldThrowOrderNotConfirmableExceptionWhenConfirmingNonPendingOrder() {
        Order order = Order.builder()
                .id(50L).amount(250f).status(OrderStatus.CONFIRMED)
                .listing(fixedListing(ListingStatus.SOLD))
                .buyer(buyer()).seller(seller()).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirm(50L, "bob@yala.pe"))
                .isInstanceOf(OrderNotConfirmableException.class);
    }

    @Test
    void shouldCancelOrderWhenBuyerInvokesAndStatusIsPending() {
        Listing listing = fixedListing(ListingStatus.SOLD);
        Order order = Order.builder()
                .id(60L).amount(250f).status(OrderStatus.PENDING)
                .listing(listing).buyer(buyer()).seller(seller()).build();
        when(orderRepository.findById(60L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancel(60L, "ada@yala.pe");

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        verify(listingRepository).save(listing);
    }

    @Test
    void shouldCancelOrderWhenSellerInvokesAndStatusIsPending() {
        Listing listing = fixedListing(ListingStatus.SOLD);
        Order order = Order.builder()
                .id(60L).amount(250f).status(OrderStatus.PENDING)
                .listing(listing).buyer(buyer()).seller(seller()).build();
        when(orderRepository.findById(60L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancel(60L, "bob@yala.pe");

        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldThrowOrderNotConfirmableExceptionWhenCancellingConfirmedOrder() {
        Order order = Order.builder()
                .id(60L).amount(250f).status(OrderStatus.CONFIRMED)
                .listing(fixedListing(ListingStatus.SOLD))
                .buyer(buyer()).seller(seller()).build();
        when(orderRepository.findById(60L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(60L, "ada@yala.pe"))
                .isInstanceOf(OrderNotConfirmableException.class);
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenStrangerCancels() {
        Order order = Order.builder()
                .id(60L).amount(250f).status(OrderStatus.PENDING)
                .listing(fixedListing(ListingStatus.SOLD))
                .buyer(buyer()).seller(seller()).build();
        when(orderRepository.findById(60L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(60L, "intruder@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void shouldReturnOrderWhenFindByIdInvokedByBuyer() {
        Order order = Order.builder()
                .id(70L).amount(250f).status(OrderStatus.PENDING)
                .listing(fixedListing(ListingStatus.SOLD))
                .buyer(buyer()).seller(seller()).build();
        when(orderRepository.findById(70L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.findById(70L, "ada@yala.pe");

        assertThat(response.id()).isEqualTo(70L);
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenFindByIdInvokedByStranger() {
        Order order = Order.builder()
                .id(70L).amount(250f).status(OrderStatus.PENDING)
                .listing(fixedListing(ListingStatus.SOLD))
                .buyer(buyer()).seller(seller()).build();
        when(orderRepository.findById(70L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findById(70L, "intruder@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
