package vn.edu.ute.milktea.entity.market;
import jakarta.persistence.*;
import lombok.*;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.catalog.Product;
@Entity @Table(name="Shop") @Getter @Setter @NoArgsConstructor
public class Shop {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
@Column(name="Name",nullable=false,length=120) private String name;
@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="OwnerId") private Account owner;
@ManyToMany @JoinTable(name="ShopManager",joinColumns=@JoinColumn(name="ShopId"),inverseJoinColumns=@JoinColumn(name="AccountId")) private java.util.Set<Account> managers=new java.util.HashSet<>();
@Column(name="Active",nullable=false) private boolean active;
@Column(name="ManagerCommissionEnabled",nullable=false) private boolean managerCommissionEnabled;
@Column(name="CommissionPercent",nullable=false,precision=5,scale=2) private java.math.BigDecimal commissionPercent=java.math.BigDecimal.ZERO;
@Column(name="Address",nullable=false,length=500) private String address;
@Column(name="Area",length=120) private String area;
@Column(name="Latitude") private Double latitude;
@Column(name="Longitude") private Double longitude;
@Column(name="CreatedAt",nullable=false) private java.time.Instant createdAt=java.time.Instant.now();
}

