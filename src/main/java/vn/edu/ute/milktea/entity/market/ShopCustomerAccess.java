package vn.edu.ute.milktea.entity.market;
import jakarta.persistence.*;
import lombok.*;
import vn.edu.ute.milktea.entity.account.Account;
@Entity @Table(name="ShopCustomerAccess",uniqueConstraints=@UniqueConstraint(name="UQ_ShopCustomerAccess",columnNames={"ShopId","AccountId"}))
@Getter @Setter @NoArgsConstructor
public class ShopCustomerAccess {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ShopId") private Shop shop;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="AccountId") private Account account;
 @Column(name="Blocked",nullable=false) private boolean blocked;
}
