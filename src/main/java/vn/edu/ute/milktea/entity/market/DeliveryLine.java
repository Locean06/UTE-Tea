package vn.edu.ute.milktea.entity.market;
import jakarta.persistence.*;
import lombok.*;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.catalog.Product;
@Entity @Table(name="DeliveryLine") @Getter @Setter @NoArgsConstructor
public class DeliveryLine {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="OrderId") private DeliveryOrder order;
@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ProductId") private Product product;
@Column(name="ProductName",nullable=false,length=120) private String productName;
@Column(name="Quantity",nullable=false) private int quantity;
@Column(name="UnitPrice",nullable=false,precision=18,scale=2) private java.math.BigDecimal unitPrice;
@Column(name="SnapshotSize",length=12) private String snapshotSize;
@Column(name="Note",length=500) private String note;
@Column(name="IsTopping",nullable=false) private boolean topping;
}

