package com.yala.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.auth.JwtService;
import com.yala.exception.ResourceNotFoundException;
import com.yala.user.dto.UpdateUserRequest;
import com.yala.user.dto.UserResponse;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    /** Satisfies the JwtAuthFilter bean pulled into the @WebMvcTest slice. */
    @MockitoBean
    private JwtService jwtService;

    private final Principal principal =
            new UsernamePasswordAuthenticationToken("ada@yala.pe", null);

    private UserResponse sampleResponse() {
        return new UserResponse(1L, "Ada Lovelace", "ada@yala.pe",
                "https://img.yala.pe/avatar.png", 4.5f, false, Role.USER);
    }

    @Test
    void shouldReturn200WithCurrentUserWhenAuthenticated() throws Exception {
        when(userService.getCurrentUser("ada@yala.pe")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/users/me").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@yala.pe"));
    }

    @Test
    void shouldReturn200WhenProfileIsUpdated() throws Exception {
        UpdateUserRequest request =
                new UpdateUserRequest("Ada L.", "https://img.yala.pe/new.png");
        UserResponse updated = new UserResponse(1L, "Ada L.", "ada@yala.pe",
                "https://img.yala.pe/new.png", 4.5f, false, Role.USER);
        when(userService.updateCurrentUser(eq("ada@yala.pe"), any(UpdateUserRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/users/me")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada L."));
    }

    @Test
    void shouldReturn200WithUserProfileWhenIdExists() throws Exception {
        when(userService.getById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturn404WhenUserDoesNotExist() throws Exception {
        when(userService.getById(99L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/api/v1/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn200WithUserListingsWhenUserExists() throws Exception {
        when(userService.getListingsByUser(eq(1L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/users/1/listings"))
                .andExpect(status().isOk());
    }
}
