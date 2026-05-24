package com.yala.bid;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.security.JwtService;
import com.yala.dto.bid.ResponseBidDTO;
import com.yala.dto.bid.RequestBidDTO;
import com.yala.exceptions.AuctionNotActiveException;
import com.yala.exceptions.InvalidBidException;
import java.security.Principal;
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

@WebMvcTest(BidController.class)
@AutoConfigureMockMvc(addFilters = false)
class BidControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private BidService bidService;

    @MockitoBean
    private JwtService jwtService;

    private static Principal principal(String email) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private ResponseBidDTO sampleBid(Long id, Float amount) {
        return new ResponseBidDTO(id, amount, LocalDateTime.now(), null);
    }

    @Test
    void shouldReturn201WhenPlaceBidIsValid() throws Exception {
        when(bidService.placeBid(any(RequestBidDTO.class), eq("ada@yala.pe")))
                .thenReturn(sampleBid(999L, 200f));

        mockMvc.perform(post("/api/v1/bids")
                        .principal(principal("ada@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestBidDTO(100L, 200f))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(999L))
                .andExpect(jsonPath("$.amount").value(200f));
    }

    @Test
    void shouldReturn400WhenBidAmountIsTooLow() throws Exception {
        when(bidService.placeBid(any(RequestBidDTO.class), eq("ada@yala.pe")))
                .thenThrow(new InvalidBidException("Bid must be greater than current price: 150.0"));

        mockMvc.perform(post("/api/v1/bids")
                        .principal(principal("ada@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestBidDTO(100L, 100f))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409WhenAuctionIsNotActive() throws Exception {
        when(bidService.placeBid(any(RequestBidDTO.class), eq("ada@yala.pe")))
                .thenThrow(new AuctionNotActiveException("Auction 100 is not active"));

        mockMvc.perform(post("/api/v1/bids")
                        .principal(principal("ada@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestBidDTO(100L, 200f))))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400WhenRequestBodyIsMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/bids")
                        .principal(principal("ada@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn200WithBidsPageWhenFindByAuctionInvoked() throws Exception {
        when(bidService.findByAuction(eq(100L), any()))
                .thenReturn(new PageImpl<>(
                        List.of(sampleBid(1L, 200f), sampleBid(2L, 180f)),
                        PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/api/v1/bids/auction/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(200f));
    }

    @Test
    void shouldReturn200WithHighestBidWhenAvailable() throws Exception {
        when(bidService.findHighest(100L)).thenReturn(sampleBid(5L, 500f));

        mockMvc.perform(get("/api/v1/bids/auction/100/highest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500f));
    }
}
