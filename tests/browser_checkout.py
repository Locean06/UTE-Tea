#!/usr/bin/env python3
"""Chromium E2E against a disposable demo server on localhost:18080 only."""
from pathlib import Path
import shutil
from playwright.sync_api import sync_playwright, expect

BASE = 'http://localhost:18080'
with sync_playwright() as p:
    args = {'headless': True, 'args': ['--no-sandbox', '--disable-dev-shm-usage']}
    chrome = shutil.which('chromium') or shutil.which('chromium-browser') or shutil.which('google-chrome')
    if not chrome and Path('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome').exists():
        chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
    if chrome:
        args['executable_path'] = chrome
    browser = p.chromium.launch(**args)
    staff = p.request.new_context(base_url=BASE)
    csrf = staff.get('/api/v1/auth/csrf').json()['data']
    response = staff.post('/api/v1/auth/login', data={'email': 'admin@milktea.vn', 'password': 'Password@123'}, headers={csrf['headerName']: csrf['token']})
    assert response.status == 200
    table = staff.get('/api/v1/cashier/tables').json()['data'][0]
    catalogue = staff.get('/api/v1/public/market/catalog?q=Tr%C3%A0%20S%E1%BB%AFa%20Truy%E1%BB%81n%20Th%E1%BB%91ng&size=100').json()['data']['items']
    product = next(item for item in catalogue if item['name'] == 'Trà Sữa Truyền Thống' and item['size'] == 'M')
    failures = []
    for mode, method, width in [('TAKEAWAY', 'CASH', 1280), ('TAKEAWAY', 'BANK_TRANSFER', 390), ('DINE_IN', 'CASH', 390), ('DINE_IN', 'BANK_TRANSFER', 1280)]:
        context = browser.new_context(viewport={'width': width, 'height': 900})
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(BASE + '/cart' + ('?qr=' + table['qrCode'] if mode == 'DINE_IN' else ''), wait_until='networkidle')
        page.evaluate('async product => { await CustomerApp.initContext(); await CustomerApp.addToCart(product); }', product)
        # Cart controls remain editable inside the checkout form.
        page.locator('#cartList button').filter(has_text='+').click()
        expect(page.locator('#checkoutBtn')).to_be_enabled()
        assert '/cart' in page.url
        page.locator('#cartList button').filter(has_text='-').click()
        expect(page.locator('#checkoutBtn')).to_be_enabled()
        assert page.locator('#fulfillmentMode, #checkoutShop, #checkoutBranchHelp').count() == 0
        if mode == 'DINE_IN':
            expect(page.locator('#checkoutTable')).to_contain_text(table['name'])
        assert page.locator('#checkoutAddress, #checkoutCarrier, #deliveryFields').count() == 0
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), 'Horizontal overflow'
        assert page.locator('#checkoutVoucher, #applyCheckoutVoucher, #checkoutMethod').count() == 0
        expect(page.locator('#checkoutPaymentDialog')).not_to_be_visible()
        requests = []
        page.on('request', lambda request: requests.append(request.url) if request.method == 'POST' and request.url.endswith(('/orders/counter', '/orders/table')) else None)
        page.locator('#checkoutBtn').click()
        expect(page.locator('#checkoutPaymentDialog')).to_be_visible()
        assert not requests, 'Opening payment selection must not create an order'
        page.locator('#checkoutPaymentBack').click()
        expect(page.locator('#checkoutPaymentDialog')).not_to_be_visible()
        assert not requests, 'Cancelling payment selection must not create an order'
        page.locator('#checkoutBtn').click()
        page.locator('input[name="paymentMethod"][value="'+method+'"]').check()
        if mode == 'TAKEAWAY' and method == 'CASH':
            page.route('**/api/v1/orders/counter', lambda route: route.fulfill(status=503, content_type='application/json', body='{"success":false,"error":{"code":"INTERNAL_ERROR","message":"Lỗi thử gửi đơn. Vui lòng thử lại."}}'))
            page.locator('#checkoutPaymentConfirm').click()
            expect(page.locator('#checkoutPaymentError')).to_contain_text('Lỗi thử gửi đơn')
            expect(page.locator('#checkoutPaymentDialog')).to_be_visible()
            expect(page.locator('#checkoutPaymentConfirm')).to_be_enabled()
            page.unroute('**/api/v1/orders/counter')
            requests.clear()
        page.locator('#checkoutPaymentConfirm').click()
        page.wait_for_url('**/order-confirmation?order=*')
        assert len(requests) == 1, 'Payment confirmation must create exactly one order'
        expect(page.locator('#confirmationDetails')).to_be_visible()
        expect(page.locator('#confirmPayment')).to_have_text('Chưa xác nhận')
        expect(page.locator('#confirmBranch')).to_contain_text('UTE Tea Campus')
        if method == 'CASH':
            expect(page.locator('#confirmCashMessage')).to_be_visible()
        else:
            expect(page.locator('#confirmBankDetails')).to_contain_text('chưa cấu hình')
            page.locator('#confirmationTransferBtn').click()
            expect(page.locator('#confirmPayment')).to_have_text('Chưa xác nhận')
        if errors:
            failures.extend(errors)
        print(f'PASS {mode} + {method} ({width}px), pending payment, no delivery fields')
        context.close()
    # Missing SMTP must lead to the recoverable OTP screen, never an active-account success.
    context = browser.new_context()
    page = context.new_page()
    page.goto(BASE + '/login', wait_until='networkidle')
    page.locator('#register-tab').click()
    import uuid
    for selector, value in {'#regFullName':'Khách E2E','#regEmail':str(uuid.uuid4())+'@example.test','#regPhone':'0901234567','#regPassword':'Password@123','#regConfirmPassword':'Password@123'}.items():
        page.locator(selector).fill(value)
    page.locator('#registerBtn').click()
    expect(page.locator('#registerOtpContainer')).to_be_visible()
    expect(page.locator('#otpAlert')).to_contain_text('Chưa gửi được email')
    print('PASS registration SMTP failure opens recoverable OTP screen')
    context.close()
    assert not failures, failures
    staff.dispose()
    browser.close()
