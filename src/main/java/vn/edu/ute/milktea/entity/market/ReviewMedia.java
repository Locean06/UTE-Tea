package vn.edu.ute.milktea.entity.market;
import jakarta.persistence.*;
import lombok.*;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.catalog.Product;
@Entity @Table(name="ReviewMedia") @Getter @Setter @NoArgsConstructor
public class ReviewMedia {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ReviewId") private ProductReview review;
@Column(name="MediaType",nullable=false,length=12) private String mediaType;
@Column(name="Url",nullable=false,length=1000) private String url;
@Column(name="PublicId",nullable=false,length=255) private String publicId;
}

