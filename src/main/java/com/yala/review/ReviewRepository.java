package com.yala.review;
import com.yala.model.*;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByRecipientId(Long recipientId, Pageable pageable);

    List<Review> findByRecipientId(Long recipientId);

    /** Average rating received by a user — used to recalculate seller reputation. */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.recipient.id = :recipientId")
    Double averageRatingByRecipientId(@Param("recipientId") Long recipientId);
}
