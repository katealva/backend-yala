package com.yala.listing;
import com.yala.model.*;

import com.yala.listing.dto.CreateListingRequest;
import com.yala.listing.dto.ListingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
@Tag(name = "Listings", description = "Gestión del catálogo de coleccionables")
public class ListingController {

    private final ListingService listingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Crea un listing (solo SELLER verificado o ADMIN)")
    public ResponseEntity<ListingResponse> create(
            @Valid @RequestBody CreateListingRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(listingService.create(request, auth.getName()));
    }

    @GetMapping
    @Operation(summary = "Lista listings activos paginados con filtros opcionales")
    public ResponseEntity<Page<ListingResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) Float minPrice,
            @RequestParam(required = false) Float maxPrice,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(listingService.findAll(
                pageable, category, mode, condition, minPrice, maxPrice, q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un listing por id")
    public ResponseEntity<ListingResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(listingService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza un listing existente (solo el dueño)")
    public ResponseEntity<ListingResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateListingRequest request,
            Authentication auth) {
        return ResponseEntity.ok(listingService.update(id, request, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancela (soft-delete) un listing (solo el dueño)")
    public ResponseEntity<Void> cancel(@PathVariable Long id, Authentication auth) {
        listingService.cancel(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
