package vn.edu.ute.milktea.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ute.milktea.common.ApiResponse;
import vn.edu.ute.milktea.dto.MarketDto.OrderView;
import vn.edu.ute.milktea.entity.market.DeliveryOrder;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.MarketService;
import java.util.List;

/** Kitchen delivery queue; distinct from the POS DonHang queue. */
@RestController
@RequestMapping("/api/v1/kitchen/delivery-orders")
@RequiredArgsConstructor
public class KitchenDeliveryController {
    private final MarketService market;

    public record PreparationRequest(@NotNull DeliveryOrder.Preparation status,
                                     @NotNull Long version) {}

    @GetMapping
    public ApiResponse<List<OrderView>> list(@AuthenticationPrincipal CurrentActor actor) {
        return ApiResponse.ok(market.kitchenDeliveryOrders(actor));
    }

    @PutMapping("/{id}/preparation")
    public ApiResponse<OrderView> prepare(@AuthenticationPrincipal CurrentActor actor,
                                           @PathVariable Long id,
                                           @Valid @RequestBody PreparationRequest body) {
        return ApiResponse.ok(market.kitchenPrepare(actor,id,body.status(),body.version()));
    }
}
