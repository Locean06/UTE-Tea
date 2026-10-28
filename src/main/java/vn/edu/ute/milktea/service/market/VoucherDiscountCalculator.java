package vn.edu.ute.milktea.service.market;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Server-side invoice discount calculator shared by quote and checkout. */
public final class VoucherDiscountCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private VoucherDiscountCalculator() {}

    public static BigDecimal discount(String type, BigDecimal amount, BigDecimal percent,
                                       BigDecimal subtotal, BigDecimal shippingFee) {
        if (subtotal == null || shippingFee == null || subtotal.signum() < 0 || shippingFee.signum() < 0) {
            throw new IllegalArgumentException("Invoice amount must not be negative");
        }
        BigDecimal bill = subtotal.add(shippingFee);
        if ("PERCENT".equals(type)) {
            if (percent == null || percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException("Voucher percent out of range");
            }
            return bill.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP).min(bill);
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Voucher amount out of range");
        }
        // Existing fixed-amount vouchers keep their original 'food subtotal only' rule.
        return amount.min(subtotal);
    }
}
