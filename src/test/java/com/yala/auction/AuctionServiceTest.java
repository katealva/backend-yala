package com.yala.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.auction.dto.AuctionResponse;
import com.yala.auction.dto.AuctionSummaryResponse;
import com.yala.auction.dto.CreateAuctionRequest;
import com.yala.bid.Bid;
import com.yala.bid.BidRepository;
import com.yala.config.ModelMapperConfig;
import com.yala.exceptions.DuplicateResourceException;
import com.yala.exceptions.InvalidBidException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.event.AuctionFinishedEvent;
import com.yala.listing.Listing;
import com.yala.listing.ListingRepository;
import com.yala.user.Role;
import com.yala.user.User;
import com.yala.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock private AuctionRepository auctionRepository;
    @Mock private BidRepository bidRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @InjectMocks
    private AuctionServiceImpl auctionService;

    private User seller() {
        return User.builder().id(1L).name("Bob").email("bob@yala.pe")
                .role(Role.SELLER).isVerifiedSeller(true).build();
    }

    private Listing listing(User owner) {
        return Listing.builder().id(10L).seller(owner).title("Charizard PSA 9").build();
    }

    private Auction activeAuction(Listing lst) {
        return Auction.builder()
                .id(100L).listing(lst).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().plusDays(1))
                .status(AuctionStatus.ACTIVE).build();
    }

    @Test
    void shouldCreateAuctionWhenRequestIsValid() {
        User owner = seller();
        Listing lst = listing(owner);
        Auction saved = activeAuction(lst);

        when(listingRepository.findById(10L)).thenReturn(Optional.of(lst));
        when(userRepository.findByEmail("bob@yala.pe")).thenReturn(Optional.of(owner));
        when(auctionRepository.findByListingId(10L)).thenReturn(Optional.empty());
        when(auctionRepository.save(any(Auction.class))).thenReturn(saved);

        AuctionResponse response = auctionService.create(
                new CreateAuctionRequest(10L, 100f, LocalDateTime.now().plusDays(1)), "bob@yala.pe");

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.currentPrice()).isEqualTo(100f);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenListingDoesNotExist() {
        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionService.create(
                new CreateAuctionRequest(99L, 100f, LocalDateTime.now().plusDays(1)), "bob@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenSellerIsNotListingOwner() {
        User owner = seller();
        User other = User.builder().id(2L).name("Eve").email("eve@yala.pe").role(Role.SELLER).build();
        Listing lst = listing(owner);

        when(listingRepository.findById(10L)).thenReturn(Optional.of(lst));
        when(userRepository.findByEmail("eve@yala.pe")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> auctionService.create(
                new CreateAuctionRequest(10L, 100f, LocalDateTime.now().plusDays(1)), "eve@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void shouldThrowDuplicateResourceExceptionWhenAuctionAlreadyExistsForListing() {
        User owner = seller();
        Listing lst = listing(owner);
        Auction existing = activeAuction(lst);

        when(listingRepository.findById(10L)).thenReturn(Optional.of(lst));
        when(userRepository.findByEmail("bob@yala.pe")).thenReturn(Optional.of(owner));
        when(auctionRepository.findByListingId(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> auctionService.create(
                new CreateAuctionRequest(10L, 100f, LocalDateTime.now().plusDays(1)), "bob@yala.pe"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void shouldThrowInvalidBidExceptionWhenEndsAtIsInThePast() {
        User owner = seller();
        Listing lst = listing(owner);

        when(listingRepository.findById(10L)).thenReturn(Optional.of(lst));
        when(userRepository.findByEmail("bob@yala.pe")).thenReturn(Optional.of(owner));
        when(auctionRepository.findByListingId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionService.create(
                new CreateAuctionRequest(10L, 100f, LocalDateTime.now().minusHours(1)), "bob@yala.pe"))
                .isInstanceOf(InvalidBidException.class)
                .hasMessageContaining("future");
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void shouldReturnAuctionResponseWhenAuctionExists() {
        Auction auction = activeAuction(listing(seller()));
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(auction));

        AuctionResponse response = auctionService.findById(100L);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenAuctionDoesNotExist() {
        when(auctionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionService.findById(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReturnPagedAuctionsWhenActiveAuctionsExist() {
        Auction auction = activeAuction(listing(seller()));
        Page<Auction> page = new PageImpl<>(List.of(auction));
        when(auctionRepository.findByStatus(AuctionStatus.ACTIVE, PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<AuctionSummaryResponse> result = auctionService.findAllActive(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(100L);
    }

    @Test
    void shouldCloseExpiredAuctionAndSetWinnerWhenWinningBidExists() {
        Listing lst = listing(seller());
        Auction auction = activeAuction(lst);
        User winner = User.builder().id(3L).name("Ada").email("ada@yala.pe").role(Role.USER).build();
        Bid winningBid = Bid.builder().id(1L).amount(200f).bidder(winner).auction(auction).build();

        when(auctionRepository.findByStatusAndEndsAtBefore(any(), any())).thenReturn(List.of(auction));
        when(bidRepository.findFirstByAuctionIdOrderByAmountDesc(100L)).thenReturn(Optional.of(winningBid));

        auctionService.closeExpiredAuctions();

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FINISHED);
        assertThat(auction.getWinner()).isEqualTo(winner);
        verify(auctionRepository).save(auction);
        verify(eventPublisher).publishEvent(any(AuctionFinishedEvent.class));
    }

    @Test
    void shouldCloseExpiredAuctionWithNullWinnerWhenNoBidsExist() {
        Listing lst = listing(seller());
        Auction auction = activeAuction(lst);

        when(auctionRepository.findByStatusAndEndsAtBefore(any(), any())).thenReturn(List.of(auction));
        when(bidRepository.findFirstByAuctionIdOrderByAmountDesc(100L)).thenReturn(Optional.empty());

        auctionService.closeExpiredAuctions();

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FINISHED);
        assertThat(auction.getWinner()).isNull();
        verify(auctionRepository).save(auction);
        verify(eventPublisher).publishEvent(any(AuctionFinishedEvent.class));
    }

    @Test
    void shouldNotCloseAnyAuctionWhenNoExpiredAuctionsExist() {
        when(auctionRepository.findByStatusAndEndsAtBefore(any(), any())).thenReturn(List.of());

        auctionService.closeExpiredAuctions();

        verify(auctionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldPublishAuctionFinishedEventForEachExpiredAuction() {
        Listing lst1 = listing(seller());
        Listing lst2 = Listing.builder().id(20L).seller(seller()).title("Pikachu").build();
        Auction auction1 = activeAuction(lst1);
        Auction auction2 = Auction.builder().id(200L).listing(lst2)
                .startingPrice(50f).currentPrice(50f)
                .endsAt(LocalDateTime.now().minusMinutes(1))
                .status(AuctionStatus.ACTIVE).build();

        when(auctionRepository.findByStatusAndEndsAtBefore(any(), any()))
                .thenReturn(List.of(auction1, auction2));
        when(bidRepository.findFirstByAuctionIdOrderByAmountDesc(any()))
                .thenReturn(Optional.empty());

        auctionService.closeExpiredAuctions();

        verify(eventPublisher, times(2)).publishEvent(any(AuctionFinishedEvent.class));
        verify(auctionRepository, times(2)).save(any(Auction.class));
    }
}
