package com.yala.config;

import com.yala.model.Auction;
import com.yala.dto.auction.ResponseAuctionDTO;
import com.yala.dto.auction.ResponseAuctionSummaryDTO;
import com.yala.model.Bid;
import com.yala.dto.bid.ResponseBidDTO;
import com.yala.model.Category;
import com.yala.dto.category.ResponseCategoryDTO;
import com.yala.model.Image;
import com.yala.dto.image.ResponseImageDTO;
import com.yala.model.Listing;
import com.yala.dto.listing.ResponseListingDTO;
import com.yala.model.Notification;
import com.yala.dto.notification.ResponseNotificationDTO;
import com.yala.model.Order;
import com.yala.dto.order.ResponseOrderDTO;
import com.yala.model.Review;
import com.yala.dto.review.ResponseReviewDTO;
import com.yala.model.User;
import com.yala.dto.user.ResponseUserDTO;
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

        mapper.addConverter(new AbstractConverter<User, ResponseUserDTO>() {
            @Override
            protected ResponseUserDTO convert(User source) {
                return toUserResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Category, ResponseCategoryDTO>() {
            @Override
            protected ResponseCategoryDTO convert(Category source) {
                return toCategoryResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Auction, ResponseAuctionSummaryDTO>() {
            @Override
            protected ResponseAuctionSummaryDTO convert(Auction source) {
                return toAuctionSummaryResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Auction, ResponseAuctionDTO>() {
            @Override
            protected ResponseAuctionDTO convert(Auction source) {
                return toAuctionResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Bid, ResponseBidDTO>() {
            @Override
            protected ResponseBidDTO convert(Bid source) {
                return toBidResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Listing, ResponseListingDTO>() {
            @Override
            protected ResponseListingDTO convert(Listing source) {
                return toListingResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Order, ResponseOrderDTO>() {
            @Override
            protected ResponseOrderDTO convert(Order source) {
                return toOrderResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Review, ResponseReviewDTO>() {
            @Override
            protected ResponseReviewDTO convert(Review source) {
                return toReviewResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Notification, ResponseNotificationDTO>() {
            @Override
            protected ResponseNotificationDTO convert(Notification source) {
                return toNotificationResponse(source);
            }
        });
        mapper.addConverter(new AbstractConverter<Image, ResponseImageDTO>() {
            @Override
            protected ResponseImageDTO convert(Image source) {
                return toImageResponse(source);
            }
        });

        return mapper;
    }

    private static ResponseUserDTO toUserResponse(User source) {
        if (source == null) return null;
        return new ResponseUserDTO(
                source.getId(),
                source.getName(),
                source.getEmail(),
                source.getAvatarUrl(),
                source.getReputation(),
                source.getIsVerifiedSeller(),
                source.getRole());
    }

    private static ResponseCategoryDTO toCategoryResponse(Category source) {
        if (source == null) return null;
        return new ResponseCategoryDTO(source.getId(), source.getName(), source.getDescription());
    }

    private static ResponseAuctionSummaryDTO toAuctionSummaryResponse(Auction source) {
        if (source == null) return null;
        return new ResponseAuctionSummaryDTO(
                source.getId(),
                source.getCurrentPrice(),
                source.getEndsAt(),
                source.getStatus() != null ? source.getStatus().name() : null);
    }

    private static ResponseAuctionDTO toAuctionResponse(Auction source) {
        if (source == null) return null;
        return new ResponseAuctionDTO(
                source.getId(),
                source.getStartingPrice(),
                source.getCurrentPrice(),
                source.getStartedAt(),
                source.getEndsAt(),
                source.getStatus() != null ? source.getStatus().name() : null,
                toUserResponse(source.getWinner()),
                source.getBids() != null ? source.getBids().size() : 0);
    }

    private static ResponseBidDTO toBidResponse(Bid source) {
        if (source == null) return null;
        return new ResponseBidDTO(
                source.getId(),
                source.getAmount(),
                source.getPlacedAt(),
                toUserResponse(source.getBidder()));
    }

    private static ResponseListingDTO toListingResponse(Listing source) {
        if (source == null) return null;
        List<String> imageUrls = source.getImages() != null
                ? source.getImages().stream().map(Image::getUrl).toList()
                : List.of();
        return new ResponseListingDTO(
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

    private static ResponseOrderDTO toOrderResponse(Order source) {
        if (source == null) return null;
        return new ResponseOrderDTO(
                source.getId(),
                source.getAmount(),
                source.getStatus() != null ? source.getStatus().name() : null,
                source.getCreatedAt(),
                source.getPaymentDeadline(),
                source.itemTitle(),
                toListingResponse(source.getListing()),
                toUserResponse(source.getBuyer()),
                toUserResponse(source.getSeller()));
    }

    private static ResponseReviewDTO toReviewResponse(Review source) {
        if (source == null) return null;
        return new ResponseReviewDTO(
                source.getId(),
                source.getRating(),
                source.getComment(),
                source.getCreatedAt(),
                toUserResponse(source.getAuthor()));
    }

    private static ResponseNotificationDTO toNotificationResponse(Notification source) {
        if (source == null) return null;
        return new ResponseNotificationDTO(
                source.getId(),
                source.getType() != null ? source.getType().name() : null,
                source.getMessage(),
                source.getIsRead(),
                source.getCreatedAt());
    }

    private static ResponseImageDTO toImageResponse(Image source) {
        if (source == null) return null;
        return new ResponseImageDTO(
                source.getId(),
                source.getUrl(),
                source.getSortOrder(),
                source.getListing() != null ? source.getListing().getId() : null);
    }
}
