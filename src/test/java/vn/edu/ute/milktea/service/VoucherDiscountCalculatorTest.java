package vn.edu.ute.milktea.service;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import vn.edu.ute.milktea.service.market.VoucherDiscountCalculator;

class VoucherDiscountCalculatorTest {
    @Test void tenPercentReducesEntireDeliveryBill() {
        BigDecimal discount = VoucherDiscountCalculator.discount("PERCENT",BigDecimal.ZERO,new BigDecimal("10"),new BigDecimal("100000"),new BigDecimal("15000"));
        assertEquals(0,discount.compareTo(new BigDecimal("11500")));
        assertEquals(0,new BigDecimal("100000").add(new BigDecimal("15000")).subtract(discount).compareTo(new BigDecimal("103500")));
    }
    @Test void twentyTwoPercentVoucher() {
        assertEquals(0,VoucherDiscountCalculator.discount("PERCENT",BigDecimal.ZERO,new BigDecimal("22"),new BigDecimal("50000"),BigDecimal.ZERO).compareTo(new BigDecimal("11000")));
    }
    @Test void legacyFixedDiscountNeverReducesShipping() {
        assertEquals(0,VoucherDiscountCalculator.discount("FIXED_AMOUNT",new BigDecimal("20000"),BigDecimal.ZERO,new BigDecimal("15000"),new BigDecimal("15000")).compareTo(new BigDecimal("15000")));
    }
    @Test void invalidPercentageRejected() {
        assertThrows(IllegalArgumentException.class,()->VoucherDiscountCalculator.discount("PERCENT",BigDecimal.ZERO,new BigDecimal("101"),BigDecimal.ONE,BigDecimal.ZERO));
    }
}
