package com.yala.config;

import com.yala.model.Auction;
import com.yala.auction.dto.AuctionResponse;
import com.yala.auction.dto.AuctionSummaryResponse;
import com.yala.model.Bid;
import com.yala.bid.dto.BidResponse;
import com.yala.model.Category;
import com.yala.category.dto.CategoryResponse;
import com.yala.model.Image;
import com.yala.image.dto.ImageResponse;
import com.yala.model.Listing;
import com.yala.listing.dto.ListingResponse;
import com.yala.model.Notification;
import com.yala.notification.dto.NotificationResponse;
import com.yala.model.Order;
import com.yala.order.dto.OrderResponse;
import com.yala.model.Review;
import com.yala.review.dto.ReviewResponse;
import com.yala.model.User;
import com.yala.user.dto.UserResponse;
import java.util.List;
import org.modelmapper.AbstractConverter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralizes Entity-to-Response mapping in a single {@link ModelMapper} bean. Records are
 * immutable, so each mapping is declared as an explicit converter; this also acts as a hard
 * guarantee that sensitive fields (e.g. {@code User.passwordHash}) are never copied into a
 * Response by accident.
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        mapper.addConverter(new AbstractConverter<User, UserResponse>() {
            @Override
            protected UserResponse convert(User source) {
                return toUserResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Category, CategoryResponse>() {
            @Override
            protected CategoryResponse convert(Category source) {
                return toCategoryResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Auction, AuctionSummaryResponse>() {
            @Override
            protected AuctionSummaryResponse convert(Auction source) {
                return toAuctionSummaryResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Auction, AuctionResponse>() {
            @Override
            protected AuctionResponse convert(Auction source) {
                return toAuctionResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Bid, BidResponse>() {
            @Override
            protected BidResponse convert(Bid source) {
                return toBidResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Listing, ListingResponse>() {
            @Override
            protected ListingResponse convert(Listing source) {
                return toListingResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Order, OrderResponse>() {
            @Override
            protected OrderResponse convert(Order source) {
                return toOrderResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Review, ReviewResponse>() {
            @Override
            protected ReviewResponse convert(Review source) {
                return toReviewResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Notification, NotificationResponse>() {
            @Override
            protected NotificationResponse convert(Notification source) {
                return toNotificationResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Image, ImageResponse>() {
            @Override
            protected ImageResponse convert(Image source) {
                return toImageResponse(source);
            }
        });

        return mapper;
    }

    private static UserResponse toUserResponse(User source) {
        if (source == null) return null;
        return new UserResponse(
                source.getId(),
                source.getName(),
                source.getEmail(),
                source.getAvatarUrl(),
                source.getReputation(),
                source.getIsVerifiedSeller(),
                source.getRole());
    }

    private static CategoryResponse toCategoryResponse(Category source) {
        if (source == null) return null;
        return new CategoryResponse(source.getId(), source.getName(), source.getDescription());
    }

    private static AuctionSummaryResponse toAuctionSummaryResponse(Auction source) {
        if (source == null) return null;
        return new AuctionSummaryResponse(
                source.getId(),
                source.getCurrentPrice(),
                source.getEndsAt(),
                source.getStatus() != null ? source.getStatus().name() : null);
    }

    private static AuctionResponse toAuctionResponse(Auction source) {
        if (source == null) return null;
        return new AuctionResponse(
                source.getId(),
                source.getStartingPrice(),
                source.getCurrentPrice(),
                source.getStartedAt(),
                source.getEndsAt(),
                source.getStatus() != null ? source.getStatus().name() : null,
                toUserResponse(source.getWinner()),
                source.getBids() != null ? source.getBids().size() : 0);
    }

    private static BidResponse toBidResponse(Bid source) {
        if (source == null) return null;
        return new BidResponse(
                source.getId(),
                source.getAmount(),
                source.getPlacedAt(),
                toUserResponse(source.getBidder()));
    }

    private static ListingResponse toListingResponse(Listing source) {
        if (source == null) return null;
        List<String> imageUrls = source.getImages() != null
                ? source.getImages().stream().map(Image::getUrl).toList()
                : List.of();
        return new ListingResponse(
                source.getId(),
                source.getTitle(),
                source.getDescription(),
                source.getMode() != null ? source.getMode().name() : null,
                source.getFixedPrice(),
                source.getCondition(),
                source.getStatus() != null ? source.getStatus().name() : null,
                source.getCreatedAt(),
                toUserResponse(source.getSeller()),
                toCategoryResponse(source.getCategory()),
                imageUrls,
                toAuctionSummaryResponse(source.getAuction()));
    }

    private static OrderResponse toOrderResponse(Order source) {
        if (source == null) return null;
        return new OrderResponse(
                source.getId(),
                source.getAmount(),
                source.getStatus() != null ? source.getStatus().name() : null,
                source.getCreatedAt(),
                toListingResponse(source.getListing()),
                toUserResponse(source.getBuyer()),
                toUserResponse(source.getSeller()));
    }

    private static ReviewResponse toReviewResponse(Review source) {
        if (source == null) return null;
        return new ReviewResponse(
                source.getId(),
                source.getRating(),
                source.getComment(),
                source.getCreatedAt(),
                toUserResponse(source.getAuthor()));
    }

    private static NotificationResponse toNotificationResponse(Notification source) {
        if (source == null) return null;
        return new NotificationResponse(
                source.getId(),
                source.getType() != null ? source.getType().name() : null,
                source.getMessage(),
                source.getIsRead(),
                source.getCreatedAt());
    }

    private static ImageResponse toImageResponse(Image source) {
        if (source == null) return null;
        return new ImageResponse(
                source.getId(),
                source.getUrl(),
                source.getSortOrder(),
                source.getListing() != null ? source.getListing().getId() : null);
    }
}
