package vn.edu.ute.milktea.service.order;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.ute.milktea.common.*;
import vn.edu.ute.milktea.entity.catalog.Product;
import vn.edu.ute.milktea.entity.market.Shop;
import vn.edu.ute.milktea.entity.order.Order;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.repository.market.ShopRepository;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import java.util.*;

/** Branch selection and catalogue resolution shared by checkout and POS. */
@Service
@org.springframework.transaction.annotation.Transactional
@RequiredArgsConstructor
public class OrderBranchService {
    private final ShopRepository shops;
    private final ProductRepository products;
    private final AccountRepository accounts;
    private final vn.edu.ute.milktea.repository.market.ShopCustomerAccessRepository customers;
    @Value("${app.pos.shop-id:0}") private long posShopId;

    public Shop resolve(Long requested) {
        Long id = requested != null ? requested : posShopId > 0 ? posShopId : null;
        if (id == null) throw invalid("Chưa cấu hình cơ sở phục vụ mặc định. Quản trị viên cần cấu hình POS_SHOP_ID");
        return shops.findByIdWithLock(id).filter(Shop::isActive)
                .orElseThrow(() -> invalid("Cơ sở không tồn tại hoặc đã ngừng phục vụ"));
    }
    public Shop tableBranch(vn.edu.ute.milktea.entity.table.DiningTable table) {
        return resolve(table.getShop() != null ? table.getShop().getId() : null);
    }
    public Product product(Product source, Shop shop) {
        if (shop == null) return source; // Historical orders only.
        if (source.getShop() != null && shop.getId().equals(source.getShop().getId())) return source;
        var matches = products.findByShopId(shop.getId()).stream()
                .filter(p -> p.menuVariantIdentity().equals(source.menuVariantIdentity())).toList();
        if (matches.isEmpty() && source.getShop() == null && shop.getId().equals(posShopId)) return source;
        if (matches.size() != 1) throw invalid("Cơ sở không phục vụ duy nhất món/size: " + source.getName() + " " + source.getSize());
        return matches.getFirst();
    }
    public boolean canAccess(Shop shop, Long staffId) {
        if (staffId == null) return false;
        var account = accounts.findById(staffId).orElse(null);
        if (account == null || !Boolean.TRUE.equals(account.getActive())) return false;
        if (account.getRole() == Role.ADMIN) return true;
        if (account.getRole() != Role.CASHIER && account.getRole() != Role.KITCHEN) return false;
        // Old POS records have no branch. Preserve historical access for existing staff.
        if (shop == null) return true;
        return shops.findAccessible(staffId).stream().anyMatch(s -> s.getId().equals(shop.getId()));
    }
    public void assertStaff(Order order, Long staffId) {
        if (!canAccess(order.getShop(), staffId)) throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED,
                "Nhân viên không được phân công cho cơ sở của đơn hàng này");
    }
    public void assertCustomer(Shop shop, vn.edu.ute.milktea.security.CurrentActor actor) {
        if (actor != null && actor.getAccountId() != null)
            customers.findByShopIdAndAccountId(shop.getId(), actor.getAccountId()).filter(c -> c.isBlocked())
                    .ifPresent(c -> {throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED,"Tài khoản bị hạn chế mua tại cơ sở này");});
    }
    public List<Map<String,Object>> options() {
        return shops.findAll().stream().filter(Shop::isActive)
                .map(s -> Map.<String,Object>of("id",s.getId(),"name",s.getName(),"address",s.getAddress())).toList();
    }
    private BusinessException invalid(String message) { return BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,message); }
}
