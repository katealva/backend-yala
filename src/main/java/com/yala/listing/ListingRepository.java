package com.yala.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Extends {@link JpaSpecificationExecutor} so the service layer can build dynamic,
 * combinable filters (category, mode, condition, price range, keyword) for the
 * paginated GET /api/v1/listings endpoint.
 */
@Repository
public interface ListingRepository
        extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    Page<Listing> findBySellerId(Long sellerId, Pageable pageable);

    Page<Listing> findByCategoryName(String categoryName, Pageable pageable);

    Page<Listing> findByMode(ListingMode mode, Pageable pageable);

    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    Page<Listing> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
}
