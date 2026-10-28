#!/usr/bin/env python3
"""Standalone checkout browser regression (mocked APIs; no SQL Server).
Run: python tests/browser_voucher_simplified.py
"""
from pathlib import Path
from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
fragment = (ROOT/'src/main/resources/templates/fragments/delivery-checkout.html').read_text(encoding='utf-8')
section = str(BeautifulSoup(fragment,'html.parser').find(id='branchCheckout'))
html = ('<!doctype html><html><head><meta charset="utf-8"/></head><body data-account-id="42">'
        '<div id="cartSummary"></div>'+section+'</body></html>')

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox'])
    page = browser.new_page(viewport={'width':390,'height':844})
    page.set_content(html)
    page.add_script_tag(content="""
        window.testCalls = { quotes:[], orders:[] };
        window.CustomerApp={ initContext:async()=>{},getLocalCart:()=>[{productId:2,quantity:2}],clearCart:async()=>{} };
        window.MilkTeaApi={
            formatVND:n=>Number(n).toLocaleString('vi-VN')+' ₫',
            get:async url=>{
                if(url==='/api/v1/public/market/carriers')return [{id:5,name:'Nhân viên UTE Tea nội bộ',fee:15000,shopId:12}];
                if(url==='/api/v1/account/addresses')return [{id:10,recipient:'Khách A',phone:'0900000000',addressLine:'Q. Thủ Đức',defaultAddress:true}];
                if(url.includes('/api/v1/account/cart/branches'))return [{id:12,name:'UTE Tea - Thủ Đức',address:'Thủ Đức',canServe:true,issues:[],nearest:true,distanceKm:0.3}];
                throw Error('Unknown GET '+url);
            },
            post:async (url,input,headers)=>{
                if(url==='/api/v1/account/cart/quote'){
                    window.testCalls.quotes.push(input);
                    if(input.voucherCode==='SAIMAAA')throw Error('Mã giảm giá không tồn tại');
                    if(input.voucherCode==='HETHAN')throw Error('Mã hết hạn, hết lượt hoặc không đủ điều kiện áp dụng');
                    if(input.voucherCode==='SERVER500'){const e=new Error('Đã xảy ra lỗi hệ thống');e.status=500;e.traceId='test-trace-123';throw e;}
                    const subtotal=100000,shippingFee=15000;
                    const discountAmount=input.voucherCode==='GIAMGIA10' ? 11500:0;
                    return {shopId:12,cartVersion:'v123',subtotal,shippingFee,discountAmount,totalAmount:subtotal+shippingFee-discountAmount,items:[]};
                }
                if(url==='/api/v1/market/orders'){
                    window.testCalls.orders.push(input);
                    return {id:77,shopName:'UTE Tea - Thủ Đức',totalAmount:input.expectedTotal,paymentMethod:input.paymentMethod};
                }
                throw Error('Unknown POST '+url);
            }
        };
    """)
    page.add_script_tag(path=str(ROOT/'src/main/resources/static/js/customer/delivery-checkout.js'))
    page.evaluate('document.dispatchEvent(new Event("DOMContentLoaded"))')
    page.wait_for_function('document.getElementById("deliverySubmit").disabled === false')
    assert '115.000' in page.locator('#branchQuote').inner_text()
    assert page.locator('#checkoutMessage').is_hidden()
    print('PASS: default address/branch selected, no voucher = 115000 VND')

    page.locator('#checkoutVoucher').fill('giamgia10')
    assert page.locator('#deliverySubmit').is_disabled()
    assert 'Bấm Áp dụng' in page.locator('#branchQuote').inner_text()
    page.locator('#applyCheckoutVoucher').click()
    page.wait_for_function('document.getElementById("deliverySubmit").disabled === false')
    assert '103.500' in page.locator('#branchQuote').inner_text()
    assert '11.500' in page.locator('#checkoutVoucherNotice').inner_text()
    assert page.locator('#checkoutVoucher').input_value()=='GIAMGIA10'
    print('PASS: GIAMGIA10 discounts (100000 + 15000) by 10% => 103500')

    page.locator('#checkoutVoucher').fill('SAIMAAA')
    assert page.locator('#deliverySubmit').is_disabled()
    page.locator('#applyCheckoutVoucher').click()
    page.wait_for_function('document.getElementById("checkoutVoucherNotice").textContent.includes("không tồn tại")')
    assert page.locator('#deliverySubmit').is_disabled()
    assert page.locator('#checkoutMessage').is_hidden()
    print('PASS: invalid voucher blocks checkout and shows one local message')

    page.locator('#checkoutVoucher').fill('HETHAN')
    page.locator('#applyCheckoutVoucher').click()
    page.wait_for_function('document.getElementById("checkoutVoucherNotice").textContent.includes("hết hạn")')
    assert page.locator('#deliverySubmit').is_disabled()
    print('PASS: expired voucher blocks checkout')

    page.locator('#checkoutVoucher').fill('SERVER500')
    page.locator('#applyCheckoutVoucher').click()
    page.wait_for_function('document.getElementById("checkoutMessage").textContent.includes("test-trace-123")')
    assert 'Không thể kiểm tra mã' in page.locator('#checkoutVoucherNotice').inner_text()
    assert page.locator('#deliverySubmit').is_disabled()
    print('PASS: server error shows trace ID instead of labeling voucher invalid')

    page.locator('#checkoutVoucher').fill('')
    page.locator('#applyCheckoutVoucher').click()
    page.wait_for_function('document.getElementById("deliverySubmit").disabled === false')
    assert '115.000' in page.locator('#branchQuote').inner_text()
    page.locator('#deliverySubmit').click()
    assert page.locator('#deliveryPaymentDialog').evaluate('(x)=>x.open')
    assert page.evaluate('testCalls.orders.length')==0
    page.locator('input[name="paymentMethod"][value="COD"]').check()
    page.locator('#deliveryPaymentConfirm').click()
    page.wait_for_function('testCalls.orders.length === 1')
    assert page.evaluate('testCalls.orders[0].expectedTotal')==115000
    print('PASS: no order before confirmation, successful COD order preserves quoted total')
    assert not page.evaluate('document.documentElement.scrollWidth>window.innerWidth'), 'mobile overflow'
    browser.close()
print('BROWSER VOUCHER REGRESSION: PASS (API mocked)')
