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
import com.yala.security.JwtService;
import com.yala.exceptions.ReviewNotAllowedException;
import com.yala.dto.review.RequestReviewDTO;
import com.yala.dto.review.ResponseReviewDTO;
import com.yala.model.Role;
import com.yala.dto.user.ResponseUserDTO;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ReviewService reviewService;

    /** Satisfies the JwtAuthorizationFilter bean pulled into the @WebMvcTest slice. */
    @MockitoBean
    private JwtService jwtService;

    private final Principal principal =
            new UsernamePasswordAuthenticationToken("ada@yala.pe", null);

    private ResponseUserDTO sampleAuthor() {
        return new ResponseUserDTO(1L, "Ada Lovelace", "ada@yala.pe",
                null, 4.5f, false, false, Role.USER);
    }

    private ResponseReviewDTO sampleReview() {
        return new ResponseReviewDTO(10L, 5, "Excelente vendedor",
                LocalDateTime.of(2026, 5, 24, 12, 0), sampleAuthor());
    }

    @Test
    void shouldReturn201WhenReviewIsCreatedOnConfirmedOrder() throws Exception {
        RequestReviewDTO request = new RequestReviewDTO(50L, 5, "Excelente vendedor");
        when(reviewService.create(any(RequestReviewDTO.class), eq("ada@yala.pe")))
                .thenReturn(sampleReview());

        mockMvc.perform(post("/api/v1/reviews")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.author.email").value("ada@yala.pe"));
    }

    @Test
    void shouldReturn403WhenOrderIsNotConfirmed() throws Exception {
        RequestReviewDTO request = new RequestReviewDTO(50L, 5, "Excelente");
        when(reviewService.create(any(RequestReviewDTO.class), eq("ada@yala.pe")))
                .thenThrow(new ReviewNotAllowedException(
                        "Order must be CONFIRMED before reviewing"));

        mockMvc.perform(post("/api/v1/reviews")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn200WithUserReviews() throws Exception {
        Page<ResponseReviewDTO> page = new PageImpl<>(List.of(sampleReview()));
        when(reviewService.findByRecipient(eq(2L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/reviews/user/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturn400WhenCreateRequestIsInvalid() throws Exception {
        RequestReviewDTO invalid = new RequestReviewDTO(50L, 0, "");

        mockMvc.perform(post("/api/v1/reviews")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }
}
