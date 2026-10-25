package vn.edu.ute.milktea.repository.market;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ute.milktea.entity.market.ProductReview;
public interface ProductReviewRepository extends JpaRepository<ProductReview,Long> {
java.util.List<ProductReview> findByProductIdOrderByIdDesc(Long productId);
boolean existsByAccountIdAndProductId(Long accountId,Long productId);
}

