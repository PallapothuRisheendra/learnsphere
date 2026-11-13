package com.learnsphere.auth;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import com.learnsphere.config.SecurityConfig;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final long tokenHours;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(AuthService authService, @Value("${app.security.token-hours}") long tokenHours,
                          @Value("${app.auth.cookie-secure:true}") boolean cookieSecure,
                          @Value("${app.auth.cookie-same-site:Strict}") String cookieSameSite) {
        this.authService = authService; this.tokenHours = tokenHours;
        this.cookieSecure = cookieSecure; this.cookieSameSite = cookieSameSite;
    }

    @GetMapping("/csrf")
    public AuthDtos.CsrfView csrf(CsrfToken csrfToken) { return new AuthDtos.CsrfView(csrfToken.getToken()); }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authResponse(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authResponse(authService.login(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthDtos.AuthResponse> verifyEmail(@Valid @RequestBody AuthDtos.TokenRequest request) {
        return authResponse(authService.verifyEmail(request.token()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(@Valid @RequestBody AuthDtos.EmailActionRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().body(java.util.Map.of("message", "If an account matches that address, a reset link will be sent."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<java.util.Map<String, String>> resendVerification(@Valid @RequestBody AuthDtos.EmailActionRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.accepted().body(java.util.Map.of("message", "If verification is needed, a new link will be sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.ok(java.util.Map.of("message", "Your password has been reset. Sign in with your new password."));
    }

    @PostMapping("/logout")
    public ResponseEntity<java.util.Map<String, String>> logout(Authentication authentication) {
        authService.logout(authentication.getName());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authCookie("", 0).toString())
            .body(java.util.Map.of("message", "You are signed out."));
    }

    @GetMapping("/me")
    public AuthDtos.UserView me(Authentication authentication) { return authService.currentUser(authentication.getName()); }

    private ResponseEntity<AuthDtos.AuthResponse> authResponse(AuthService.AuthResult result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.token() != null)
            response.header(HttpHeaders.SET_COOKIE, authCookie(result.token(), tokenHours * 3600).toString());
        return response.body(result.response());
    }

    private ResponseCookie authCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(SecurityConfig.AUTH_COOKIE, value).httpOnly(true).secure(cookieSecure)
            .sameSite(cookieSameSite).path("/").maxAge(maxAgeSeconds).build();
    }
}
