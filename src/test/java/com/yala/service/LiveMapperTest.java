package com.yala.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.yala.dto.live.ResponseLiveStreamDTO;
import com.yala.model.LiveStatus;
import com.yala.model.LiveStream;
import com.yala.model.Role;
import com.yala.model.User;
import org.junit.jupiter.api.Test;

class LiveMapperTest {

    private final LiveMapper mapper = new LiveMapper();

    @Test
    void toStreamDetailMapsSellerWithoutThrowing() {
        User seller = User.builder()
                .id(7L).name("Ada Lovelace").email("ada@yala.pe")
                .role(Role.SELLER).isVerifiedSeller(true).isIdentityVerified(true)
                .build();
        LiveStream stream = LiveStream.builder()
                .id(1L).title("Sobres Pokémon 151").status(LiveStatus.LIVE)
                .roomName("live-abc").seller(seller)
                .build();

        ResponseLiveStreamDTO dto = mapper.toStreamDetail(stream, null);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.status()).isEqualTo("LIVE");
        assertThat(dto.seller()).isNotNull();
        assertThat(dto.seller().id()).isEqualTo(7L);
        assertThat(dto.seller().name()).isEqualTo("Ada Lovelace");
        assertThat(dto.seller().role()).isEqualTo(Role.SELLER);
        assertThat(dto.seller().isVerifiedSeller()).isTrue();
    }

    @Test
    void toStreamDetailIsNullSafeOnMissingSeller() {
        LiveStream stream = LiveStream.builder()
                .id(2L).title("Sin seller").status(LiveStatus.ENDED).roomName("live-z").build();
        assertThatCode(() -> {
            ResponseLiveStreamDTO dto = mapper.toStreamDetail(stream, null);
            assertThat(dto.seller()).isNull();
        }).doesNotThrowAnyException();
    }
}
