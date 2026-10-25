package vn.edu.ute.milktea.repository.account;
import org.springframework.data.jpa.repository.*;
import vn.edu.ute.milktea.entity.account.ProductInterest;
import java.util.*;
public interface ProductInterestRepository extends JpaRepository<ProductInterest,Long> {
    Optional<ProductInterest> findByAccountIdAndProductId(Long accountId,Long productId);
    @EntityGraph(attributePaths={"product","product.category"})
    List<ProductInterest> findByAccountIdOrderByLastViewedAtDesc(Long accountId);
    long countByProductIdAndFavoriteTrue(Long productId);
}
