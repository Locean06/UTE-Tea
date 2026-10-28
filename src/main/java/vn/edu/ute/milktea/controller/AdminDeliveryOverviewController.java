package vn.edu.ute.milktea.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.repository.market.DeliveryOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminDeliveryOverviewController {
    private final DeliveryOrderRepository repository;
    public record DeliveryRow(Long id,String shopName,String recipient,String status,String paymentMethod,boolean paid,BigDecimal totalAmount,Instant createdAt) {}
    @GetMapping("/delivery")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly=true)
    public List<DeliveryRow> delivery() {
        return repository.findAll().stream().sorted(Comparator.comparing(o -> o.getId(),Comparator.reverseOrder()))
            .map(o -> new DeliveryRow(o.getId(),o.getShop().getName(),o.getRecipient(),o.getStatus().name(),o.getPaymentMethod().name(),o.isPaid(),o.getTotalAmount(),o.getCreatedAt())).toList();
    }
}
