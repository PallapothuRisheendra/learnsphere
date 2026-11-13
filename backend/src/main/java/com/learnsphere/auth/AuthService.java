package com.learnsphere.auth;

import com.learnsphere.user.User;
import com.learnsphere.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public record AuthResult(String token, AuthDtos.AuthResponse response) {}
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AuthActionTokenRepository actionTokens;
    private final MailService mail;
    private final long tokenHours;
    private final boolean requireVerification;
    private final String frontendUrl;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       AuthActionTokenRepository actionTokens, MailService mail,
                       @Value("${app.security.token-hours}") long tokenHours,
                       @Value("${app.auth.require-email-verification:false}") boolean requireVerification,
                       @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.jwtEncoder = jwtEncoder;
        this.actionTokens = actionTokens; this.mail = mail; this.tokenHours = tokenHours;
        this.requireVerification = requireVerification; this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional
    public AuthResult register(AuthDtos.RegisterRequest request) {
        requireBcryptSafePassword(request.password(), HttpStatus.BAD_REQUEST);
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        User user = users.save(new User(request.fullName().trim(), email, passwordEncoder.encode(request.password())));
        if (requireVerification) {
            sendToken(user, "VERIFY_EMAIL", 24, "Verify your LearnSphere email", "/?verify=");
            return new AuthResult(null, new AuthDtos.AuthResponse(userView(user), true,
                "Check your email for a verification link before signing in."));
        }
        user.verifyEmail();
        return issueToken(user, false, "Your account is ready.");
    }

    @Transactional(readOnly = true)
    public AuthResult login(AuthDtos.LoginRequest request) {
        requireBcryptSafePassword(request.password(), HttpStatus.UNAUTHORIZED);
        User user = users.findByEmailIgnoreCaseAndActiveTrue(normalizeEmail(request.email()))
            .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."));
        if (requireVerification && !user.isEmailVerified())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email before signing in.");
        return issueToken(user, false, "You’re signed in.");
    }

    @Transactional
    public AuthResult verifyEmail(String rawToken) {
        AuthActionToken token = usableToken(rawToken, "VERIFY_EMAIL");
        token.consume(Instant.now());
        token.getUser().verifyEmail();
        return issueToken(token.getUser(), false, "Your email is verified.");
    }

    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmailIgnoreCaseAndActiveTrue(normalizeEmail(email)).ifPresent(user -> {
            try { sendToken(user, "RESET_PASSWORD", 1, "Reset your LearnSphere password", "/?reset="); }
            catch (RuntimeException ex) { log.warn("Password reset email could not be delivered; verify mail configuration."); }
        });
    }

    @Transactional
    public void resendVerification(String email) {
        if (!requireVerification) return;
        users.findByEmailIgnoreCaseAndActiveTrue(normalizeEmail(email))
            .filter(user -> !user.isEmailVerified())
            .ifPresent(user -> {
                try { sendToken(user, "VERIFY_EMAIL", 24, "Verify your LearnSphere email", "/?verify="); }
                catch (RuntimeException ex) { log.warn("Verification email could not be delivered; verify mail configuration."); }
            });
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        requireBcryptSafePassword(password, HttpStatus.BAD_REQUEST);
        AuthActionToken token = usableToken(rawToken, "RESET_PASSWORD");
        token.consume(Instant.now());
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.invalidateTokens();
        actionTokens.deleteAllByUser_IdAndPurpose(user.getId(), "RESET_PASSWORD");
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserView currentUser(String email) {
        User user = users.findByEmailIgnoreCaseAndActiveTrue(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
        return userView(user);
    }

    @Transactional
    public void logout(String email) {
        User user = users.findByEmailIgnoreCaseAndActiveTrue(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
        user.invalidateTokens();
    }

    private void sendToken(User user, String purpose, long ttlHours, String subject, String path) {
        actionTokens.deleteAllByUser_IdAndPurpose(user.getId(), purpose);
        String raw = AuthTokenCodec.newToken();
        actionTokens.save(new AuthActionToken(user, purpose, AuthTokenCodec.hash(raw), Instant.now().plus(ttlHours, ChronoUnit.HOURS)));
        mail.sendActionLink(user.getEmail(), subject, frontendUrl + path + raw);
    }

    private AuthActionToken usableToken(String raw, String purpose) {
        if (raw == null || raw.isBlank() || raw.length() > 128)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired.");
        AuthActionToken token = actionTokens.findByTokenHashAndPurpose(AuthTokenCodec.hash(raw), purpose)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired."));
        if (!token.isUsable(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired.");
        return token;
    }

    private AuthResult issueToken(User user, boolean verificationRequired, String message) {
        Instant now = Instant.now();
        Instant expires = now.plus(tokenHours, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("learnsphere-api").issuedAt(now).expiresAt(expires)
            .subject(user.getEmail()).claim("name", user.getFullName()).claim("role", user.getRole())
            .claim("roles", java.util.List.of("ROLE_" + user.getRole()))
            .claim("ver", user.getTokenVersion()).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResult(token, new AuthDtos.AuthResponse(userView(user), verificationRequired, message));
    }

    private AuthDtos.UserView userView(User user) {
        return new AuthDtos.UserView(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isEmailVerified());
    }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private void requireBcryptSafePassword(String password, HttpStatus status) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(status, status == HttpStatus.BAD_REQUEST
                ? "Password must not exceed 72 UTF-8 bytes." : "Email or password is incorrect.");
    }
}
