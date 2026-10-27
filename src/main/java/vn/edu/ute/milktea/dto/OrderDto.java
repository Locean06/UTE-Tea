package vn.edu.ute.milktea.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ute.milktea.dto.CartDto;
import vn.edu.ute.milktea.entity.order.InvoiceStatus;
import vn.edu.ute.milktea.entity.order.OrderSource;
import vn.edu.ute.milktea.entity.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTableOrderRequest {
        private Long shopId;
        private vn.edu.ute.milktea.entity.payment.PaymentMethod paymentMethod;
        @jakarta.validation.constraints.Size(max = 32)
        private String voucherCode;
        private BigDecimal expectedTotal;
        private String qrCode;
        private String tableToken;

        @NotNull(message = "Mã bàn không được để trống")
        private Long tableId;

        private Long expectedSessionId; // null nếu đặt đơn đầu mở phiên

        @NotEmpty(message = "Danh sách món đặt không được rỗng")
        @jakarta.validation.Valid
        private List<CartDto.CartItemRequest> items;

        private List<CartDto.CartItemRequest> remainingCartItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCounterOrderRequest {
        private Long shopId;
        private vn.edu.ute.milktea.entity.payment.PaymentMethod paymentMethod;
        @jakarta.validation.constraints.Size(max = 32)
        private String voucherCode;
        private BigDecimal expectedTotal;

        @NotEmpty(message = "Danh sách món đặt không được rỗng")
        @jakarta.validation.Valid
        private List<CartDto.CartItemRequest> items;

        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignTableRequest {
        @NotNull(message = "Mã bàn không được để trống")
        private Long tableId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private String size;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private String note;
        private List<CartDto.ToppingItemResponse> toppings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private Long shopId;
        private String shopName;
        private String fulfillmentMode;
        private String paymentStatus;
        private String bankName;
        private String bankAccountNumber;
        private String bankAccountHolder;
        private String transferContent;
        private String vietQrUrl;
        private Long orderId;
        private Long sessionId;
        private Long tableId;
        private String tableName;
        private OrderSource source;
        private OrderStatus status;
        private String customerName;
        private String customerEmail;
        private Long invoiceId;
        private BigDecimal subtotal;
        private BigDecimal discountAmount;
        private BigDecimal surchargeAmount;
        private BigDecimal totalAmount;
        private BigDecimal discountPercent;
        private String discountType;
        private BigDecimal discountConfigValue;
        private InvoiceStatus invoiceStatus;
        private String paymentMethod;
        private String paymentRequestMethod;
        private Instant paidAt;
        private String cashierName;
        private CancellationDto.RefundResponse refund;
        private boolean cancellationPending;
        private boolean paid;
        private boolean staffCreated;
        private boolean transferNotified;
        private List<OrderItemResponse> items;
        private Instant createdAt;
        private String guestTableToken;   // Trả về khi đơn đầu tạo phiên
        private String guestCounterToken; // Token theo từng đơn (quầy hoặc tại bàn), dùng để theo dõi riêng một đơn
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderWorkSummaryResponse {
        private long totalCount;
        private long confirmCount;
        private long cookingCount;
        private long completedCount;
        private long transferCount;
        private long cancelCount;
        private long refundCount;
        private long pendingActionCount;
    }
}
