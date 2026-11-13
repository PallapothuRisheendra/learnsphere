package com.learnsphere.auth;

import com.learnsphere.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_action_tokens")
public class AuthActionToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 32)
    private String purpose;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuthActionToken() {}
    public AuthActionToken(User user, String purpose, String tokenHash, Instant expiresAt) {
        this.user = user; this.purpose = purpose; this.tokenHash = tokenHash; this.expiresAt = expiresAt;
    }
    public User getUser() { return user; }
    public String getPurpose() { return purpose; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isUsable(Instant now) { return consumedAt == null && expiresAt.isAfter(now); }
    public void consume(Instant now) { this.consumedAt = now; }
}
