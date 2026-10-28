package vn.edu.ute.milktea.config;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.entity.market.Voucher;
import vn.edu.ute.milktea.repository.market.VoucherRepository;

/** Seed demo voucher once; local/prod SQL Server uses migration 14. */
@Component
@Profile({"demo", "prod"})
@Order(12)
@RequiredArgsConstructor
public class PromotionDemoData implements CommandLineRunner {
    private final VoucherRepository vouchers;
    private final org.springframework.core.env.Environment environment;

    @org.springframework.beans.factory.annotation.Value("${DEMO_SEED_ENABLED:false}")
    private boolean demoSeedEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod")) && !demoSeedEnabled) return;
        if (vouchers.findByCode("GIAMGIA10").isPresent()) return;
        Voucher v = new Voucher();
        v.setCode("GIAMGIA10");
        v.setDiscountType("PERCENT");
        v.setDiscountPercent(new BigDecimal("10.00"));
        v.setDiscountAmount(BigDecimal.ZERO);
        v.setMinimumAmount(BigDecimal.ZERO);
        v.setStartsAt(Instant.parse("2026-01-01T00:00:00Z"));
        v.setEndsAt(Instant.parse("2032-12-31T23:59:59Z"));
        v.setUsageLimit(100000);
        v.setPerAccountLimit(1000);
        v.setActive(true);
        vouchers.save(v);
    }
}
