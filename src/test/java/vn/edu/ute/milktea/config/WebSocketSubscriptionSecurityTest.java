package vn.edu.ute.milktea.config;

import org.junit.jupiter.api.Test;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.security.CurrentActor;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketSubscriptionSecurityTest {
    private final CurrentActor cashier = CurrentActor.authenticated(1L, Role.CASHIER);
    private final CurrentActor kitchen = CurrentActor.authenticated(2L, Role.KITCHEN);
    private final CurrentActor admin = CurrentActor.authenticated(3L, Role.ADMIN);
    private final CurrentActor customer = CurrentActor.authenticated(4L, Role.CUSTOMER);

    @Test void staffTopicsMustMatchCorrectRole() {
        assertTrue(WebSocketConfig.isAllowedSubscription("/topic/cashier", cashier));
        assertTrue(WebSocketConfig.isAllowedSubscription("/topic/cashier", admin));
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/cashier", customer));
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/kitchen", cashier));
        assertTrue(WebSocketConfig.isAllowedSubscription("/topic/kitchen", kitchen));
        assertTrue(WebSocketConfig.isAllowedSubscription("/topic/stock", kitchen));
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/admin", kitchen));
        assertTrue(WebSocketConfig.isAllowedSubscription("/topic/admin", admin));
    }

    @Test void onlyOwnTableSessionIsVisibleToGuest() {
        CurrentActor guest = CurrentActor.tableGuest(42L, 3L);
        assertTrue(WebSocketConfig.isAllowedSubscription("/topic/table-sessions/42", guest));
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/table-sessions/43", guest));
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/table-sessions/42/items", guest));
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/table-sessions/invalid", guest));
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/table-sessions/9999999999999999999", guest));
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/table-sessions/-42", guest));
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/table-sessions/042", guest));
        assertTrue(WebSocketConfig.isAllowedSubscription("/topic/table-sessions/42", cashier));
    }

    @Test void unexpectedDestinationsAreDeniedByDefault() {
        for (String destination : new String[]{"/topic/secret", "/queue/private", "/topic/admin/details", "/topic/cashier/other", "/topic/table-sessions/0"}) {
            assertFalse(WebSocketConfig.isAllowedSubscription(destination, admin), destination);
        }
        assertFalse(WebSocketConfig.isAllowedSubscription("/topic/cashier", null));
    }
}
