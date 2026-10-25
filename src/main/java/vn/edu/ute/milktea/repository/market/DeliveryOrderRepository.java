package vn.edu.ute.milktea.repository.market;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ute.milktea.entity.market.DeliveryOrder;
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder,Long> {
boolean existsByGatewayReference(String reference);
java.util.Optional<DeliveryOrder> findByAccountIdAndIdempotencyKey(Long accountId,String idempotencyKey);
java.util.List<DeliveryOrder> findByAccountIdOrderByIdDesc(Long accountId);
java.util.List<DeliveryOrder> findByShopIdOrderByIdDesc(Long shopId);
java.util.List<DeliveryOrder> findByShipperIdOrderByIdDesc(Long shipperId);
long countByAccountIdAndVoucherId(Long accountId,Long voucherId);
@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
@org.springframework.data.jpa.repository.Query("select o from DeliveryOrder o where o.id=:id")
java.util.Optional<DeliveryOrder> lockId(@org.springframework.data.repository.query.Param("id") Long id);
}

