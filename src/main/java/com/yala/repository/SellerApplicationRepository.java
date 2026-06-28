package com.yala.repository;
import com.yala.model.*;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    Optional<SellerApplication> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SellerApplication> findFirstByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, SellerApplicationStatus status);
}
