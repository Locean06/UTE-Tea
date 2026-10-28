package vn.edu.ute.milktea.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ute.milktea.common.ApiResponse;
import vn.edu.ute.milktea.dto.MarketDto.OrderView;
import vn.edu.ute.milktea.dto.MarketDto.ShipperOption;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.MarketService;
import java.util.List;

/** Staff-only read models for delivery orders. POS orders remain in DonHang. */
@RestController
@RequestMapping("/api/v1/cashier/delivery-orders")
@RequiredArgsConstructor
public class CashierDeliveryController {
    private final MarketService market;

    @GetMapping
    public ApiResponse<List<OrderView>> list(@AuthenticationPrincipal CurrentActor actor) {
        return ApiResponse.ok(market.cashierDeliveryOrders(actor));
    }

    @GetMapping("/shippers")
    public ApiResponse<List<ShipperOption>> shippers(@AuthenticationPrincipal CurrentActor actor) {
        return ApiResponse.ok(market.cashierActiveShippers(actor));
    }
}
