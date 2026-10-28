package vn.edu.ute.milktea.service.market;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.common.ErrorCode;
import vn.edu.ute.milktea.dto.MarketDto.OrderView;
import vn.edu.ute.milktea.dto.MarketDto.Transition;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.entity.market.DeliveryOrder;
import vn.edu.ute.milktea.repository.market.DeliveryOrderRepository;
import vn.edu.ute.milktea.security.CurrentActor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Dedicated view of assigned orders. Never accepts an arbitrary shipper ID from the client. */
@Service
@RequiredArgsConstructor
public class DeliveryWorkspaceService {
    private final DeliveryOrderRepository orders;
    private final MarketService market;

    public record Statistics(long assigned, long waiting, long shipping, long delivered,
                             long failed, long other, long codDelivered, BigDecimal codCollected,
                             double successRate) {}
    public record Workspace(Statistics statistics, List<OrderView> orders) {}

    private Long shipperId(CurrentActor actor) {
        if (actor == null || !actor.hasRole(Role.SHIPPER) || actor.getAccountId() == null) {
            throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED, "Chỉ nhân viên giao hàng được truy cập");
        }
        return actor.getAccountId();
    }

    @Transactional(readOnly = true)
    public Workspace workspace(CurrentActor actor) {
        var assigned = orders.findByShipperIdOrderByIdDesc(shipperId(actor));
        long waiting = assigned.stream().filter(o -> o.getStatus() == DeliveryOrder.Status.CONFIRMED).count();
        long shipping = assigned.stream().filter(o -> o.getStatus() == DeliveryOrder.Status.SHIPPING).count();
        long delivered = assigned.stream().filter(o -> o.getStatus() == DeliveryOrder.Status.DELIVERED).count();
        long failed = assigned.stream().filter(o -> o.getStatus() == DeliveryOrder.Status.DELIVERY_FAILED).count();
        long other = assigned.size() - waiting - shipping - delivered - failed;
        var paidCod = assigned.stream().filter(o -> o.getStatus() == DeliveryOrder.Status.DELIVERED
                && o.getPaymentMethod() == DeliveryOrder.Method.COD && o.isPaid()).toList();
        BigDecimal collected = paidCod.stream().map(DeliveryOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double successRate = delivered + failed == 0 ? 0 : Math.round(1000.0 * delivered / (delivered + failed)) / 10.0;
        return new Workspace(new Statistics(assigned.size(), waiting, shipping, delivered, failed,
                other, paidCod.size(), collected, successRate),
                assigned.stream().map(market::orderView).toList());
    }

    @Transactional
    public OrderView updateStatus(CurrentActor actor, Long orderId, Transition transition) {
        Long accountId = shipperId(actor);
        if (transition == null || transition.status() == null || !Set.of(DeliveryOrder.Status.SHIPPING, DeliveryOrder.Status.DELIVERED,
                DeliveryOrder.Status.DELIVERY_FAILED).contains(transition.status())) {
            throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED, "Trạng thái không thuộc quyền nhân viên giao hàng");
        }
        // Existing transition locks the order, compares version and validates its assigned shipper.
        // Pre-check ensures no unassigned/foreign order enters the mutation path.
        var assigned = orders.findById(orderId).orElseThrow(() ->
                BusinessException.forbidden(ErrorCode.ACCESS_DENIED, "Đơn hàng không được phân công"));
        if (assigned.getShipper() == null || !accountId.equals(assigned.getShipper().getId())) {
            throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED, "Đơn hàng không được phân công");
        }
        return market.transition(actor, orderId, transition);
    }
}
