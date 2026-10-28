package vn.edu.ute.milktea.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.dto.OrderDto;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.entity.market.Shop;
import vn.edu.ute.milktea.repository.market.ShopRepository;
import vn.edu.ute.milktea.repository.order.OrderRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.order.OrderService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickupBranchSelectionTest {
    @Mock ShopRepository shopRepository;
    @Mock OrderRepository orderRepository;
    @InjectMocks OrderService orderService;

    @Test
    void customerMustSelectPickupBranchBeforeSavingOrder() {
        var request = new OrderDto.CreateCounterOrderRequest();
        var customer = CurrentActor.authenticated(3L, Role.CUSTOMER);
        var error = assertThrows(BusinessException.class,
                () -> orderService.createCounterOrder(request, customer, null));
        assertTrue(error.getMessage().contains("chi nhánh"));
        verifyNoInteractions(shopRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cashierMustSelectShopBeforeCreatingWalkInOrder() {
        var request = new OrderDto.CreateCounterOrderRequest();
        var cashier = CurrentActor.authenticated(5L, Role.CASHIER);
        var error = assertThrows(BusinessException.class,
                () -> orderService.createCounterOrder(request, cashier, null));
        assertTrue(error.getMessage().contains("chi nhánh"));
        verifyNoInteractions(shopRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void inactiveBranchCannotReceivePickupOrder() {
        var request = new OrderDto.CreateCounterOrderRequest();
        request.setShopId(2L);
        var inactive = new Shop();
        inactive.setId(2L);
        inactive.setActive(false);
        when(shopRepository.findById(2L)).thenReturn(Optional.of(inactive));
        var error = assertThrows(BusinessException.class,
                () -> orderService.createCounterOrder(request, CurrentActor.authenticated(3L, Role.CUSTOMER), null));
        assertTrue(error.getMessage().contains("Chi nhánh"));
        verify(orderRepository, never()).save(any());
    }
}
