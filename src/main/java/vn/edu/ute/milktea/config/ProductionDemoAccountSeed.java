package vn.edu.ute.milktea.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.repository.account.AccountRepository;

/**
 * One-time demo-only accounts for a dedicated Neon database.
 * BCrypt hashes only. Plaintext login details are distributed separately.
 * Existing accounts are never reset and the seed is opt-in.
 */
@Slf4j
@Component
@Profile("prod")
@Order(-10)
@RequiredArgsConstructor
public class ProductionDemoAccountSeed implements CommandLineRunner {
    private final AccountRepository accounts;
    @Value("${DEMO_SEED_ENABLED:false}")
    private boolean enabled;

    // role | email | display name | precomputed BCrypt password hash
    private static final String[][] USERS = {
        {"ADMIN", "nguyenthienan@utetea.vn", "Nguyễn Thiên An", "$2y$11$kHXxrdOTFR2u3jTclgJuBedhc2aFQKpyrIVis72pZhkgPX4EXioLm"},
        {"ADMIN", "tranminhkhoa@utetea.vn", "Trần Minh Khoa", "$2y$11$KNux0KW5eWjXnsmek1ha/.D9.DN59ElePI7EpBS4P.lR3nF5aZ0wS"},
        {"ADMIN", "lehoangphuc@utetea.vn", "Lê Hoàng Phúc", "$2y$11$SbPxlEb24hWbaXyoIuldx.B/niXynUjpkEVxWe96mxxC2B/kq3RM2"},
        {"CASHIER", "phamthuha@utetea.vn", "Phạm Thu Hà", "$2y$11$c4gKqQEdthsogaZbM.yz9O3YtISwWfOA19ms.bAf.gPBy6D4ml.UK"},
        {"CASHIER", "vongoclinh@utetea.vn", "Võ Ngọc Linh", "$2y$11$CE7a216kU82XTWg0ADzAt.P3dZMHNdNbEb307Pj.Tp8jT341FEaxK"},
        {"CASHIER", "buitramanh@utetea.vn", "Bùi Trâm Anh", "$2y$11$EWpcsUUGoSUCRPMMUl/8ye9FEI4qizFFWbmYbneUVayErQcNsMi02"},
        {"KITCHEN", "nguyenducminh@utetea.vn", "Nguyễn Đức Minh", "$2y$11$mlPY9AmjQOhDplG5SLLp1OA4Li5he1N.4v4xKIiWsEJ0i8EF4Lt9."},
        {"KITCHEN", "tranduylong@utetea.vn", "Trần Duy Long", "$2y$11$6hx0chGpZ.WHdqrgJB/SMuyE4g7HqCNaqGemCIwSkV6QiZtX.eifO"},
        {"KITCHEN", "hoangbaotran@utetea.vn", "Hoàng Bảo Trân", "$2y$11$iKR7eDGCRod.x6kqRG0sJeUCOyanXDEMQOIfAqKWa2wvPJEp9dIBa"},
        {"CUSTOMER", "maiphuongthao@utetea.vn", "Mai Phương Thảo", "$2y$11$HV63ED89EXuUhHD570WltOTWCVOkuxMbIwDTtfwK0VX1Rt21oR2uy"},
        {"CUSTOMER", "nguyenkhanhvy@utetea.vn", "Nguyễn Khánh Vy", "$2y$11$rjft1/yvpWfCbwqnox/ARuZKQ8xZ2HE1MwC63L8MKVTx1mDwoJiu6"},
        {"CUSTOMER", "levanhoan@utetea.vn", "Lê Văn Hoàn", "$2y$11$gCWrt6gp.s/Fgi/KRL2xneAyh8a23c.mjSOZC/g7QsYNUmW.v7n2O"},
        {"VENDOR", "dangthanhbinh@utetea.vn", "Đặng Thanh Bình", "$2y$11$GcOB15.evysN9C7XS.4bR.4kYIPIrz9VkaADOUmX2PDS7RJU0uLNK"},
        {"VENDOR", "phamgiahan@utetea.vn", "Phạm Gia Hân", "$2y$11$qJK.VAsXdRJaMuCaKMEYTe7E58Q4hV8G1J.pV0JsOBdXx3qGMBHAy"},
        {"VENDOR", "truonghieunghia@utetea.vn", "Trương Hiếu Nghĩa", "$2y$11$UPiOWX9.GnjwGNouAMDn0OnCQobqDO7XjNVp/3pKRqzcM2LJHoPj."},
        {"MANAGER", "dohongnhung@utetea.vn", "Đỗ Hồng Nhung", "$2y$11$/xDP6/HWSP0MIIzEJ99FvONjFlAmoreK/ha3hiv0prIkFJQCHrE.2"},
        {"MANAGER", "nguyentuananh@utetea.vn", "Nguyễn Tuấn Anh", "$2y$11$ULpGQCOGB12JctUtRrl/QOR6dCUMooL/NOdVgiNdYmbzVDyCNmXy6"},
        {"MANAGER", "lethanhthao@utetea.vn", "Lê Thanh Thảo", "$2y$11$LzrFKtlNl0HUaDsA1z6JZODqP7nGRCRmXHZwkGEUSYQqMznfThb7u"},
        {"SHIPPER", "vominhquan@utetea.vn", "Võ Minh Quân", "$2y$11$D7UQEaF3dcno5jkXoY8piuufnYfOUyxUtozK48eIDyrWPHvYwtsUS"},
        {"SHIPPER", "tranhuyhoang@utetea.vn", "Trần Huy Hoàng", "$2y$11$OtyIrFsjEkGNKIssDu3UiubvvcqtBAATitwM.xrTLDtURzpv9hI.W"},
        {"SHIPPER", "buiduckhang@utetea.vn", "Bùi Đức Khang", "$2y$11$renhnl.o2/UEwW9fchA59eCH4wDB4iCQL6zJZRk.E/vsSoOiWX37O"}
    };

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;
        int created = 0;
        for (String[] row : USERS) {
            var existing = accounts.findByEmail(row[1]);
            if (existing.isPresent()) {
                if (existing.get().getRole() != Role.valueOf(row[0])) {
                    throw new IllegalStateException("Demo account email belongs to a different role: " + row[1]);
                }
                continue;
            }
            accounts.save(Account.builder()
                    .email(row[1])
                    .fullName(row[2])
                    .passwordHash(row[3])
                    .role(Role.valueOf(row[0]))
                    .active(true)
                    .emailVerified(true)
                    .tokenVersion(1L)
                    .build());
            created++;
        }
        log.info("Neon demo accounts seed complete: {} created, {} configured", created, USERS.length);
    }
}
