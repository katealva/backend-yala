package com.yala.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.auction.Auction;
import com.yala.auction.AuctionRepository;
import com.yala.auction.AuctionStatus;
import com.yala.bid.dto.BidResponse;
import com.yala.bid.dto.CreateBidRequest;
import com.yala.exceptions.AuctionNotActiveException;
import com.yala.exceptions.InvalidBidException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.listing.Listing;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock private BidRepository bidRepository;
    @Mock private AuctionRepository auctionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BidServiceImpl bidService;

    private User seller() {
        return User.builder().id(1L).name("Bob").email("bob@yala.pe")
                .role(Role.SELLER).isVerifiedSeller(true).build();
    }

    private User bidder() {
        return User.builder().id(2L).name("Ada").email("ada@yala.pe").role(Role.USER).build();
    }

    private Auction activeAuction() {
        Listing listing = Listing.builder().id(10L).seller(seller()).build();
        return Auction.builder()
                .id(100L).listing(listing).startingPrice(100f).currentPrice(150f)
                .endsAt(LocalDateTime.now().plusHours(1)).status(AuctionStatus.ACTIVE).build();
    }

    @Test
    void shouldPlaceBidWhenAmountIsHigherThanCurrentPrice() {
        Auction auction = activeAuction();
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(bidder()));
        when(bidRepository.save(any(Bid.class))).thenAnswer(inv -> {
            Bid b = inv.getArgument(0);
            b.setId(999L);
            return b;
        });

        BidResponse response = bidService.placeBid(
                new CreateBidRequest(100L, 200f), "ada@yala.pe");

        assertThat(response.id()).isEqualTo(999L);
        assertThat(response.amount()).isEqualTo(200f);
        assertThat(auction.getCurrentPrice()).isEqualTo(200f);
        verify(auctionRepository).save(auction);
    }

    @Test
    void shouldThrowInvalidBidExceptionWhenAmountIsLowerOrEqualThanCurrentPrice() {
        Auction auction = activeAuction();
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(bidder()));

        assertThatThrownBy(() -> bidService.placeBid(
                new CreateBidRequest(100L, 150f), "ada@yala.pe"))
                .isInstanceOf(InvalidBidException.class)
                .hasMessageContaining("greater than current price");
        verify(bidRepository, never()).save(any());
    }

    @Test
    void shouldThrowAuctionNotActiveExceptionWhenAuctionIsFinished() {
        Auction auction = activeAuction();
        auction.setStatus(AuctionStatus.FINISHED);
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> bidService.placeBid(
                new CreateBidRequest(100L, 200f), "ada@yala.pe"))
                .isInstanceOf(AuctionNotActiveException.class);
    }

    @Test
    void shouldThrowAuctionNotActiveExceptionWhenAuctionHasExpired() {
        Auction auction = activeAuction();
        auction.setEndsAt(LocalDateTime.now().minusMinutes(1));
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> bidService.placeBid(
                new CreateBidRequest(100L, 200f), "ada@yala.pe"))
                .isInstanceOf(AuctionNotActiveException.class)
                .hasMessageContaining("already ended");
    }

    @Test
    void shouldThrowInvalidBidExceptionWhenSellerBidsOnOwnAuction() {
        Auction auction = activeAuction();
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(userRepository.findByEmail("bob@yala.pe")).thenReturn(Optional.of(seller()));

        assertThatThrownBy(() -> bidService.placeBid(
                new CreateBidRequest(100L, 200f), "bob@yala.pe"))
                .isInstanceOf(InvalidBidException.class)
                .hasMessageContaining("cannot bid on their own");
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenAuctionDoesNotExist() {
        when(auctionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bidService.placeBid(
                new CreateBidRequest(404L, 200f), "ada@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReturnBidsPageWhenFindByAuctionInvoked() {
        Auction auction = activeAuction();
        Bid bid = Bid.builder().id(1L).amount(200f).auction(auction).bidder(bidder()).build();
        when(auctionRepository.existsById(100L)).thenReturn(true);
        when(bidRepository.findByAuctionId(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bid)));

        Page<BidResponse> page = bidService.findByAuction(100L, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).amount()).isEqualTo(200f);
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenFindByAuctionWithMissingAuction() {
        when(auctionRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> bidService.findByAuction(404L, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReturnHighestBidWhenAuctionHasBids() {
        Bid top = Bid.builder().id(5L).amount(500f).bidder(bidder()).build();
        when(auctionRepository.existsById(100L)).thenReturn(true);
        when(bidRepository.findFirstByAuctionIdOrderByAmountDesc(100L)).thenReturn(Optional.of(top));

        BidResponse response = bidService.findHighest(100L);

        assertThat(response.amount()).isEqualTo(500f);
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenAuctionHasNoBids() {
        when(auctionRepository.existsById(100L)).thenReturn(true);
        when(bidRepository.findFirstByAuctionIdOrderByAmountDesc(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bidService.findHighest(100L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
