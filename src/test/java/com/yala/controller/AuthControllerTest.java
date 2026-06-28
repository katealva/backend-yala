package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.dto.auth.ResponseAuthDTO;
import com.yala.dto.auth.RequestLoginDTO;
import com.yala.dto.auth.RequestRefreshTokenDTO;
import com.yala.dto.auth.RequestRegisterDTO;
import com.yala.exceptions.EmailAlreadyExistsException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.security.JwtService;
import com.yala.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    /** Satisfies the JwtAuthorizationFilter bean pulled into the @WebMvcTest slice. */
    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldReturn201WhenRegisterIsSuccessful() throws Exception {
        RequestRegisterDTO request = new RequestRegisterDTO(
                "12345678", "ada@yala.pe", "password123", "Ada", "Lovelace", "Byron");
        ResponseAuthDTO response = new ResponseAuthDTO(
                "access-token", "refresh-token", 1L, "ada@yala.pe", "Ada Lovelace", Role.USER);
        when(authService.register(any(RequestRegisterDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        RequestRegisterDTO request = new RequestRegisterDTO(
                "12345678", "ada@yala.pe", "password123", "Ada", "Lovelace", "Byron");
        when(authService.register(any(RequestRegisterDTO.class)))
                .thenThrow(new EmailAlreadyExistsException("Email already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn200WithTokenWhenLoginIsSuccessful() throws Exception {
        RequestLoginDTO request = new RequestLoginDTO("ada@yala.pe", "password123");
        ResponseAuthDTO response = new ResponseAuthDTO(
                "access-token", "refresh-token", 1L, "ada@yala.pe", "Ada Lovelace", Role.USER);
        when(authService.login(any(RequestLoginDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void shouldReturn401WhenCredentialsAreInvalid() throws Exception {
        RequestLoginDTO request = new RequestLoginDTO("ada@yala.pe", "wrong-password");
        when(authService.login(any(RequestLoginDTO.class)))
                .thenThrow(new UnauthorizedException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn200WhenRefreshTokenIsValid() throws Exception {
        RequestRefreshTokenDTO request = new RequestRefreshTokenDTO("valid-refresh-token");
        ResponseAuthDTO response = new ResponseAuthDTO(
                "new-access-token", "valid-refresh-token", 1L, "ada@yala.pe",
                "Ada Lovelace", Role.USER);
        when(authService.refreshToken(any(RequestRefreshTokenDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void shouldReturn400WhenRegisterRequestIsInvalid() throws Exception {
        RequestRegisterDTO request = new RequestRegisterDTO(
                "123", "not-an-email", "short", "", "", "");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
