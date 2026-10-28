package vn.edu.ute.milktea.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.repository.account.AccountRepository;

/**
 * Opt-in, create-only bootstrap for a fresh production database.
 * Never modifies an existing account's password or role.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionAdminBootstrap implements CommandLineRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_BOOTSTRAP_ENABLED:false}")
    private boolean enabled;
    @Value("${ADMIN_BOOTSTRAP_EMAIL:}")
    private String email;
    @Value("${ADMIN_BOOTSTRAP_PASSWORD:}")
    private String password;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;
        String normalized = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL must be valid");
        }
        if (password == null || password.length() < 16) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_PASSWORD must have at least 16 characters");
        }
        if (accounts.findByEmail(normalized).isPresent()) {
            log.info("Admin bootstrap skipped: account already exists");
            return;
        }
        accounts.save(Account.builder()
                .email(normalized)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("UTE Tea Administrator")
                .role(Role.ADMIN)
                .active(true)
                .emailVerified(true)
                .tokenVersion(1L)
                .build());
        log.info("Admin bootstrap created an administrator; disable ADMIN_BOOTSTRAP_ENABLED now");
    }
}
