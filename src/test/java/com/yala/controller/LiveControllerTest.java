package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.dto.live.RequestFlashAuctionDTO;
import com.yala.dto.live.RequestLiveBidDTO;
import com.yala.dto.live.RequestStartLiveDTO;
import com.yala.dto.live.ResponseLiveAuctionDTO;
import com.yala.dto.live.ResponseLiveBidDTO;
import com.yala.dto.live.ResponseLiveStreamDTO;
import com.yala.dto.live.ResponseLiveSummaryDTO;
import com.yala.dto.live.ResponseLiveTokenDTO;
import com.yala.security.JwtService;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LiveController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LiveControllerTest.MethodSecurityTestConfig.class)
class LiveControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private LiveStreamService liveStreamService;
    @MockitoBean private LiveAuctionService liveAuctionService;
    @MockitoBean private LiveBidService liveBidService;
    @MockitoBean private LiveCommentService liveCommentService;
    @MockitoBean private JwtService jwtService;

    private static Principal principal(String email, String role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void shouldListActiveLives() throws Exception {
        var summary = new ResponseLiveSummaryDTO(
                12L, "Live de prueba", "LIVE", null, "Bob", 1L, LocalDateTime.now());
        when(liveStreamService.listActive(any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/api/v1/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("LIVE"));
    }

    @Test
    void shouldGetLiveDetail() throws Exception {
        var detail = new ResponseLiveStreamDTO(
                12L, "Live", "LIVE", "room-1", null, LocalDateTime.now(), null, null, null);
        when(liveStreamService.findById(12L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/live/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12));
    }

    @Test
    @WithMockUser(username = "bob@yala.pe", roles = "SELLER")
    void shouldStartLiveAsSeller() throws Exception {
        when(liveStreamService.start(any(RequestStartLiveDTO.class), any()))
                .thenReturn(new ResponseLiveTokenDTO(12L, "room-1", "wss://lk", "jwt-token"));

        mockMvc.perform(post("/api/v1/live")
                        .principal(principal("bob@yala.pe", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestStartLiveDTO("Mi live", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    @WithMockUser(username = "bob@yala.pe", roles = "SELLER")
    void shouldCreateFlashAuctionAsSeller() throws Exception {
        when(liveAuctionService.create(eq(12L), any(RequestFlashAuctionDTO.class), any()))
                .thenReturn(new ResponseLiveAuctionDTO(
                        88L, 12L, "Charizard", 50f, 1f, null, "ACTIVE", null, 0, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/live/12/auctions")
                        .principal(principal("bob@yala.pe", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RequestFlashAuctionDTO("Charizard", 50f, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "USER")
    void shouldPlaceLiveBidAsUser() throws Exception {
        when(liveBidService.place(eq(88L), any(RequestLiveBidDTO.class), any()))
                .thenReturn(new ResponseLiveBidDTO(900L, 56f, LocalDateTime.now(), null));

        mockMvc.perform(post("/api/v1/live/auctions/88/bids")
                        .principal(principal("ada@yala.pe", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestLiveBidDTO(56f))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(56.0));
    }

    @Test
    void shouldGetWatchTokenPublicly() throws Exception {
        when(liveStreamService.watchToken(eq(12L), any()))
                .thenReturn(new ResponseLiveTokenDTO(12L, "room-1", "wss://lk", "viewer-token"));

        mockMvc.perform(post("/api/v1/live/12/watch-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("viewer-token"));
    }
}
