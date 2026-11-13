package com.learnsphere.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCaseAndActiveTrue(String email);
    Optional<User> findByIdAndActiveTrue(Long id);
    List<User> findAllByActiveTrueOrderByCreatedAtDesc();
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
