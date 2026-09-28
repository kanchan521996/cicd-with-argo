package com.paylane.request;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoneyRequestRepository extends JpaRepository<MoneyRequest, Long> {

    Page<MoneyRequest> findByPayerIdOrderByCreatedAtDesc(Long payerId, Pageable pageable);

    Page<MoneyRequest> findByPayerIdAndStatusOrderByCreatedAtDesc(Long payerId, MoneyRequestStatus status,
                                                                  Pageable pageable);

    Page<MoneyRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId, Pageable pageable);

    Page<MoneyRequest> findByRequesterIdAndStatusOrderByCreatedAtDesc(Long requesterId, MoneyRequestStatus status,
                                                                      Pageable pageable);

    long countByPayerIdAndStatus(Long payerId, MoneyRequestStatus status);

    /** Locks the request row so it can't be paid twice by concurrent clicks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MoneyRequest r where r.id = :id")
    Optional<MoneyRequest> findForUpdate(@Param("id") Long id);
}
