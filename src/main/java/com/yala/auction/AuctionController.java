package com.yala.auction;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.auction.dto.AuctionResponse;
import com.yala.auction.dto.AuctionSummaryResponse;
import com.yala.auction.dto.CreateAuctionRequest;
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
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping
    public ResponseEntity<Page<AuctionSummaryResponse>> findAllActive(
            @PageableDefault(size = 20, sort = "endsAt") Pageable pageable) {
        return ResponseEntity.ok(auctionService.findAllActive(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(auctionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AuctionResponse> create(
            @Valid @RequestBody CreateAuctionRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.create(request, auth.getName()));
    }

}
