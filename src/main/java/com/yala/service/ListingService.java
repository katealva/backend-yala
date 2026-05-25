package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.model.Category;
import com.yala.repository.CategoryRepository;
import com.yala.exceptions.InvalidBidException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.dto.listing.RequestListingDTO;
import com.yala.dto.listing.ResponseListingDTO;
import com.yala.model.Tag;
import com.yala.repository.TagRepository;
import com.yala.model.Role;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public ResponseListingDTO create(RequestListingDTO request, String sellerEmail) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ensureCanSell(seller);

        ListingMode mode = parseMode(request.mode());
        validatePriceByMode(mode, request.fixedPrice());

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.categoryId()));

        Listing saved = listingRepository.save(Listing.builder()
                .title(request.title())
                .description(request.description())
                .mode(mode)
                .fixedPrice(request.fixedPrice())
                .condition(request.condition())
                .status(ListingStatus.ACTIVE)
                .seller(seller)
                .category(category)
                .tags(resolveTags(request.tags()))
                .build());

        log.info("Listing {} created by seller {}", saved.getId(), seller.getEmail());
        return modelMapper.map(saved, ResponseListingDTO.class);
    }

    @Transactional(readOnly = true)
    public ResponseListingDTO findById(Long id) {
        return modelMapper.map(findOrThrow(id), ResponseListingDTO.class);
    }

    @Transactional(readOnly = true)
    public Page<ResponseListingDTO> findAll(
            Pageable pageable,
            String category,
            String mode,
            String condition,
            Float minPrice,
            Float maxPrice,
            String q) {

        List<Specification<Listing>> specs = new ArrayList<>();
        specs.add(isActive());
        if (isPresent(category)) specs.add(hasCategory(category));
        if (isPresent(mode)) specs.add(hasMode(mode));
        if (isPresent(condition)) specs.add(hasCondition(condition));
        if (minPrice != null) specs.add(priceGreaterThanOrEqualTo(minPrice));
        if (maxPrice != null) specs.add(priceLessThanOrEqualTo(maxPrice));
        if (isPresent(q)) specs.add(titleContains(q));

        Specification<Listing> combined = specs.stream()
                .reduce(Specification::and)
                .orElse(null);

        return listingRepository.findAll(combined, pageable).map(l -> modelMapper.map(l, ResponseListingDTO.class));
    }

    @Transactional
    public ResponseListingDTO update(Long id, RequestListingDTO request, String requesterEmail) {
        Listing listing = findOrThrow(id);
        ensureOwner(listing, requesterEmail);

        ListingMode mode = parseMode(request.mode());
        validatePriceByMode(mode, request.fixedPrice());

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.categoryId()));

        listing.setTitle(request.title());
        listing.setDescription(request.description());
        listing.setMode(mode);
        listing.setFixedPrice(request.fixedPrice());
        listing.setCondition(request.condition());
        listing.setCategory(category);
        listing.setTags(resolveTags(request.tags()));

        return modelMapper.map(listingRepository.save(listing), ResponseListingDTO.class);
    }

    @Transactional
    public void cancel(Long id, String requesterEmail) {
        Listing listing = findOrThrow(id);
        ensureOwner(listing, requesterEmail);
        listing.setStatus(ListingStatus.CANCELLED);
        listingRepository.save(listing);
        log.info("Listing {} cancelled by {}", listing.getId(), requesterEmail);
    }

    private Listing findOrThrow(Long id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing not found with id: " + id));
    }

    private void ensureOwner(Listing listing, String requesterEmail) {
        if (!listing.getSeller().getEmail().equals(requesterEmail)) {
            throw new UnauthorizedException("Only the listing owner can perform this operation");
        }
    }

    private void ensureCanSell(User user) {
        if (user.getRole() == Role.ADMIN) return;
        boolean verifiedSeller = user.getRole() == Role.SELLER
                && Boolean.TRUE.equals(user.getIsVerifiedSeller());
        if (!verifiedSeller) {
            throw new UnauthorizedException(
                    "Only verified sellers or admins can create or update listings");
        }
    }

    private ListingMode parseMode(String mode) {
        try {
            return ListingMode.valueOf(mode);
        } catch (IllegalArgumentException ex) {
            throw new InvalidBidException("Invalid listing mode: " + mode);
        }
    }

    private void validatePriceByMode(ListingMode mode, Float fixedPrice) {
        if (mode == ListingMode.FIXED && (fixedPrice == null || fixedPrice <= 0)) {
            throw new InvalidBidException("FIXED listings require a positive fixedPrice");
        }
        if (mode == ListingMode.AUCTION && fixedPrice != null) {
            throw new InvalidBidException("AUCTION listings must not declare a fixedPrice");
        }
    }

    private List<Tag> resolveTags(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return new ArrayList<>();
        }
        List<Tag> tags = new ArrayList<>();
        for (String rawName : tagNames) {
            if (rawName == null) continue;
            String name = rawName.trim();
            if (name.isEmpty()) continue;
            Tag tag = tagRepository.findByName(name)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build()));
            tags.add(tag);
        }
        return tags;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static Specification<Listing> isActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), ListingStatus.ACTIVE);
    }

    private static Specification<Listing> hasCategory(String category) {
        return (root, query, cb) ->
                cb.equal(cb.lower(root.get("category").get("name")), category.toLowerCase());
    }

    private static Specification<Listing> hasMode(String mode) {
        ListingMode parsed;
        try {
            parsed = ListingMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("mode"), parsed);
    }

    private static Specification<Listing> hasCondition(String condition) {
        return (root, query, cb) ->
                cb.equal(cb.lower(root.get("condition")), condition.toLowerCase());
    }

    private static Specification<Listing> priceGreaterThanOrEqualTo(Float min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fixedPrice"), min);
    }

    private static Specification<Listing> priceLessThanOrEqualTo(Float max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("fixedPrice"), max);
    }

    private static Specification<Listing> titleContains(String q) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + q.toLowerCase() + "%");
    }
}
