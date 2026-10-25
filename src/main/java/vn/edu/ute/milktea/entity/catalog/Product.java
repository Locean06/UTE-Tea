package vn.edu.ute.milktea.entity.catalog;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "MonUong", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_MonUong_Shop_Ten_Size", columnNames = {"ShopId", "TenMon", "Size"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaMon")
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="ShopId")
    private vn.edu.ute.milktea.entity.market.Shop shop;

    @Column(name="DeliveryStock")
    private Integer deliveryStock;

    @Version @Column(name="CatalogVersion",nullable=false)
    @Builder.Default private Long catalogVersion=0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaDM", nullable = false)
    private Category category;

    @Column(name = "TenMon", nullable = false, length = 120)
    private String name;

    @Column(name="SearchName",length=240)
    private String searchName;

    public static String normalizeSearch(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ','d').replace('Đ','D').toLowerCase(java.util.Locale.ROOT).trim();
    }
    @PrePersist @PreUpdate
    public void updateSearchName() { searchName=normalizeSearch(name); }

    @Column(name = "Size", nullable = false, length = 12)
    private String size; // S, M, L

    @Column(name = "MoTa", length = 1000)
    private String description;

    @Column(name = "Gia", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "AnhUrl", length = 1000)
    private String imageUrl;

    @Column(name = "AnhPublicId", length = 255)
    private String imagePublicId;

    @Column(name = "HoatDong", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
