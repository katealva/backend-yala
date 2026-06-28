package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.dto.identity.ResponseSessionDTO;
import com.yala.dto.seller.RequestSellerApplicationDTO;
import com.yala.dto.seller.ResponseSellerApplicationDTO;
import com.yala.exceptions.DuplicateResourceException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerApplicationServiceTest {

    @Mock private SellerApplicationRepository sellerApplicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private IdentityService identityService;

    @InjectMocks
    private SellerApplicationService service;

    private User user(Role role) {
        return User.builder().id(2L).name("Ada").email("ada@yala.pe")
                .role(role).isVerifiedSeller(role == Role.SELLER).build();
    }

    private RequestSellerApplicationDTO request() {
        return new RequestSellerApplicationDTO("CardVault PE", "Av. Ejemplo 123", "+51 999 999 999",
                "00219300012345678901");
    }

    @Test
    void shouldCreatePendingApplicationAndStartDiditSession() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(user(Role.USER)));
        when(sellerApplicationRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(
                2L, SellerApplicationStatus.PENDING)).thenReturn(Optional.empty());
        when(sellerApplicationRepository.save(any(SellerApplication.class))).thenAnswer(inv -> {
            SellerApplication a = inv.getArgument(0);
            a.setId(5L);
            return a;
        });
        when(identityService.isConfigured()).thenReturn(true);
        when(identityService.createSession("ada@yala.pe"))
                .thenReturn(new ResponseSessionDTO("https://verify.didit.me/s/abc", "sess-1"));

        ResponseSellerApplicationDTO dto = service.apply("ada@yala.pe", request());

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.storeName()).isEqualTo("CardVault PE");
        assertThat(dto.diditUrl()).isEqualTo("https://verify.didit.me/s/abc");
        verify(identityService).createSession("ada@yala.pe");
    }

    @Test
    void shouldPropagateWhenDiditSessionFailsWhileConfigured() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(user(Role.USER)));
        when(sellerApplicationRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(
                2L, SellerApplicationStatus.PENDING)).thenReturn(Optional.empty());
        when(sellerApplicationRepository.save(any(SellerApplication.class))).thenAnswer(inv -> {
            SellerApplication a = inv.getArgument(0);
            a.setId(5L);
            return a;
        });
        when(identityService.isConfigured()).thenReturn(true);
        when(identityService.createSession("ada@yala.pe"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Didit session creation failed"));

        // No se traga el error: propaga para que el front muestre un fallo real (y la tx hace rollback).
        assertThatThrownBy(() -> service.apply("ada@yala.pe", request()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectWhenUserIsAlreadySeller() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(user(Role.SELLER)));

        assertThatThrownBy(() -> service.apply("ada@yala.pe", request()))
                .isInstanceOf(DuplicateResourceException.class);
        verify(sellerApplicationRepository, never()).save(any());
    }
}
