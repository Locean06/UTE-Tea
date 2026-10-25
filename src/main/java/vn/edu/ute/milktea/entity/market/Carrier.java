package vn.edu.ute.milktea.entity.market;
import jakarta.persistence.*;
import lombok.*;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.catalog.Product;
@Entity @Table(name="Carrier") @Getter @Setter @NoArgsConstructor
public class Carrier {
@ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ShopId") private Shop shop;
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
@Column(name="Name",nullable=false,length=120) private String name;
@Column(name="Fee",nullable=false,precision=18,scale=2) private java.math.BigDecimal fee=java.math.BigDecimal.ZERO;
@Column(name="Active",nullable=false) private boolean active=true;
}

