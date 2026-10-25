package vn.edu.ute.milktea.entity.catalog;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "DanhMuc",uniqueConstraints=@UniqueConstraint(name="UQ_DanhMuc_Shop_Name",columnNames={"ShopId","TenDM"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaDM")
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="ShopId")
    private vn.edu.ute.milktea.entity.market.Shop shop;

    @Column(name = "TenDM", nullable = false, length = 100)
    private String name;

    @Column(name = "HoatDong", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "ChoPhepTopping", nullable = false)
    @Builder.Default
    private Boolean allowTopping = true;
}
