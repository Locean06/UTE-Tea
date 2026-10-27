package vn.edu.ute.milktea.entity.order;

import jakarta.persistence.*;
import lombok.*;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.table.TableSession;

import java.time.Instant;

@Entity
@Table(name = "DonHang", uniqueConstraints = @UniqueConstraint(name="UX_DonHang_MaYeuCau", columnNames="MaYeuCau"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaDH")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaPhien")
    private TableSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaTK")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "Nguon", nullable = false, length = 10)
    private OrderSource source; // TABLE, COUNTER

    @Enumerated(EnumType.STRING)
    @Column(name = "TrangThai", nullable = false, length = 24)
    private OrderStatus status; // CHO_THANH_TOAN, CHO_XAC_NHAN, CHO_CHE_BIEN, DANG_CHE_BIEN, HOAN_THANH, DA_HUY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ThayTheMaDH")
    private Order replacementOfOrder;

    @Column(name = "MaYeuCau", length = 64)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ShopId")
    private vn.edu.ute.milktea.entity.market.Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(name = "RequestedPaymentMethod", length = 20)
    private vn.edu.ute.milktea.entity.payment.PaymentMethod requestedPaymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VoucherId")
    private vn.edu.ute.milktea.entity.market.Voucher voucher;

    @Column(name = "RequestFingerprint", length = 64)
    private String requestFingerprint;

    @Column(name = "GhiChu", length = 500)
    private String note;

    @Column(name = "TaoLuc", nullable = false)
    private Instant createdAt;

    @Column(name = "BatDauLuc")
    private Instant startedAt;

    @Column(name = "HoanThanhLuc")
    private Instant completedAt;

    @Version
    @Column(name = "Version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
