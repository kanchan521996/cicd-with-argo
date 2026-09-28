package com.paylane.paymentmethod;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByUserIdAndRemovedFalseOrderByCreatedAtDesc(Long userId);

    Optional<PaymentMethod> findByIdAndUserIdAndRemovedFalse(Long id, Long userId);

    long countByUserIdAndRemovedFalse(Long userId);
}
