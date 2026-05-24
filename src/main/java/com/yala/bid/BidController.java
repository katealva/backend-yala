package com.yala.bid;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.bid.dto.BidResponse;
import com.yala.bid.dto.CreateBidRequest;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bids")
@RequiredArgsConstructor
@Tag(name = "Bids", description = "Pujas sobre subastas activas")
public class BidController {

    private final BidService bidService;

    @PostMapping
    @Operation(summary = "Realiza una puja en una subasta activa")
    public ResponseEntity<BidResponse> placeBid(
            @Valid @RequestBody CreateBidRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bidService.placeBid(request, auth.getName()));
    }

    @GetMapping("/auction/{auctionId}")
    @Operation(summary = "Historial paginado de pujas de una subasta")
    public ResponseEntity<Page<BidResponse>> findByAuction(
            @PathVariable Long auctionId,
            @PageableDefault(size = 20, sort = "amount", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(bidService.findByAuction(auctionId, pageable));
    }

    @GetMapping("/auction/{auctionId}/highest")
    @Operation(summary = "Puja máxima vigente de una subasta")
    public ResponseEntity<BidResponse> findHighest(@PathVariable Long auctionId) {
        return ResponseEntity.ok(bidService.findHighest(auctionId));
    }
}
