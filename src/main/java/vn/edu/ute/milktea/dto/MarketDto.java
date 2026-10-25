package vn.edu.ute.milktea.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.edu.ute.milktea.entity.market.DeliveryOrder;
import vn.edu.ute.milktea.entity.account.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class MarketDto {
 public record ShopInput(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=500) String address) {}
 public record ShopView(Long id,String name,String address,boolean active,BigDecimal commissionPercent,Long ownerId,boolean managerCommissionEnabled) {}
 public record ShopConfig(boolean active,@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent,boolean managerCommissionEnabled) {
  public ShopConfig(boolean active,BigDecimal commissionPercent){this(active,commissionPercent,false);}
 }
 public record CategoryInput(@NotBlank @Size(max=100) String name,boolean active) {}
 public record ProductInput(@NotNull Long categoryId,@NotBlank @Size(max=120) String name,@NotBlank @Size(max=12) String size,
   @Size(max=1000) String description,@NotNull @DecimalMin("0.01") BigDecimal price,@Min(0) int stock,boolean active,Long catalogVersion) {
   public ProductInput(Long categoryId,String name,String size,String description,BigDecimal price,int stock,boolean active){this(categoryId,name,size,description,price,stock,active,null);}
 }
 public record ProductView(Long id,String name,String size,String description,BigDecimal price,String imageUrl,Long categoryId,String categoryName,Long shopId,String shopName,Integer stock,boolean active,Long catalogVersion,double rating,long favorites) {}
 public record CatalogPage(List<ProductView> items,long total,int page,int size) {}
 public record CategoryView(Long id,String name,Long shopId,boolean active) {}
 public record CarrierInput(@NotBlank @Size(max=120) String name,@NotNull @DecimalMin("0") BigDecimal fee,boolean active) {}
 public record CarrierView(Long id,String name,BigDecimal fee,Long shopId,boolean active) {}
 public record VoucherInput(@NotBlank @Pattern(regexp="[A-Z0-9_-]{3,32}") String code,@NotNull @DecimalMin("0.01") BigDecimal discountAmount,
   @NotNull @DecimalMin("0") BigDecimal minimumAmount,@NotNull Instant startsAt,@NotNull Instant endsAt,@Min(1) int usageLimit,@Min(1) int perAccountLimit,boolean active) {}
 public record VoucherView(Long id,String code,Long shopId,BigDecimal discountAmount,BigDecimal minimumAmount,Instant startsAt,Instant endsAt,int usageLimit,int usedCount,int perAccountLimit,boolean active) {}
 public record BuyLine(@NotNull Long productId,@Min(1) @Max(99) int quantity) {}
 public record Checkout(@NotNull Long shopId,@NotNull Long addressId,@NotNull Long carrierId,@Size(max=32) String voucherCode,
   @NotNull DeliveryOrder.Method paymentMethod,@NotEmpty @Size(max=100) List<@Valid BuyLine> items) {}
 public record LineView(Long productId,String name,int quantity,BigDecimal price) {}
 public record OrderView(Long id,Long shopId,String shopName,Long accountId,Long shipperId,DeliveryOrder.Status status,DeliveryOrder.Method paymentMethod,
   boolean paid,String recipient,String phone,String addressLine,BigDecimal subtotal,BigDecimal shippingFee,BigDecimal discountAmount,BigDecimal totalAmount,
   BigDecimal commissionPercent,Instant createdAt,boolean deliveryFailed,Long version,List<LineView> items) {}
 public record Transition(@NotNull DeliveryOrder.Status status,@NotNull Long version,@Size(max=120) String reference) {}
 public record AssignShipper(@NotNull Long accountId,@NotNull Long version) {}
 public record RoleInput(@NotNull Role role,boolean active) {}
 public record AccountView(Long id,String email,String name,Role role,boolean active) {}
 public record Summary(long orders,long delivered,long failed,BigDecimal paidRevenue,BigDecimal platformCommission) {}
 public record ReviewInput(@NotNull Long orderId,boolean delivery,@Min(1) @Max(5) int stars,@NotBlank @Size(min=50,max=3000) String comment) {}
 public record MediaView(Long id,String type,String url) {}
 public record ReviewView(Long id,Long accountId,int stars,String comment,Instant createdAt,List<MediaView> media) {}
}
