"""Static regression contract for the four order pages (no live server required)."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
cp = (root/'src/main/java/vn/edu/ute/milktea/controller/CustomerPageController.java').read_text()
sp = (root/'src/main/java/vn/edu/ute/milktea/controller/StaffPageController.java').read_text()
er = (root/'src/main/java/vn/edu/ute/milktea/common/GlobalExceptionHandler.java').read_text()
sec = (root/'src/main/java/vn/edu/ute/milktea/security/SecurityConfig.java').read_text()

for path, method, template, text in [
    ('/orders', 'ordersPage', 'customer/orders', cp),
    ('/my-orders', 'myOrdersPage', 'customer/my-orders', cp),
    ('/cashier/delivery-orders', 'cashierDeliveryPage', 'cashier/delivery-orders', sp),
    ('/admin/orders', 'adminOrdersPage', 'admin/orders', sp),
]:
    assert f'@GetMapping("{path}")' in text, path
    assert f'return "{template}";' in text, template
    file = root/'src/main/resources/templates'/f'{template}.html'
    assert file.is_file(), file
    methodbody = re.search(r'public String ' + method + r'\([^)]*\).*?\n    }', text, re.S)
    assert methodbody, method
    assert 'settingsRepository.findById' not in methodbody.group(0), method
    print('OK route / template / optional-settings independence:', path)

assert '.requestMatchers("/orders").hasAnyRole("ADMIN", "CASHIER")' in sec
assert '.requestMatchers("/cashier", "/cashier/**").hasAnyRole("ADMIN", "CASHIER")' in sec
assert 'NoResourceFoundException.class' in er and 'HttpStatus.NOT_FOUND' in er
print('OK staff-only guards and actual 404 handling')
