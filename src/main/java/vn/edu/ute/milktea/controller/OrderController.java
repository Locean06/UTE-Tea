package vn.edu.ute.milktea.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ute.milktea.common.ApiResponse;
import vn.edu.ute.milktea.dto.OrderDto;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.security.JwtAuthenticationFilter;
import vn.edu.ute.milktea.service.order.OrderService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final vn.edu.ute.milktea.service.order.OrderBranchService branchService;

    @GetMapping("/public/checkout/branches")
    public ApiResponse<java.util.List<java.util.Map<String,Object>>> branches() {
        return ApiResponse.ok(branchService.options());
    }

    @PostMapping("/public/checkout/quote")
    public ApiResponse<java.util.Map<String,Object>> quote(@Valid @RequestBody OrderDto.CreateCounterOrderRequest body,
            @AuthenticationPrincipal CurrentActor actor) {
        return ApiResponse.ok(orderService.quote(body, actor));
    }

    @PostMapping({ "/orders/table", "/customer/orders" })
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> createTableOrder(
            @Valid @RequestBody OrderDto.CreateTableOrderRequest requestBody,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        CurrentActor actor = (CurrentActor) request.getAttribute(JwtAuthenticationFilter.CURRENT_ACTOR_ATTR);
        OrderDto.OrderResponse response = orderService.createTableOrder(requestBody, actor, idempotencyKey);
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }

    @PostMapping({ "/orders/counter", "/cashier/orders" })
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> createCounterOrder(
            @Valid @RequestBody OrderDto.CreateCounterOrderRequest requestBody,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CurrentActor actor) {

        OrderDto.OrderResponse response = orderService.createCounterOrder(requestBody, actor, idempotencyKey);
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }

    @GetMapping("/customer/orders")
    public ResponseEntity<ApiResponse<java.util.List<OrderDto.OrderResponse>>> getCustomerOrders(
            HttpServletRequest request) {
        CurrentActor actor = (CurrentActor) request.getAttribute(JwtAuthenticationFilter.CURRENT_ACTOR_ATTR);
        if (actor == null || !actor.isAuthenticated()) {
            throw vn.edu.ute.milktea.common.BusinessException.unauthorized(
                    vn.edu.ute.milktea.common.ErrorCode.ACCESS_DENIED,
                    "Vui lòng đăng nhập để xem lịch sử đơn hàng");
        }
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrdersForCustomer(actor)));
    }

    @GetMapping("/customer/orders/{id}")
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> getCustomerOrder(
            @PathVariable("id") Long orderId,
            HttpServletRequest request) {
        CurrentActor actor = (CurrentActor) request.getAttribute(JwtAuthenticationFilter.CURRENT_ACTOR_ATTR);
        if (actor == null || !actor.isAuthenticated()) {
            throw vn.edu.ute.milktea.common.BusinessException.unauthorized(
                    vn.edu.ute.milktea.common.ErrorCode.ACCESS_DENIED,
                    "Vui lòng đăng nhập để xem lịch sử đơn hàng");
        }
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderForCustomer(orderId, actor)));
    }

    @GetMapping("/cashier/orders")
    public ResponseEntity<ApiResponse<java.util.List<OrderDto.OrderResponse>>> getCashierOrders(
            @RequestParam(value = "status", required = false) vn.edu.ute.milktea.entity.order.OrderStatus status,
            @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
            @RequestParam(value = "tab", required = false) String tab,
            @RequestParam(value = "query", required = false) String query) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrdersForCashier(status, paymentStatus, tab, query)));
    }

    @GetMapping("/cashier/orders/work-summary")
    public ResponseEntity<ApiResponse<OrderDto.OrderWorkSummaryResponse>> getOrderWorkSummary() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderWorkSummary()));
    }

    @GetMapping("/kitchen/orders")
    public ResponseEntity<ApiResponse<java.util.List<OrderDto.OrderResponse>>> getKitchenOrders() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrdersForKitchen()));
    }

    @PostMapping("/cashier/orders/{id}/assign-table")
    public ResponseEntity<ApiResponse<Void>> assignToTable(
            @PathVariable("id") Long orderId,
            @Valid @RequestBody OrderDto.AssignTableRequest requestBody,
            @AuthenticationPrincipal CurrentActor actor) {

        Long cashierId = actor != null ? actor.getAccountId() : null;
        orderService.assignCounterOrderToTable(orderId, requestBody.getTableId(), cashierId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/cashier/orders/{id}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmOrder(
            @PathVariable("id") Long orderId,
            @AuthenticationPrincipal CurrentActor actor) {

        Long cashierId = actor != null ? actor.getAccountId() : null;
        orderService.confirm(orderId, cashierId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/kitchen/orders/{id}/start")
    public ResponseEntity<ApiResponse<Void>> startCooking(
            @PathVariable("id") Long orderId,
            @AuthenticationPrincipal CurrentActor actor) {

        Long kitchenId = actor != null ? actor.getAccountId() : null;
        orderService.start(orderId, kitchenId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/kitchen/orders/{id}/complete")
    public ResponseEntity<ApiResponse<Void>> completeCooking(
            @PathVariable("id") Long orderId,
            @AuthenticationPrincipal CurrentActor actor) {

        Long kitchenId = actor != null ? actor.getAccountId() : null;
        orderService.complete(orderId, kitchenId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> getOrder(@PathVariable("id") Long orderId,
            @AuthenticationPrincipal CurrentActor actor) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderForCustomer(orderId, actor)));
    }
}
