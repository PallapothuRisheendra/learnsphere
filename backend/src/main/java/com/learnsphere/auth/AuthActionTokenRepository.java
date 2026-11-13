package com.learnsphere.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthActionTokenRepository extends JpaRepository<AuthActionToken, Long> {
    Optional<AuthActionToken> findByTokenHashAndPurpose(String tokenHash, String purpose);
    void deleteAllByUser_IdAndPurpose(Long userId, String purpose);
}
