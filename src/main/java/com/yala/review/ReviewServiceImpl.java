package com.yala.review;

import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.ReviewNotAllowedException;
import com.yala.order.Order;
import com.yala.order.OrderRepository;
import com.yala.order.OrderStatus;
import com.yala.review.dto.CreateReviewRequest;
import com.yala.review.dto.ReviewResponse;
import com.yala.user.User;
import com.yala.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public ReviewResponse create(CreateReviewRequest request, String authorEmail) {
        User author = userRepository.findByEmail(authorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + request.orderId()));

        if (!order.getBuyer().getId().equals(author.getId())) {
            throw new ReviewNotAllowedException("Only the buyer of this order can leave a review");
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ReviewNotAllowedException("Reviews are only allowed on confirmed orders");
        }

        boolean alreadyReviewed = order.getReviews().stream()
                .anyMatch(r -> r.getAuthor().getId().equals(author.getId()));
        if (alreadyReviewed) {
            throw new ReviewNotAllowedException("You have already reviewed this order");
        }

        Review saved = reviewRepository.save(Review.builder()
                .rating(request.rating())
                .comment(request.comment())
                .order(order)
                .author(author)
                .recipient(order.getSeller())
                .build());

        Double avg = reviewRepository.averageRatingByRecipientId(order.getSeller().getId());
        if (avg != null) {
            User seller = order.getSeller();
            seller.setReputation(avg.floatValue());
            userRepository.save(seller);
        }

        return modelMapper.map(saved, ReviewResponse.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> findByRecipient(Long recipientId, Pageable pageable) {
        return reviewRepository.findByRecipientId(recipientId, pageable)
                .map(r -> modelMapper.map(r, ReviewResponse.class));
    }
}
