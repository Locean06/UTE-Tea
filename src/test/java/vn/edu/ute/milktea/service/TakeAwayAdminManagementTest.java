package vn.edu.ute.milktea.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import vn.edu.ute.milktea.repository.market.ShopRepository;
import vn.edu.ute.milktea.repository.market.VoucherRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.MarketService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TakeAwayAdminManagementTest {
    @Autowired MarketService market;
    @Autowired AccountRepository accounts;
    @Autowired ShopRepository shops;
    @Autowired VoucherRepository vouchers;

    private CurrentActor admin() {
        var account = accounts.save(Account.builder()
                .email("takeaway-" + UUID.randomUUID() + "@test.local")
                .passwordHash("test-only")
                .role(Role.ADMIN)
                .build());
        return CurrentActor.authenticated(account.getId(), Role.ADMIN);
    }

    @Test
    void adminCreatesUpdatesArchivesAndRestoresBranch() {
        var actor = admin();
        var created = market.createShop(actor, new ShopInput("UTE Tea Test", "1 Vo Van Ngan"));
        assertTrue(created.active());

        var updated = market.editShop(actor, created.id(), new ShopInput("UTE Tea Test 2", "2 Vo Van Ngan", "Thu Duc", null, null));
        assertEquals("UTE Tea Test 2", updated.name());
        assertEquals("Thu Duc", updated.area());

        market.archiveShop(actor, created.id());
        assertFalse(shops.findById(created.id()).orElseThrow().isActive());
        market.configureShop(actor, created.id(), new ShopConfig(true, BigDecimal.ZERO));
        assertTrue(shops.findById(created.id()).orElseThrow().isActive());
    }

    @Test
    void adminCanEditAndDisableVoucherWithoutLosingUsageHistory() {
        var actor = admin();
        var code = "TA" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
        var now = Instant.now();
        var created = market.saveVoucher(actor, null, new VoucherInput(code, BigDecimal.ZERO,
                BigDecimal.ZERO, now.minusSeconds(60), now.plusSeconds(3600), 50, 1, true,
                "PERCENT", new BigDecimal("10")));
        assertTrue(created.active());
        var modified = market.updateVoucher(actor, created.id(), new VoucherInput(code, BigDecimal.ZERO,
                BigDecimal.ZERO, now.minusSeconds(60), now.plusSeconds(7200), 80, 2, false,
                "PERCENT", new BigDecimal("15")));
        assertFalse(modified.active());
        assertEquals(0, new BigDecimal("15").compareTo(modified.discountPercent()));
        assertEquals(0, vouchers.findByCode(code).orElseThrow().getUsedCount());
    }
}
