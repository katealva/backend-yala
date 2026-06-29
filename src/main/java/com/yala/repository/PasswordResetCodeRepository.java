package com.yala.repository;

import com.yala.model.PasswordResetCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findFirstByUserIdAndCodeAndUsedFalseOrderByCreatedAtDesc(
            Long userId, String code);
}
