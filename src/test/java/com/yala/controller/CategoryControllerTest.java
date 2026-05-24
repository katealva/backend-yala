package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.security.JwtService;
import com.yala.dto.category.ResponseCategoryDTO;
import com.yala.dto.category.RequestCategoryDTO;
import com.yala.exceptions.DuplicateResourceException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CategoryControllerTest.MethodSecurityTestConfig.class)
class CategoryControllerTest {

    /** Enables @PreAuthorize evaluation inside the WebMvcTest slice. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CategoryService categoryService;

    /** Satisfies the JwtAuthorizationFilter bean pulled into the @WebMvcTest slice. */
    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldReturn200WithCategoryListWhenInvoked() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of(
                new ResponseCategoryDTO(1L, "Pokémon TCG", "Cards"),
                new ResponseCategoryDTO(2L, "Funko Pop", "Figures")));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pokémon TCG"))
                .andExpect(jsonPath("$[1].name").value("Funko Pop"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn201WhenCategoryIsCreatedByAdmin() throws Exception {
        RequestCategoryDTO request = new RequestCategoryDTO("Funko Pop", "Figures");
        when(categoryService.create(any(RequestCategoryDTO.class)))
                .thenReturn(new ResponseCategoryDTO(2L, "Funko Pop", "Figures"));

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Funko Pop"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturn403WhenNonAdminCreatesCategory() throws Exception {
        RequestCategoryDTO request = new RequestCategoryDTO("Comics", "Marvel/DC");

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn409WhenCategoryAlreadyExists() throws Exception {
        RequestCategoryDTO request = new RequestCategoryDTO("Pokémon TCG", "Cards");
        when(categoryService.create(any(RequestCategoryDTO.class)))
                .thenThrow(new DuplicateResourceException(
                        "Category already exists with name: Pokémon TCG"));

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
