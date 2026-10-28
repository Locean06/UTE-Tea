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
        {"ADMIN", "nguyenthienan@utetea.vn", "Nguyễn Thiên An", "$2y$11$6lyZMpHOSKgaw2og8NGWZeHEKMMJBqZJWyK5Xy9iPqdGbpf3fRw5q"},
        {"ADMIN", "tranminhkhoa@utetea.vn", "Trần Minh Khoa", "$2y$11$uqNCYdbLMYnf6I9lwNv2Q.2HCY1cWptjlMuToPt.62zc/eYBwznbu"},
        {"ADMIN", "lehoangphuc@utetea.vn", "Lê Hoàng Phúc", "$2y$11$rGrj0Y9nJ5OLXx6U.Y76xOPugO/MnmoXR8UkZwnQX72vI7NHeTbom"},
        {"CASHIER", "phamthuha@utetea.vn", "Phạm Thu Hà", "$2y$11$FkLK4sNehKacmAyEw9KxlesvouAUBGLvo2L1BblJ2E8REX7orToXu"},
        {"CASHIER", "vongoclinh@utetea.vn", "Võ Ngọc Linh", "$2y$11$Egf9gdxkWHZKXe5Cux5Q9uSMT7H1m6DzGnRkxC24GchhIqQ9HB7gS"},
        {"CASHIER", "buitramanh@utetea.vn", "Bùi Trâm Anh", "$2y$11$FiMmTdWzPcEw2/anE7Zh..YoeEQc2UDQK/UramC.JJzWv/DA2JfQi"},
        {"KITCHEN", "nguyenducminh@utetea.vn", "Nguyễn Đức Minh", "$2y$11$EKYKKnjZCI9JcnujzjoJTu9DsZST0cBmD7C2PaFInL16ygVwg2Tcm"},
        {"KITCHEN", "tranduylong@utetea.vn", "Trần Duy Long", "$2y$11$uKZZ1.DlaV9y46hHm3SMC.12qYJtbbwBVwtJFsmMGSz9abQYFcYCa"},
        {"KITCHEN", "hoangbaotran@utetea.vn", "Hoàng Bảo Trân", "$2y$11$0ftnT.m0d2uxbaQ2b5d1Yu.cFbJTOqTe9lFfpbE4RIOHYzMj9ury2"},
        {"CUSTOMER", "maiphuongthao@utetea.vn", "Mai Phương Thảo", "$2y$11$t6nhpFr2CF6tk/VVIHJCxOm3VAXdC2wj/tMXXgM3xVc1654qNDlnC"},
        {"CUSTOMER", "nguyenkhanhvy@utetea.vn", "Nguyễn Khánh Vy", "$2y$11$69KvPwuyDzay1pKlqQmQV.2Q6MFti0J8yzPB.jS4QO3tzQqxHCdfm"},
        {"CUSTOMER", "levanhoan@utetea.vn", "Lê Văn Hoàn", "$2y$11$RpE1MnM055uzeR2XeGpoQuR2v3JJhtGPWsm2UnWgojbaLpzcS7gcq"},
        {"VENDOR", "dangthanhbinh@utetea.vn", "Đặng Thanh Bình", "$2y$11$WVqy0h7aNTh2Xp9TPAyjju/A2JfCXpRrGhkHGlpgfoCNXhmn4Vl3e"},
        {"VENDOR", "phamgiahan@utetea.vn", "Phạm Gia Hân", "$2y$11$XzD5uU80qhA2QwKMoZpUfuaeSB5KzFewOFwqV3l1FtgamYEX5wUXq"},
        {"VENDOR", "truonghieunghia@utetea.vn", "Trương Hiếu Nghĩa", "$2y$11$5wuPc6CC0sRJnxv8/ED3yuflJ6S.7nwOQ3UvNMp8jaZkAqD7ZNdn."},
        {"MANAGER", "dohongnhung@utetea.vn", "Đỗ Hồng Nhung", "$2y$11$9.huofGkmZaUIH5tuU2wfOQvZK.D96dBShrSrkXZ8z7udoaXfHypK"},
        {"MANAGER", "nguyentuananh@utetea.vn", "Nguyễn Tuấn Anh", "$2y$11$IE7cbJWsfPlLWCY/SWmh/OzGgwQlZLhtRPmtknkI4MmnbVk8wnfue"},
        {"MANAGER", "lethanhthao@utetea.vn", "Lê Thanh Thảo", "$2y$11$lePX9Vq3BKb4.2RS21egwOG7VmGdkAz1w8pYz/zsUTCJQIns2tPeK"},
        {"SHIPPER", "vominhquan@utetea.vn", "Võ Minh Quân", "$2y$11$u3AxES7Lg9qRzgYGfaCWaetrEVJmpBB8nueqnqeBrKPKQB1joX3zS"},
        {"SHIPPER", "tranhuyhoang@utetea.vn", "Trần Huy Hoàng", "$2y$11$FGi5DpV8OIxSfZLWZOrcgeoCRWRoUVKlA6v4xAb1G4y0EWjkdxzPK"},
        {"SHIPPER", "buiduckhang@utetea.vn", "Bùi Đức Khang", "$2y$11$Q0msuwvVqfye6Isf6.kTl.XZO0dLJ.UYTXeSKQmCW9.UKyE5rpufy"}
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
