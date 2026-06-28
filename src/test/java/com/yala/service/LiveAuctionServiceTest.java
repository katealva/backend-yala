package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.config.ModelMapperConfig;
import com.yala.dto.live.RequestFlashAuctionDTO;
import com.yala.dto.live.ResponseLiveAuctionDTO;
import com.yala.event.LiveAuctionClosedEvent;
import com.yala.event.LiveAuctionStartedEvent;
import com.yala.exceptions.AuctionNotActiveException;
import com.yala.exceptions.DuplicateResourceException;
import com.yala.exceptions.UnauthorizedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LiveAuctionServiceTest {

    @Mock private LiveAuctionRepository liveAuctionRepository;
    @Mock private LiveStreamRepository liveStreamRepository;
    @Mock private LiveBidRepository liveBidRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Spy
    private LiveMapper liveMapper = new LiveMapper();

    @InjectMocks
    private LiveAuctionService liveAuctionService;

    private User seller() {
        return User.builder().id(1L).name("Bob").email("bob@yala.pe")
                .role(Role.SELLER).isVerifiedSeller(true).build();
    }

    private User bidder() {
        return User.builder().id(2L).name("Ada").email("ada@yala.pe").role(Role.USER).build();
    }

    private LiveStream liveStream(LiveStatus status) {
        return LiveStream.builder().id(10L).title("Live").roomName("room-1")
                .status(status).seller(seller()).build();
    }

    private LiveAuction activeAuction() {
        return LiveAuction.builder().id(100L).liveStream(liveStream(LiveStatus.LIVE))
                .title("Charizard").basePrice(50f).bidIncrement(1f).currentPrice(60f)
                .status(LiveAuctionStatus.ACTIVE).build();
    }

    @Test
    void shouldCreateFlashAuctionWithDefaultIncrementWhenNull() {
        when(liveStreamRepository.findById(10L)).thenReturn(Optional.of(liveStream(LiveStatus.LIVE)));
        when(liveAuctionRepository.findFirstByLiveStreamIdAndStatusOrderByStartedAtDesc(
                10L, LiveAuctionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(liveAuctionRepository.save(any(LiveAuction.class))).thenAnswer(inv -> {
            LiveAuction a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        ResponseLiveAuctionDTO dto = liveAuctionService.create(
                10L, new RequestFlashAuctionDTO("Charizard", 50f, null), "bob@yala.pe");

        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.bidIncrement()).isEqualTo(1f);
        assertThat(dto.basePrice()).isEqualTo(50f);
        verify(eventPublisher).publishEvent(any(LiveAuctionStartedEvent.class));
    }

    @Test
    void shouldCreateFlashAuctionWithProvidedIncrement() {
        when(liveStreamRepository.findById(10L)).thenReturn(Optional.of(liveStream(LiveStatus.LIVE)));
        when(liveAuctionRepository.findFirstByLiveStreamIdAndStatusOrderByStartedAtDesc(
                10L, LiveAuctionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(liveAuctionRepository.save(any(LiveAuction.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseLiveAuctionDTO dto = liveAuctionService.create(
                10L, new RequestFlashAuctionDTO("Charizard", 50f, 5f), "bob@yala.pe");

        assertThat(dto.bidIncrement()).isEqualTo(5f);
    }

    @Test
    void shouldRejectCreateWhenNotHost() {
        when(liveStreamRepository.findById(10L)).thenReturn(Optional.of(liveStream(LiveStatus.LIVE)));

        assertThatThrownBy(() -> liveAuctionService.create(
                10L, new RequestFlashAuctionDTO("Charizard", 50f, null), "eve@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(liveAuctionRepository, never()).save(any());
    }

    @Test
    void shouldRejectCreateWhenStreamNotLive() {
        when(liveStreamRepository.findById(10L)).thenReturn(Optional.of(liveStream(LiveStatus.ENDED)));

        assertThatThrownBy(() -> liveAuctionService.create(
                10L, new RequestFlashAuctionDTO("Charizard", 50f, null), "bob@yala.pe"))
                .isInstanceOf(AuctionNotActiveException.class);
    }

    @Test
    void shouldRejectCreateWhenAnotherAuctionActive() {
        when(liveStreamRepository.findById(10L)).thenReturn(Optional.of(liveStream(LiveStatus.LIVE)));
        when(liveAuctionRepository.findFirstByLiveStreamIdAndStatusOrderByStartedAtDesc(
                10L, LiveAuctionStatus.ACTIVE)).thenReturn(Optional.of(activeAuction()));

        assertThatThrownBy(() -> liveAuctionService.create(
                10L, new RequestFlashAuctionDTO("Pikachu", 30f, null), "bob@yala.pe"))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void shouldCloseAsSoldWhenThereAreBids() {
        LiveAuction auction = activeAuction();
        LiveBid winning = LiveBid.builder().id(7L).amount(60f).bidder(bidder()).liveAuction(auction).build();
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(liveBidRepository.findFirstByLiveAuctionIdOrderByAmountDesc(100L))
                .thenReturn(Optional.of(winning));
        when(liveBidRepository.countByLiveAuctionId(100L)).thenReturn(1L);

        ResponseLiveAuctionDTO dto = liveAuctionService.close(100L, "bob@yala.pe");

        assertThat(dto.status()).isEqualTo("SOLD");
        assertThat(dto.winnerName()).isEqualTo("Ada");
        assertThat(auction.getWinningAmount()).isEqualTo(60f);
        verify(eventPublisher).publishEvent(any(LiveAuctionClosedEvent.class));
    }

    @Test
    void shouldCloseAsDesertedWhenNoBids() {
        LiveAuction auction = activeAuction();
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));
        when(liveBidRepository.findFirstByLiveAuctionIdOrderByAmountDesc(100L))
                .thenReturn(Optional.empty());
        when(liveBidRepository.countByLiveAuctionId(100L)).thenReturn(0L);

        ResponseLiveAuctionDTO dto = liveAuctionService.close(100L, "bob@yala.pe");

        assertThat(dto.status()).isEqualTo("DESERTED");
        assertThat(auction.getWinner()).isNull();
    }

    @Test
    void shouldRejectCloseWhenNotHost() {
        LiveAuction auction = activeAuction();
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> liveAuctionService.close(100L, "eve@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void shouldRejectCloseWhenNotActive() {
        LiveAuction auction = activeAuction();
        auction.setStatus(LiveAuctionStatus.SOLD);
        when(liveAuctionRepository.findById(100L)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> liveAuctionService.close(100L, "bob@yala.pe"))
                .isInstanceOf(AuctionNotActiveException.class);
    }
}
