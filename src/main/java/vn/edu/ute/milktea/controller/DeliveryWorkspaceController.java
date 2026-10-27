package vn.edu.ute.milktea.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ute.milktea.common.ApiResponse;
import vn.edu.ute.milktea.dto.MarketDto.OrderView;
import vn.edu.ute.milktea.dto.MarketDto.Transition;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.DeliveryWorkspaceService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shipper")
public class DeliveryWorkspaceController {
    private final DeliveryWorkspaceService service;

    @GetMapping("/workspace")
    public ApiResponse<DeliveryWorkspaceService.Workspace> workspace(@AuthenticationPrincipal CurrentActor actor) {
        return ApiResponse.ok(service.workspace(actor));
    }

    @PutMapping("/orders/{id}/status")
    public ApiResponse<OrderView> updateStatus(@AuthenticationPrincipal CurrentActor actor,
                                                @PathVariable Long id, @Valid @RequestBody Transition transition) {
        return ApiResponse.ok(service.updateStatus(actor, id, transition));
    }
}
