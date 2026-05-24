package com.yala.image;
import com.yala.model.*;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {

    List<Image> findByListingIdOrderBySortOrderAsc(Long listingId);

    /** Used to enforce the max-5-images-per-listing business rule. */
    long countByListingId(Long listingId);
}
