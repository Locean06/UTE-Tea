package vn.edu.ute.milktea.repository.market;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ute.milktea.entity.market.Voucher;
public interface VoucherRepository extends JpaRepository<Voucher,Long> {
java.util.Optional<Voucher> findByCode(String code);
@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
@org.springframework.data.jpa.repository.Query("select v from Voucher v where v.code=:code")
java.util.Optional<Voucher> lockCode(@org.springframework.data.repository.query.Param("code") String code);
}

