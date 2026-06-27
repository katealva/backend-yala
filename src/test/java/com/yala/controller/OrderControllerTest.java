package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.security.JwtService;
import com.yala.exceptions.OrderNotConfirmableException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.dto.order.RequestOrderDTO;
import com.yala.dto.order.ResponseOrderDTO;
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

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OrderControllerTest.MethodSecurityTestConfig.class)
class OrderControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtService jwtService;

    private static Principal principal(String email, String role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private ResponseOrderDTO sampleResponse(Long id, String status) {
        return new ResponseOrderDTO(
                id, 250f, status, LocalDateTime.now(), null, "Item de prueba", null, null, null);
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "USER")
    void shouldReturn201WhenBuyerCreatesOrder() throws Exception {
        when(orderService.create(any(RequestOrderDTO.class), eq("ada@yala.pe")))
                .thenReturn(sampleResponse(99L, "PENDING"));

        mockMvc.perform(post("/api/v1/orders")
                        .principal(principal("ada@yala.pe", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestOrderDTO(10L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99L))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "USER")
    void shouldReturn409WhenListingIsNotFixed() throws Exception {
        when(orderService.create(any(RequestOrderDTO.class), eq("ada@yala.pe")))
                .thenThrow(new OrderNotConfirmableException("Direct purchase is only allowed on FIXED listings"));

        mockMvc.perform(post("/api/v1/orders")
                        .principal(principal("ada@yala.pe", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestOrderDTO(11L))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "USER")
    void shouldReturn200WithBuyerOrdersWhenFindMyOrdersInvoked() throws Exception {
        when(orderService.findByBuyer(eq("ada@yala.pe"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(sampleResponse(1L, "PENDING")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/orders/my-orders")
                        .principal(principal("ada@yala.pe", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "USER")
    void shouldReturn200WhenFindByIdInvokedByBuyer() throws Exception {
        when(orderService.findById(eq(50L), eq("ada@yala.pe")))
                .thenReturn(sampleResponse(50L, "PENDING"));

        mockMvc.perform(get("/api/v1/orders/50")
                        .principal(principal("ada@yala.pe", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50L));
    }

    @Test
    @WithMockUser(username = "intruder@yala.pe", roles = "USER")
    void shouldReturn401WhenFindByIdInvokedByStranger() throws Exception {
        when(orderService.findById(eq(50L), eq("intruder@yala.pe")))
                .thenThrow(new UnauthorizedException("Only the buyer or seller of this order can access it"));

        mockMvc.perform(get("/api/v1/orders/50")
                        .principal(principal("intruder@yala.pe", "USER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "bob@yala.pe", roles = "SELLER")
    void shouldReturn200WhenSellerConfirmsOrder() throws Exception {
        when(orderService.confirm(eq(50L), eq("bob@yala.pe")))
                .thenReturn(sampleResponse(50L, "CONFIRMED"));

        mockMvc.perform(put("/api/v1/orders/50/confirm")
                        .principal(principal("bob@yala.pe", "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "USER")
    void shouldReturn403WhenNonSellerTriesToConfirm() throws Exception {
        mockMvc.perform(put("/api/v1/orders/50/confirm")
                        .principal(principal("ada@yala.pe", "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "bob@yala.pe", roles = "SELLER")
    void shouldReturn409WhenConfirmingNonPendingOrder() throws Exception {
        when(orderService.confirm(eq(50L), eq("bob@yala.pe")))
                .thenThrow(new OrderNotConfirmableException(
                        "Order 50 is not pending and cannot be confirmed"));

        mockMvc.perform(put("/api/v1/orders/50/confirm")
                        .principal(principal("bob@yala.pe", "SELLER")))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "ada@yala.pe", roles = "USER")
    void shouldReturn200WhenBuyerCancelsPendingOrder() throws Exception {
        when(orderService.cancel(eq(60L), eq("ada@yala.pe")))
                .thenReturn(sampleResponse(60L, "CANCELLED"));

        mockMvc.perform(put("/api/v1/orders/60/cancel")
                        .principal(principal("ada@yala.pe", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(username = "intruder@yala.pe", roles = "USER")
    void shouldReturn401WhenStrangerTriesToCancel() throws Exception {
        when(orderService.cancel(eq(60L), eq("intruder@yala.pe")))
                .thenThrow(new UnauthorizedException("Only the buyer or seller can cancel this order"));

        mockMvc.perform(put("/api/v1/orders/60/cancel")
                        .principal(principal("intruder@yala.pe", "USER")))
                .andExpect(status().isUnauthorized());
    }
}
