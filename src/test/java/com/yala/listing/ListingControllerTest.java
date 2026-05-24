package com.yala.listing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.security.JwtService;
import com.yala.dto.category.ResponseCategoryDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.dto.listing.RequestListingDTO;
import com.yala.dto.listing.ResponseListingDTO;
import com.yala.model.Role;
import com.yala.dto.user.ResponseUserDTO;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ListingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ListingControllerTest.MethodSecurityTestConfig.class)
class ListingControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ListingService listingService;

    @MockitoBean
    private JwtService jwtService;

    private static Principal principal(String email, String role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private ResponseListingDTO sampleResponse(Long id) {
        return new ResponseListingDTO(
                id,
                "Charizard Holo",
                "Near mint",
                "FIXED",
                250.0f,
                "USED",
                "ACTIVE",
                LocalDateTime.now(),
                new ResponseUserDTO(1L, "Ada", "ada@yala.pe", null, 0f, true, Role.SELLER),
                new ResponseCategoryDTO(10L, "Pokémon TCG", "Cards"),
                List.of(),
                null);
    }

    private RequestListingDTO sampleRequest() {
        return new RequestListingDTO(
                "Charizard Holo", "Near mint", "FIXED", 250.0f, "USED", 10L, List.of("rare"));
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "SELLER")
    void shouldReturn201WhenSellerCreatesListing() throws Exception {
        when(listingService.create(any(RequestListingDTO.class), eq("ada@yala.pe")))
                .thenReturn(sampleResponse(99L));

        mockMvc.perform(post("/api/v1/listings")
                        .principal(principal("ada@yala.pe", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99L))
                .andExpect(jsonPath("$.mode").value("FIXED"));
    }

    @Test
    @WithMockUser(username = "admin@yala.pe", roles = "ADMIN")
    void shouldReturn201WhenAdminCreatesListing() throws Exception {
        when(listingService.create(any(RequestListingDTO.class), eq("admin@yala.pe")))
                .thenReturn(sampleResponse(100L));

        mockMvc.perform(post("/api/v1/listings")
                        .principal(principal("admin@yala.pe", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "cleo@yala.pe", roles = "USER")
    void shouldReturn403WhenRegularUserCreatesListing() throws Exception {
        mockMvc.perform(post("/api/v1/listings")
                        .principal(principal("cleo@yala.pe", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn200WithPagedListingsWhenFindAllInvoked() throws Exception {
        when(listingService.findAll(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse(1L)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturn200WithFilteredListingsWhenQueryParamsProvided() throws Exception {
        when(listingService.findAll(
                any(),
                eq("Pokémon TCG"),
                eq("FIXED"),
                eq("USED"),
                anyFloat(),
                anyFloat(),
                anyString()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse(1L))));

        mockMvc.perform(get("/api/v1/listings")
                        .param("category", "Pokémon TCG")
                        .param("mode", "FIXED")
                        .param("condition", "USED")
                        .param("minPrice", "50")
                        .param("maxPrice", "500")
                        .param("q", "charizard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L));
    }

    @Test
    void shouldReturn200WhenFindByIdInvokedWithExistingId() throws Exception {
        when(listingService.findById(5L)).thenReturn(sampleResponse(5L));

        mockMvc.perform(get("/api/v1/listings/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L));
    }

    @Test
    void shouldReturn404WhenFindByIdInvokedWithMissingId() throws Exception {
        when(listingService.findById(404L))
                .thenThrow(new ResourceNotFoundException("Listing not found with id: 404"));

        mockMvc.perform(get("/api/v1/listings/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "SELLER")
    void shouldReturn200WhenOwnerUpdatesListing() throws Exception {
        when(listingService.update(eq(7L), any(RequestListingDTO.class), eq("ada@yala.pe")))
                .thenReturn(sampleResponse(7L));

        mockMvc.perform(put("/api/v1/listings/7")
                        .principal(principal("ada@yala.pe", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7L));
    }

    @Test
    @WithMockUser(username = "intruder@yala.pe", roles = "SELLER")
    void shouldReturn401WhenNonOwnerUpdatesListing() throws Exception {
        when(listingService.update(eq(7L), any(RequestListingDTO.class), eq("intruder@yala.pe")))
                .thenThrow(new UnauthorizedException(
                        "Only the listing owner can perform this operation"));

        mockMvc.perform(put("/api/v1/listings/7")
                        .principal(principal("intruder@yala.pe", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "SELLER")
    void shouldReturn204WhenOwnerCancelsListing() throws Exception {
        mockMvc.perform(delete("/api/v1/listings/8")
                        .principal(principal("ada@yala.pe", "SELLER")))
                .andExpect(status().isNoContent());
    }
}
