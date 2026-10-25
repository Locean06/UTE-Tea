package vn.edu.ute.milktea.entity.market;
import jakarta.persistence.*;
import lombok.*;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.catalog.Product;
@Entity @Table(name="Voucher") @Getter @Setter @NoArgsConstructor
public class Voucher {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
@Column(name="Code",nullable=false,unique=true,length=32) private String code;
@ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ShopId") private Shop shop;
@Column(name="DiscountAmount",nullable=false,precision=18,scale=2) private java.math.BigDecimal discountAmount;
@Column(name="MinimumAmount",nullable=false,precision=18,scale=2) private java.math.BigDecimal minimumAmount=java.math.BigDecimal.ZERO;
@Column(name="StartsAt",nullable=false) private java.time.Instant startsAt;
@Column(name="EndsAt",nullable=false) private java.time.Instant endsAt;
@Column(name="UsageLimit",nullable=false) private int usageLimit;
@Column(name="UsedCount",nullable=false) private int usedCount;
@Column(name="PerAccountLimit",nullable=false) private int perAccountLimit=1;
@Column(name="Active",nullable=false) private boolean active=true;
}

