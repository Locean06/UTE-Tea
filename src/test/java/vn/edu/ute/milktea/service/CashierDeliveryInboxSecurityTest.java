package vn.edu.ute.milktea.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.repository.market.DeliveryOrderRepository;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.MarketService;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CashierDeliveryInboxSecurityTest {
    @Mock DeliveryOrderRepository deliveryOrders;
    @Mock AccountRepository accounts;
    @InjectMocks MarketService marketService;

    @Test void cashierCanReadDeliveryOrdersFromSeparateTable() {
        when(deliveryOrders.findAll()).thenReturn(List.of());
        assertTrue(marketService.cashierDeliveryOrders(CurrentActor.authenticated(1L, Role.CASHIER)).isEmpty());
        verify(deliveryOrders).findAll();
    }
    @Test void customersCannotReadAllDeliveryOrders() {
        assertThrows(BusinessException.class,
            () -> marketService.cashierDeliveryOrders(CurrentActor.authenticated(2L, Role.CUSTOMER)));
        verifyNoInteractions(deliveryOrders);
    }
    @Test void shipperCannotReadAllDeliveryOrders() {
        assertThrows(BusinessException.class,
            () -> marketService.cashierDeliveryOrders(CurrentActor.authenticated(3L, Role.SHIPPER)));
        verifyNoInteractions(deliveryOrders);
    }
    @Test void adminCanReadDeliveryOrders() {
        when(deliveryOrders.findAll()).thenReturn(List.of());
        assertTrue(marketService.cashierDeliveryOrders(CurrentActor.authenticated(4L, Role.ADMIN)).isEmpty());
    }
}
