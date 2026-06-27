package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.config.ModelMapperConfig;
import com.yala.dto.live.RequestLiveBidDTO;
import com.yala.dto.live.ResponseLiveBidDTO;
import com.yala.exceptions.AuctionNotActiveException;
import com.yala.exceptions.InvalidBidException;
import com.yala.exceptions.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LiveBidServiceTest {

    @Mock private LiveBidRepository liveBidRepository;
    @Mock private LiveAuctionRepository liveAuctionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Spy
    private LiveMapper liveMapper = new LiveMapper(new ModelMapperConfig().modelMapper());

    @InjectMocks
    private LiveBidService liveBidService;

    private User seller() {
        return User.builder().id(1L).name("Bob").email("bob@yala.pe")
                .role(Role.SELLER).isVerifiedSeller(true).build();
    }

    private User bidder() {
        return User.builder().id(2L).name("Ada").email("ada@yala.pe").role(Role.USER).build();
    }

    private LiveStream liveStream() {
        return LiveStream.builder().id(10L).title("Live").roomName("room-1")
                .status(LiveStatus.LIVE).seller(seller()).build();
    }

    private LiveAuction activeAuction(Float currentPrice) {
        return LiveAuction.builder().id(100L).liveStream(liveStream())
                .title("Charizard").basePrice(50f).bidIncrement(1f).currentPrice(currentPrice)
                .status(LiveAuctionStatus.ACTIVE).build();
    }

    @Test
    void shouldPlaceFirstBidAtOrAboveBasePrice() {
        LiveAuction auction = activeAuction(null);
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(bidder()));
        when(liveBidRepository.findFirstByLiveAuctionIdOrderByAmountDesc(100L))
                .thenReturn(Optional.empty());
        when(liveBidRepository.save(any(LiveBid.class))).thenAnswer(inv -> {
            LiveBid b = inv.getArgument(0);
            b.setId(999L);
            return b;
        });

        ResponseLiveBidDTO response = liveBidService.place(100L, new RequestLiveBidDTO(50f), "ada@yala.pe");

        assertThat(response.id()).isEqualTo(999L);
        assertThat(response.amount()).isEqualTo(50f);
        assertThat(auction.getCurrentPrice()).isEqualTo(50f);
        verify(liveAuctionRepository).save(auction);
    }

    @Test
    void shouldRejectFirstBidBelowBasePrice() {
        LiveAuction auction = activeAuction(null);
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(bidder()));

        assertThatThrownBy(() -> liveBidService.place(100L, new RequestLiveBidDTO(40f), "ada@yala.pe"))
                .isInstanceOf(InvalidBidException.class);
        verify(liveBidRepository, never()).save(any());
    }

    @Test
    void shouldRejectBidBelowCurrentPlusIncrement() {
        LiveAuction auction = activeAuction(55f);
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(bidder()));

        assertThatThrownBy(() -> liveBidService.place(100L, new RequestLiveBidDTO(55f), "ada@yala.pe"))
                .isInstanceOf(InvalidBidException.class);
    }

    @Test
    void shouldRejectBidWhenAuctionNotActive() {
        LiveAuction auction = activeAuction(55f);
        auction.setStatus(LiveAuctionStatus.SOLD);
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> liveBidService.place(100L, new RequestLiveBidDTO(60f), "ada@yala.pe"))
                .isInstanceOf(AuctionNotActiveException.class);
    }

    @Test
    void shouldRejectBidWhenLiveEnded() {
        LiveAuction auction = activeAuction(55f);
        auction.getLiveStream().setStatus(LiveStatus.ENDED);
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> liveBidService.place(100L, new RequestLiveBidDTO(60f), "ada@yala.pe"))
                .isInstanceOf(AuctionNotActiveException.class);
    }

    @Test
    void shouldRejectHostBiddingOwnAuction() {
        LiveAuction auction = activeAuction(55f);
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(userRepository.findByEmail("bob@yala.pe")).thenReturn(Optional.of(seller()));

        assertThatThrownBy(() -> liveBidService.place(100L, new RequestLiveBidDTO(60f), "bob@yala.pe"))
                .isInstanceOf(InvalidBidException.class)
                .hasMessageContaining("cannot bid");
    }

    @Test
    void shouldThrowNotFoundWhenAuctionMissing() {
        when(liveAuctionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> liveBidService.place(404L, new RequestLiveBidDTO(60f), "ada@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
