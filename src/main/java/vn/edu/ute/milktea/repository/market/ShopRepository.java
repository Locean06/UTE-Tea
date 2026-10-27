package vn.edu.ute.milktea.repository.market;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ute.milktea.entity.market.Shop;
public interface ShopRepository extends JpaRepository<Shop,Long> {
@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
@org.springframework.data.jpa.repository.Query("select s from Shop s where s.id=:id")
java.util.Optional<Shop> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);
@org.springframework.data.jpa.repository.Query("select distinct s from Shop s left join s.managers m where s.owner.id=:accountId or m.id=:accountId") java.util.List<Shop> findAccessible(@org.springframework.data.repository.query.Param("accountId") Long accountId);
}

