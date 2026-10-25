package vn.edu.ute.milktea.entity.market;
import jakarta.persistence.*;
import lombok.*;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.catalog.Product;
@Entity @Table(name="DeliveryOrder",uniqueConstraints=@UniqueConstraint(name="UQ_DeliveryOrder_Key",columnNames={"AccountId","IdempotencyKey"})) @Getter @Setter @NoArgsConstructor
public class DeliveryOrder {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
public enum Status { NEW, CONFIRMED, SHIPPING, DELIVERED, DELIVERY_FAILED, CANCELLED, RETURN_REQUESTED, RETURNED, REFUNDED }
public enum Method { COD, VNPAY }
@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="AccountId") private Account account;
@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ShopId") private Shop shop;
@ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ShipperId") private Account shipper;
@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="CarrierId") private Carrier carrier;
@ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="VoucherId") private Voucher voucher;
@Column(name="Recipient",nullable=false,length=120) private String recipient;
@Column(name="Phone",nullable=false,length=20) private String phone;
@Column(name="AddressLine",nullable=false,length=500) private String addressLine;
@Column(name="Subtotal",nullable=false,precision=18,scale=2) private java.math.BigDecimal subtotal;
@Column(name="ShippingFee",nullable=false,precision=18,scale=2) private java.math.BigDecimal shippingFee;
@Column(name="DiscountAmount",nullable=false,precision=18,scale=2) private java.math.BigDecimal discountAmount=java.math.BigDecimal.ZERO;
@Column(name="TotalAmount",nullable=false,precision=18,scale=2) private java.math.BigDecimal totalAmount;
@Column(name="CommissionPercent",nullable=false,precision=5,scale=2) private java.math.BigDecimal commissionPercent;
@Enumerated(EnumType.STRING) @Column(name="Status",nullable=false,length=24) private Status status=Status.NEW;
@Enumerated(EnumType.STRING) @Column(name="PaymentMethod",nullable=false,length=16) private Method paymentMethod=Method.COD;
@Column(name="Paid",nullable=false) private boolean paid;
@Column(name="GatewayReference",length=120) private String gatewayReference;
@Column(name="RefundReference",length=120) private String refundReference;
@Column(name="IdempotencyKey",nullable=false,length=64) private String idempotencyKey;
@Column(name="RequestHash",nullable=false,length=64) private String requestHash;
@Column(name="CreatedAt",nullable=false) private java.time.Instant createdAt=java.time.Instant.now();
@Column(name="DeliveredAt") private java.time.Instant deliveredAt;
@Column(name="DeliveryFailed",nullable=false) private boolean deliveryFailed;
@Version @Column(name="Version",nullable=false) private Long version=0L;
@OneToMany(mappedBy="order",cascade=CascadeType.ALL) private java.util.List<DeliveryLine> lines=new java.util.ArrayList<>();
}

