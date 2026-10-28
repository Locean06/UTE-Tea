"""Offline regression checks for discount code invoice and address UI.
Run: python tests/voucher_checkout_contract.py
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
files = {
    'checkout': root / 'src/main/resources/templates/fragments/delivery-checkout.html',
    'menu': root / 'src/main/resources/templates/customer/menu.html',
    'backend': root / 'src/main/java/vn/edu/ute/milktea/service/market/MarketService.java',
    'calculator': root / 'src/main/java/vn/edu/ute/milktea/service/market/VoucherDiscountCalculator.java',
    'dto': root / 'src/main/java/vn/edu/ute/milktea/dto/MarketDto.java',
    'migration': root / 'database/sqlserver/14_percent_voucher_bill.sql',
    'receipt': root / 'src/main/resources/static/js/customer/my-orders.js',
}
text = {k: v.read_text(encoding='utf-8') for k, v in files.items()}

def check(name, condition):
    assert condition, 'FAIL ' + name
    print('PASS ' + name)

check('Add address before saved address selector', text['checkout'].index('id="newAddressDetails"') < text['checkout'].index('id="checkoutAddress"'))
check('One address expandable control', text['checkout'].count('<summary') == 1)
check('Promotion banner before product list', text['menu'].index('ute-promo-before-menu') < text['menu'].index('id="uteCatalog"'))
check('Banner only if active DB promotion', 'promoDiscountPercent != null' in text['menu'])
check('Quotation and order use same calculation', len(re.findall('calculateVoucherDiscount\\(', text['backend'])) == 3)
check('Quote includes full order discount', 'plan.subtotal().subtract(discount).add(carrier.getFee())' in text['backend'])
check('Checkout stores discounted total', 'subtotal.subtract(o.getDiscountAmount()).add(o.getShippingFee())' in text['backend'])
check('Migration extends the whole-bill constraint', 'DiscountAmount<=Subtotal+ShippingFee' in text['migration'])
check('Receipt exposes promo code and discount', "o.voucherCode" in text['receipt'] and 'o.discountAmount' in text['receipt'])
check('Voucher DTO validates percent discount', 'isDiscountValid()' in text['dto'])

if shutil.which('javac') and shutil.which('java'):
    with tempfile.TemporaryDirectory() as tmp:
        source = Path(tmp) / 'CheckInvoice.java'
        source.write_text('''
import java.math.*;
import vn.edu.ute.milktea.service.market.VoucherDiscountCalculator;
class CheckInvoice {
  public static void main(String[] args) {
    BigDecimal subtotal = new BigDecimal("100000");
    BigDecimal deliveryFee = new BigDecimal("15000");
    BigDecimal discount = VoucherDiscountCalculator.discount("PERCENT", BigDecimal.ZERO, new BigDecimal("10"), subtotal, deliveryFee);
    BigDecimal total = subtotal.add(deliveryFee).subtract(discount);
    if(total.compareTo(new BigDecimal("103500"))!=0) throw new RuntimeException("Wrong discounted invoice: "+total);
    BigDecimal fixed = VoucherDiscountCalculator.discount("FIXED_AMOUNT", new BigDecimal("20000"), BigDecimal.ZERO, new BigDecimal("15000"), new BigDecimal("15000"));
    if(fixed.compareTo(new BigDecimal("15000"))!=0) throw new RuntimeException("Fixed voucher regression");
    System.out.println("PASS Calculated invoice 115000 - 11500 = "+total);
  }
}
''',encoding='utf-8')
        subprocess.run(['javac','-d',tmp,str(files['calculator']),str(source)],check=True)
        subprocess.run(['java','-cp',tmp,'CheckInvoice'],check=True)
else:
    print('SKIP standalone Java calculator test: Java compiler/runtime unavailable')
