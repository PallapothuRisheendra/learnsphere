package com.learnsphere.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String fullName;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false, length = 20)
    private String role;
    @Column(nullable = false)
    private boolean emailVerified;
    @Column(nullable = false)
    private int tokenVersion;
    @Column(nullable = false)
    private boolean active = true;

    protected User() {}
    public User(String fullName, String email, String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
        this.role = "LEARNER";
        this.emailVerified = false;
    }
    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public String getRole() { return role; }
    public boolean isEmailVerified() { return emailVerified; }
    public int getTokenVersion() { return tokenVersion; }
    public boolean isActive() { return active; }
    public void verifyEmail() { this.emailVerified = true; }
    public void setRole(String role) { this.role = role; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void invalidateTokens() { this.tokenVersion++; }
    public void deactivate() { this.active = false; this.tokenVersion++; }
    public void activate() { this.active = true; }
    public void updateProfile(String fullName, String email, String role) {
        this.fullName = fullName; this.email = email; this.role = role; this.tokenVersion++;
    }
}
