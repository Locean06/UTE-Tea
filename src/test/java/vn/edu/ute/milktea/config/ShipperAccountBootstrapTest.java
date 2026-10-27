package vn.edu.ute.milktea.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.repository.account.AccountRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipperAccountBootstrapTest {

    @Mock AccountRepository accounts;
    @Mock PasswordEncoder encoder;
    @InjectMocks ShipperAccountBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bootstrap, "enabled", true);
        ReflectionTestUtils.setField(bootstrap, "email", "  SHIPPER@Example.com  ");
        ReflectionTestUtils.setField(bootstrap, "password", "StrongPassword@2026");
        ReflectionTestUtils.setField(bootstrap, "fullName", "Nhân viên giao hàng");
        ReflectionTestUtils.setField(bootstrap, "phone", "0901000005");
    }

    @Test
    void enabledCreatesVerifiedActiveShipperWithEncodedPassword() throws Exception {
        when(accounts.findByEmail("shipper@example.com")).thenReturn(Optional.empty());
        when(encoder.encode("StrongPassword@2026")).thenReturn("bcrypt-hash");
        bootstrap.run();
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(captor.capture());
        Account saved = captor.getValue();
        assertEquals("shipper@example.com", saved.getEmail());
        assertEquals("bcrypt-hash", saved.getPasswordHash());
        assertEquals(Role.SHIPPER, saved.getRole());
        assertTrue(saved.getActive());
        assertTrue(saved.getEmailVerified());
        assertEquals(1L, saved.getTokenVersion());
        assertEquals("Nhân viên giao hàng", saved.getFullName());
        assertEquals("0901000005", saved.getPhone());
    }

    @Test
    void disabledDoesNotAccessAccounts() throws Exception {
        ReflectionTestUtils.setField(bootstrap, "enabled", false);
        bootstrap.run();
        verifyNoInteractions(accounts, encoder);
    }

    @Test
    void secondStartupDoesNotChangeExistingShipperOrPassword() throws Exception {
        Account existing = Account.builder().email("shipper@example.com").role(Role.SHIPPER)
                .passwordHash("saved-hash").active(false).build();
        when(accounts.findByEmail("shipper@example.com")).thenReturn(Optional.of(existing));
        bootstrap.run();
        verify(accounts, never()).save(any());
        verifyNoInteractions(encoder);
        assertFalse(existing.getActive());
        assertEquals("saved-hash", existing.getPasswordHash());
    }

    @Test
    void existingNonShipperRoleIsNotUpgraded() {
        Account customer = Account.builder().email("shipper@example.com").role(Role.CUSTOMER).build();
        when(accounts.findByEmail("shipper@example.com")).thenReturn(Optional.of(customer));
        assertThrows(IllegalStateException.class, () -> bootstrap.run());
        assertEquals(Role.CUSTOMER, customer.getRole());
        verify(accounts, never()).save(any());
    }

    @Test
    void weakPasswordIsRejectedWithoutDatabaseModification() {
        ReflectionTestUtils.setField(bootstrap, "password", "123456");
        assertThrows(IllegalStateException.class, () -> bootstrap.run());
        verifyNoInteractions(accounts, encoder);
    }
}
