package com.yala.listing;

import com.yala.listing.dto.CreateListingRequest;
import com.yala.listing.dto.ListingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListingService {

    ListingResponse create(CreateListingRequest request, String sellerEmail);

    ListingResponse findById(Long id);

    Page<ListingResponse> findAll(
            Pageable pageable,
            String category,
            String mode,
            String condition,
            Float minPrice,
            Float maxPrice,
            String q);

    ListingResponse update(Long id, CreateListingRequest request, String requesterEmail);

    void cancel(Long id, String requesterEmail);
}
