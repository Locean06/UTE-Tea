package vn.edu.ute.milktea.repository.account;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ute.milktea.entity.account.DeliveryAddress;
import java.util.List;
import java.util.Optional;
public interface DeliveryAddressRepository extends JpaRepository<DeliveryAddress,Long> {
    List<DeliveryAddress> findByAccountIdOrderByIdAsc(Long accountId);
    Optional<DeliveryAddress> findByIdAndAccountId(Long id,Long accountId);
}
