package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.dto.auction.ResponseAuctionDTO;
import com.yala.dto.auction.ResponseAuctionSummaryDTO;
import com.yala.dto.auction.RequestAuctionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
@Tag(name = "Auctions", description = "Gestión de subastas: crear, listar activas y consultar detalle con historial de pujas")
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping
    @Operation(summary = "Lista subastas activas paginadas",
            description = "Devuelve un resumen de las subastas con status ACTIVE ordenadas por fecha de cierre. Endpoint público.")
    public ResponseEntity<Page<ResponseAuctionSummaryDTO>> findAllActive(
            @PageableDefault(size = 20, sort = "endsAt") Pageable pageable) {
        return ResponseEntity.ok(auctionService.findAllActive(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una subasta",
            description = "Devuelve el detalle completo de una subasta, incluyendo la puja más alta y el ganador si ya finalizó. Endpoint público.")
    public ResponseEntity<ResponseAuctionDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(auctionService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Crea una nueva subasta",
            description = "Crea una subasta sobre un listing existente. Requiere autenticación y que el usuario sea SELLER o ADMIN dueño del listing.")
    public ResponseEntity<ResponseAuctionDTO> create(
            @Valid @RequestBody RequestAuctionDTO request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.create(request, auth.getName()));
    }

}
