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

import java.time.Instant;
import java.util.Locale;

/**
 * Explicit, one-time staff provisioning for existing local/production databases.
 * Disabled by default. Never resets a password, reactivates a disabled account,
 * or upgrades an unrelated email to a privileged role.
 */
@Slf4j
@Component
@Profile("!demo & !test")
@RequiredArgsConstructor
public class ShipperAccountBootstrap implements CommandLineRunner {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.shipper.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${app.shipper.bootstrap.email:}")
    private String email;

    @Value("${app.shipper.bootstrap.password:}")
    private String password;

    @Value("${app.shipper.bootstrap.name:Nhân viên giao hàng UTE Tea}")
    private String fullName;

    @Value("${app.shipper.bootstrap.phone:}")
    private String phone;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > 254 || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException("SHIPPER_BOOTSTRAP_EMAIL must be a valid email address");
        }
        if (password == null || password.length() < 12) {
            throw new IllegalStateException("SHIPPER_BOOTSTRAP_PASSWORD must be at least 12 characters");
        }
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 120) {
            throw new IllegalStateException("SHIPPER_BOOTSTRAP_NAME must contain 1 to 120 characters");
        }
        if (phone != null && phone.length() > 20) {
            throw new IllegalStateException("SHIPPER_BOOTSTRAP_PHONE must not exceed 20 characters");
        }

        var existing = accounts.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.SHIPPER) {
                throw new IllegalStateException("SHIPPER_BOOTSTRAP_EMAIL belongs to a different role; choose another email");
            }
            log.info("Shipper bootstrap skipped: account already exists (no password, role or active-status changes)");
            return;
        }

        accounts.save(Account.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName.trim())
                .phone(phone == null || phone.isBlank() ? null : phone.trim())
                .role(Role.SHIPPER)
                .active(true)
                .emailVerified(true)
                .tokenVersion(1L)
                .createdAt(Instant.now())
                .build());
        log.info("Created SHIPPER account via explicitly enabled bootstrap");
    }
}
