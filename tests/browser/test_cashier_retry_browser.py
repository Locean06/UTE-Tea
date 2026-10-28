from pathlib import Path
import os
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[2]
html=(root/'src/main/resources/templates/cashier/index.html').read_text().split('<th:block th:fragment="scripts">')[0]+'</body></html>'
js=(root/'src/main/resources/static/js/cashier/cashier.js').read_text()
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True, **({'executable_path':os.environ['CHROMIUM_EXECUTABLE']} if os.environ.get('CHROMIUM_EXECUTABLE') else {}),args=['--no-sandbox'])
 page=browser.new_page()
 errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.set_content(html)
 page.evaluate('''
 window.calls=[];window.failPayment=true;
 window.MilkTeaReceipts={esc:s=>String(s||''),code:n=>'DH'+n,open:()=>{}};
 window.MilkTeaRealtime={connect:()=>{},on:()=>{},onReconnect:()=>{}};
 window.bootstrap={Modal:{getOrCreateInstance:el=>({show(){el.dataset.visible='true'},hide(){}})}};
 window.MilkTeaApi={formatVND:n=>Number(n||0).toLocaleString('vi-VN')+' ₫',showToast:(...args)=>window.calls.push(['toast',...args]),
 get:async url=>url.includes('public/market/shops')?[{id:7,active:true,name:'Thủ Đức'}]:url.includes('public/shop-info')?{bankName:'MB Bank',bankAccountNumber:'0934197505',bankAccountHolder:'KIEN NGUYEN XUAN LUU'}:[],
 post:async(url,body,headers)=>{window.calls.push(['post',url,body,headers]);
 if(url.includes('/quote'))return {subtotal:45000,discountAmount:0,totalAmount:45000};
 if(url==='/api/v1/orders/counter')return {orderId:100,totalAmount:45000,subtotal:45000,pickupShopName:'Thủ Đức'};
 if(url.includes('/payments')) {if(window.failPayment){window.failPayment=false;throw Error('Mất mạng khi ghi thu')}return {paid:true}}
 return {};
 }};
 const el=document.createElement('span');el.dataset.id='1';el.dataset.name='Trà sữa';el.dataset.size='M';el.dataset.price='45000';el.dataset.category='1';el.dataset.categoryName='Trà sữa';document.getElementById('posProductsData').append(el);
 ''')
 page.add_script_tag(content=js)
 page.evaluate('CashierApp.init()')
 assert page.locator('#posShopId').input_value()=='7'
 page.evaluate("CashierApp.addCounterCart({id:1,name:'Trà sữa',size:'M',price:45000,categoryName:'Trà sữa'})")
 page.wait_for_timeout(450)
 page.select_option('#counterPayMethod','BANK_TRANSFER')
 # cannot submit without transfer acknowledgement
 page.evaluate('CashierApp.submitCounterOrder()')
 assert len(page.evaluate("window.calls.filter(x=>x[0]==='post'&&x[1]==='/api/v1/orders/counter')"))==0
 page.locator('#counterTransferConfirmed').check()
 page.evaluate('CashierApp.submitCounterOrder()')
 page.wait_for_timeout(80)
 assert page.locator('#posShopId').is_disabled()
 assert len(page.evaluate("window.calls.filter(x=>x[0]==='post'&&x[1]==='/api/v1/orders/counter')"))==1
 assert len(page.evaluate("window.calls.filter(x=>x[0]==='post'&&x[1].includes('/payments'))"))==1
 page.evaluate('CashierApp.submitCounterOrder()')
 page.wait_for_timeout(80)
 assert len(page.evaluate("window.calls.filter(x=>x[0]==='post'&&x[1]==='/api/v1/orders/counter')"))==1
 payment_calls=page.evaluate("window.calls.filter(x=>x[0]==='post'&&x[1].includes('/payments'))")
 assert len(payment_calls)==2
 assert payment_calls[0][3]['Idempotency-Key']==payment_calls[1][3]['Idempotency-Key']
 assert page.locator('#posPaymentSuccessModal').get_attribute('data-visible')=='true'
 assert not errors,errors
 print('PASS: QR, bank confirmation required, after payment network error retry same order/payment idempotency key, success popup')
 browser.close()
