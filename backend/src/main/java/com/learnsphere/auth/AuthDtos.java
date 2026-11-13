package com.learnsphere.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 10, max = 72) String password) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record UserView(Long id, String fullName, String email, String role, boolean emailVerified) {}
    public record AuthResponse(UserView user, boolean verificationRequired, String message) {}
    public record CsrfView(String token) {}
    public record EmailActionRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record TokenRequest(@NotBlank String token) {}
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 10, max = 72) String password) {}
}
