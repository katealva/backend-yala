package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.dto.review.RequestReviewDTO;
import com.yala.dto.review.ResponseReviewDTO;
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
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ResponseReviewDTO> create(
            @Valid @RequestBody RequestReviewDTO request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(request, auth.getName()));
    }

    @GetMapping("/user/{recipientId}")
    public ResponseEntity<Page<ResponseReviewDTO>> findByRecipient(
            @PathVariable Long recipientId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(reviewService.findByRecipient(recipientId, pageable));
    }
}
