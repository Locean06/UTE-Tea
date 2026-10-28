from pathlib import Path
from playwright.sync_api import sync_playwright
import json
import os
root=Path(__file__).resolve().parents[2]
html=(root/'src/main/resources/templates/cashier/index.html').read_text()
html=html.split('<th:block th:fragment="scripts">')[0]+'</body></html>'
js=(root/'src/main/resources/static/js/cashier/cashier.js').read_text()
mock='''
window.logCalls=[];
window.mockErrorPaymentOnce=false;
window.mockBanks={bankName:'MB Bank',bankAccountNumber:'0934197505',bankAccountHolder:'KIEN NGUYEN XUAN LUU'};
window.MilkTeaReceipts={esc:s=>String(s||'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;'),code:id=>'DH'+id,open:id=>{window.printed=id}};
window.MilkTeaRealtime={connect:()=>{},on:()=>{},onReconnect:()=>{}};
window.bootstrap={Modal:{getOrCreateInstance:el=>({show(){el.dataset.visible='true'},hide(){el.dataset.visible='false'}}),getInstance:()=>null}};
window.MilkTeaApi={formatVND:n=>Number(n||0).toLocaleString('vi-VN')+' ₫',showToast:(text,type)=>{window.logCalls.push(['toast',text,type])},
 get: async url=>{window.logCalls.push(['get',url]);if(url.includes('public/market/shops'))return [{id:1,name:'Bình Thạnh',active:true},{id:2,name:'Gò Vấp',active:true}];
 if(url.includes('public/shop-info'))return window.mockBanks;
 if(url.includes('cashier/orders?tab=confirm'))return [{orderId:133,status:'CHO_XAC_NHAN',staffCreated:false,pickupShopName:'Gò Vấp',customerName:'A',totalAmount:28000}];
 if(url.includes('cashier/tables'))return [];return []},
 post: async (url,body,headers)=>{window.logCalls.push(['post',url,body,headers]);
 if(url.includes('/quote')) return {subtotal:28000,discountAmount:0,totalAmount:28000};
 if(url==='/api/v1/orders/counter')return {orderId:91,totalAmount:28000,subtotal:28000,discountAmount:0,pickupShopName:'Bình Thạnh'};
 if(url.includes('/payments')){if(window.mockErrorPaymentOnce){window.mockErrorPaymentOnce=false;throw Error('Kết nối thanh toán lỗi')}return {paid:true}}
 return {};}}
const holder=document.getElementById('posProductsData');
const item=document.createElement('span');
item.dataset.id='72';item.dataset.name='Bạc Xỉu';item.dataset.size='M';item.dataset.price='28000';item.dataset.image='/none.png';item.dataset.category='1';item.dataset.categoryName='Cà phê';item.dataset.categoryAllowTopping='true';
holder.replaceChildren(item);
'''
with sync_playwright() as p:
 b=p.chromium.launch(headless=True,**({'executable_path':os.environ['CHROMIUM_EXECUTABLE']} if os.environ.get('CHROMIUM_EXECUTABLE') else {}),args=['--no-sandbox'])
 page=b.new_page()
 errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.set_content(html)
 page.evaluate(mock)
 page.add_script_tag(content=js)
 page.evaluate('CashierApp.init()')
 assert page.locator('#posShopId option').count()==3
 assert page.locator('#counterSubmitBtn').is_disabled()
 assert page.locator('#posPickupPendingCount').inner_text()=='1'
 assert page.locator('#posBankQr').is_hidden()
 page.select_option('#posShopId','1')
 page.evaluate("CashierApp.addCounterCart({id:72,name:'Bạc Xỉu',size:'M',price:28000,imageUrl:'',categoryName:'Cà phê',allowTopping:true})")
 page.wait_for_timeout(550)
 assert page.locator('#counterSubmitBtn').is_enabled()
 page.fill('#counterCashReceived','50000')
 assert '22.000' in page.locator('#posChangeDue').inner_text()
 page.evaluate('CashierApp.submitCounterOrder()')
 page.wait_for_timeout(150)
 posts=page.evaluate("window.logCalls.filter(x=>x[0]==='post')")
 assert [x for x in posts if x[1]=='/api/v1/orders/counter'][0][2]['shopId']==1
 assert len([x for x in posts if '/payments' in x[1]])==1
 assert page.locator('#posPaymentSuccessModal').get_attribute('data-visible')=='true'
 assert page.locator('#posSuccessBranch').inner_text()=='Bình Thạnh'
 assert page.locator('#counterItemCount').inner_text()=='(0)'
 assert page.locator('#counterSubmitBtn').is_disabled()
 assert not errors,errors
 print('PASS: chi nhánh bắt buộc, quote, tiền thối, tạo đơn theo shopId, ghi thu, popup và reset giỏ')
 # transfer account settings: correct bank name should reveal image after choosing bank
 page.select_option('#counterPayMethod','BANK_TRANSFER')
 assert page.locator('#posBankQr').is_visible()
 assert '0934197505' in page.locator('#posBankAccount').inner_text()
 page.evaluate("window.mockBanks={bankName:'VPBank',bankAccountNumber:'123',bankAccountHolder:'Tester'}")
 page.evaluate('CashierApp.init()') # reinit only for test, refresh bank setting
 assert page.locator('#posBankQr').is_hidden()
 print('PASS: hiển thị QR đúng STK, ẩn QR khi admin đổi STK')
 b.close()
