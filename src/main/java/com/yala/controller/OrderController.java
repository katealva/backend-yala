package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.dto.order.RequestOrderDTO;
import com.yala.dto.order.ResponseOrderDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Compra directa, confirmación y cancelación")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Crea una orden de compra directa sobre un listing FIXED activo")
    public ResponseEntity<ResponseOrderDTO> create(
            @Valid @RequestBody RequestOrderDTO request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.create(request, auth.getName()));
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Lista paginada de órdenes del comprador autenticado")
    public ResponseEntity<Page<ResponseOrderDTO>> findMyOrders(
            Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(orderService.findByBuyer(auth.getName(), pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una orden por id (solo buyer o seller involucrados)")
    public ResponseEntity<ResponseOrderDTO> findById(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(orderService.findById(id, auth.getName()));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Confirma la orden (solo el seller)")
    public ResponseEntity<ResponseOrderDTO> confirm(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(orderService.confirm(id, auth.getName()));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancela la orden (buyer o seller, solo si PENDING)")
    public ResponseEntity<ResponseOrderDTO> cancel(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(orderService.cancel(id, auth.getName()));
    }
}
