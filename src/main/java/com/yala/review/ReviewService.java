package com.yala.review;
import com.yala.model.*;

import com.yala.review.dto.CreateReviewRequest;
import com.yala.review.dto.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    ReviewResponse create(CreateReviewRequest request, String authorEmail);

    Page<ReviewResponse> findByRecipient(Long recipientId, Pageable pageable);
}
