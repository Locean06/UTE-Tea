package vn.edu.ute.milktea.repository.market;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ute.milktea.entity.market.ShopCustomerAccess;
import java.util.*;
public interface ShopCustomerAccessRepository extends JpaRepository<ShopCustomerAccess,Long> {
 Optional<ShopCustomerAccess> findByShopIdAndAccountId(Long shopId,Long accountId);
 List<ShopCustomerAccess> findByShopId(Long shopId);
}
