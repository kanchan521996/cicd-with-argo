package com.paylane.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    long countByStatus(UserStatus status);

    @Query("select u from User u where lower(u.fullName) like lower(concat('%', :q, '%')) "
            + "or lower(u.email) like lower(concat('%', :q, '%')) or u.phone like concat('%', :q, '%')")
    Page<User> search(@Param("q") String q, Pageable pageable);
}
