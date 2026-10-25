package vn.edu.ute.milktea.entity.account;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import vn.edu.ute.milktea.entity.catalog.Product;

@Entity @Table(name="ProductInterest",uniqueConstraints=@UniqueConstraint(name="UQ_ProductInterest",columnNames={"AccountId","ProductId"}))
@Getter @Setter @NoArgsConstructor
public class ProductInterest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="AccountId") private Account account;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ProductId") private Product product;
    @Column(name="Favorite",nullable=false) private boolean favorite;
    @Column(name="LastViewedAt") private Instant lastViewedAt;
}
