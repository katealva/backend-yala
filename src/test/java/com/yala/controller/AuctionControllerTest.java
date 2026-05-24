package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yala.dto.auction.ResponseAuctionDTO;
import com.yala.dto.auction.ResponseAuctionSummaryDTO;
import com.yala.dto.auction.RequestAuctionDTO;
import com.yala.security.JwtService;
import com.yala.exceptions.DuplicateResourceException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuctionController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuctionControllerTest {

    @Autowired private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean private AuctionService auctionService;
    @MockitoBean private JwtService jwtService;

    private static UsernamePasswordAuthenticationToken principal(String email) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_SELLER")));
    }

    private ResponseAuctionSummaryDTO summaryResponse() {
        return new ResponseAuctionSummaryDTO(100L, 100f, LocalDateTime.now().plusDays(1), "ACTIVE");
    }

    private ResponseAuctionDTO fullResponse() {
        return new ResponseAuctionDTO(100L, 100f, 100f, LocalDateTime.now(),
                LocalDateTime.now().plusDays(1), "ACTIVE", null, 0);
    }

    @Test
    void shouldReturn200WithActiveAuctionsPageWhenFindAllActiveInvoked() throws Exception {
        when(auctionService.findAllActive(any()))
                .thenReturn(new PageImpl<>(List.of(summaryResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(100L));
    }

    @Test
    void shouldReturn200WithAuctionWhenFindByIdExists() throws Exception {
        when(auctionService.findById(100L)).thenReturn(fullResponse());

        mockMvc.perform(get("/api/v1/auctions/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturn404WhenAuctionDoesNotExist() throws Exception {
        when(auctionService.findById(404L))
                .thenThrow(new ResourceNotFoundException("Auction not found with id: 404"));

        mockMvc.perform(get("/api/v1/auctions/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn201WhenCreateAuctionRequestIsValid() throws Exception {
        RequestAuctionDTO request = new RequestAuctionDTO(
                10L, 100f, LocalDateTime.now().plusDays(1));
        when(auctionService.create(any(RequestAuctionDTO.class), eq("bob@yala.pe")))
                .thenReturn(fullResponse());

        mockMvc.perform(post("/api/v1/auctions")
                        .principal(principal("bob@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturn400WhenCreateAuctionRequestIsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/auctions")
                        .principal(principal("bob@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenSellerIsNotListingOwner() throws Exception {
        RequestAuctionDTO request = new RequestAuctionDTO(
                10L, 100f, LocalDateTime.now().plusDays(1));
        when(auctionService.create(any(RequestAuctionDTO.class), eq("eve@yala.pe")))
                .thenThrow(new UnauthorizedException("Only the seller of this listing can create an auction"));

        mockMvc.perform(post("/api/v1/auctions")
                        .principal(principal("eve@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn409WhenAuctionAlreadyExistsForListing() throws Exception {
        RequestAuctionDTO request = new RequestAuctionDTO(
                10L, 100f, LocalDateTime.now().plusDays(1));
        when(auctionService.create(any(RequestAuctionDTO.class), eq("bob@yala.pe")))
                .thenThrow(new DuplicateResourceException("Listing 10 already has an auction"));

        mockMvc.perform(post("/api/v1/auctions")
                        .principal(principal("bob@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
