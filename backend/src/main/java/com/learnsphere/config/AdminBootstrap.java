package com.learnsphere.config;

import com.learnsphere.user.User;
import com.learnsphere.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.email = email.trim(); this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length < 12
            || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("Configure both ADMIN_EMAIL and ADMIN_PASSWORD; admin password must be 12 to 72 UTF-8 bytes.");
        String normalized = email.toLowerCase(Locale.ROOT);
        User user = users.findByEmailIgnoreCase(normalized).orElseGet(() ->
            users.save(new User("LearnSphere Administrator", normalized, passwordEncoder.encode(password))));
        user.setRole("ADMIN");
        user.activate();
        user.verifyEmail();
    }
}
