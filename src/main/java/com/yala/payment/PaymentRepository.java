package com.yala.payment;
import com.yala.model.*;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderId(Long orderId);

    /** Resolves a payment from the MercadoPago preference id when handling webhooks. */
    Optional<Payment> findByExternalReference(String externalReference);
}
