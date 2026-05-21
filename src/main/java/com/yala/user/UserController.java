package com.yala.user;

import com.yala.listing.dto.ListingResponse;
import com.yala.user.dto.UpdateUserRequest;
import com.yala.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Perfil de usuario y publicaciones")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Devuelve el perfil del usuario autenticado")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(authentication.getName()));
    }

    @PutMapping("/me")
    @Operation(summary = "Actualiza el nombre y avatar del usuario autenticado")
    public ResponseEntity<UserResponse> updateCurrentUser(
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(
                userService.updateCurrentUser(authentication.getName(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Devuelve el perfil público de un usuario")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping("/{id}/listings")
    @Operation(summary = "Lista paginada de las publicaciones de un usuario")
    public ResponseEntity<Page<ListingResponse>> getListingsByUser(
            @PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(userService.getListingsByUser(id, pageable));
    }
}
