package vn.edu.ute.milktea.entity.market;
import jakarta.persistence.*;
import lombok.*;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.catalog.Product;
@Entity @Table(name="ProductReview",uniqueConstraints=@UniqueConstraint(name="UQ_ProductReview_Account",columnNames={"AccountId","ProductId"})) @Getter @Setter @NoArgsConstructor
public class ProductReview {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="AccountId") private Account account;
@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ProductId") private Product product;
@ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="DeliveryOrderId") private DeliveryOrder deliveryOrder;
@ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="CounterOrderId") private vn.edu.ute.milktea.entity.order.Order counterOrder;
@Column(name="Stars",nullable=false) private int stars;
@Column(name="Comment",nullable=false,length=3000) private String comment;
@Column(name="CreatedAt",nullable=false) private java.time.Instant createdAt=java.time.Instant.now();
@OneToMany(mappedBy="review",cascade=CascadeType.ALL) private java.util.List<ReviewMedia> media=new java.util.ArrayList<>();
}

