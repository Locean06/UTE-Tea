package vn.edu.ute.milktea.repository.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ute.milktea.entity.catalog.Product;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Product p where p.id=:id")
    java.util.Optional<Product> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);
    List<Product> findByShopId(Long shopId);
    @EntityGraph(attributePaths = {"category", "shop"})
    @org.springframework.data.jpa.repository.Query("select p from Product p left join p.shop s where p.active=true and p.category.active=true and (p.shop is null or s.active=true)")
    List<Product> findAllByActiveTrue();
    @EntityGraph(attributePaths = {"category"})
    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);
    boolean existsByNameIgnoreCaseAndSizeIgnoreCase(String name, String size);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findAllByOrderByIdAsc();

    long countByImagePublicId(String imagePublicId);
}


