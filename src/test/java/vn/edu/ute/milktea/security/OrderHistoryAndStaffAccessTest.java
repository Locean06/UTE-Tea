package vn.edu.ute.milktea.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.controller.CustomerPageController;
import vn.edu.ute.milktea.controller.OrderController;
import vn.edu.ute.milktea.controller.StaffPageController;
import vn.edu.ute.milktea.controller.TableController;
import vn.edu.ute.milktea.service.table.TableContextService;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.entity.order.Order;
import vn.edu.ute.milktea.entity.table.TableSession;
import vn.edu.ute.milktea.repository.order.OrderRepository;
import vn.edu.ute.milktea.service.order.OrderService;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderHistoryAndStaffAccessTest {
    @Mock OrderService orderService;
    @InjectMocks OrderController orderController;
    @InjectMocks CustomerPageController customerPages;
    @InjectMocks StaffPageController staffPages;
    @InjectMocks TableController tableController;
    @Mock TableContextService tableContextService;
    @Mock OrderRepository orderRepository;
    // Use the real access-checking service instance, with its repository set through Mockito.
    @InjectMocks OrderService guardedOrderService;

    @Test void anonymousBrowserDoesNotSeeOrderHistoryPage() {
        String result = customerPages.ordersPage(null, "1", null, null, null, new ExtendedModelMap());
        assertEquals("redirect:/login?redirect=/orders", result);
    }

    @Test void tableGuestCannotListHistoryEvenWithValidSessionToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthenticationFilter.CURRENT_ACTOR_ATTR))
                .thenReturn(CurrentActor.tableGuest(6L, 1L));
        assertThrows(BusinessException.class, () -> orderController.getCustomerOrders(request));
        verifyNoInteractions(orderService);
    }

    @Test void guestCannotOpenPrivateHistoryDetailEndpoint() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthenticationFilter.CURRENT_ACTOR_ATTR))
                .thenReturn(CurrentActor.tableGuest(6L, 1L));
        assertThrows(BusinessException.class, () -> orderController.getCustomerOrder(17L, request));
        verifyNoInteractions(orderService);
    }

    @Test void tableSessionTokenDoesNotGrantAnotherOrdersReceipt() {
        var order = Order.builder().id(17L).session(TableSession.builder().id(6L).build()).build();
        when(orderRepository.findById(17L)).thenReturn(Optional.of(order));
        assertThrows(BusinessException.class, () -> guardedOrderService.getOrderForCustomer(17L,
                CurrentActor.tableGuest(6L, 1L)));
    }

    @Test void tableIdAloneCannotMintAnonymousSessionToken() {
        assertThrows(BusinessException.class, () -> tableController.getTableContext(null, "1", null));
        verifyNoInteractions(tableContextService);
    }

    @Test void guestCannotCreateOrdersForArbitraryTable() {
        assertThrows(BusinessException.class, () -> guardedOrderService.createTableOrder(null, null, null));
        assertThrows(BusinessException.class, () -> guardedOrderService.createTableOrder(null,
                CurrentActor.tableGuest(6L, 1L), null));
        verifyNoInteractions(orderRepository);
    }

    @Test void customerCannotExchangeRetiredQrForTableSessionToken() {
        assertThrows(BusinessException.class, () -> tableController.getTableContext("QR_TABLE_01_TOKEN_A1B2C3D4", null,
                CurrentActor.authenticated(4L, Role.CUSTOMER)));
        verifyNoInteractions(tableContextService);
    }

    @Test void staffPortalIsRestrictedByRole() {
        var model = new ExtendedModelMap();
        assertEquals("redirect:/cashier", staffPages.staffHubPage(CurrentActor.authenticated(2L, Role.CASHIER), model));
        assertEquals("redirect:/kitchen", staffPages.staffHubPage(CurrentActor.authenticated(3L, Role.KITCHEN), model));
        assertEquals("redirect:/login?redirect=/staff",
                staffPages.staffHubPage(CurrentActor.authenticated(4L, Role.CUSTOMER), new ExtendedModelMap()));
        assertEquals("redirect:/login?redirect=/kitchen",
                staffPages.kitchenPage(CurrentActor.authenticated(2L, Role.CASHIER), new ExtendedModelMap()));
        assertEquals("redirect:/login?redirect=/cashier",
                staffPages.cashierPage(CurrentActor.authenticated(3L, Role.KITCHEN), new ExtendedModelMap()));
    }
}
