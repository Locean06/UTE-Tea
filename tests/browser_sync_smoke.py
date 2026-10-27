"""Browser-level tests with mocked API responses; not a replacement for SQL Server integration tests."""
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]


def run():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, executable_path='/usr/bin/chromium', args=['--no-sandbox'])
        page = browser.new_page(viewport={"width": 390, "height": 844})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))

        page.set_content('<ul id="uteFooterBranches"></ul>')
        page.evaluate('''window.MilkTeaApi = {get: async () => [
          {id:1, name:'UTE Tea - Thủ Đức', address:'Địa chỉ mẫu Thủ Đức'},
          {id:2, name:'UTE Tea - Bình Thạnh (Demo)', address:'Địa chỉ mẫu Bình Thạnh'},
          {id:3, name:'UTE Tea - Gò Vấp (Demo)', address:'Địa chỉ mẫu Gò Vấp'}
        ]};''')
        page.add_script_tag(content=(ROOT/'src/main/resources/static/js/common/footer-branches.js').read_text(encoding='utf-8'))
        page.wait_for_function('document.querySelectorAll("#uteFooterBranches li").length === 3')
        assert 'Thủ Đức' in page.locator('#uteFooterBranches').inner_text()
        assert 'Gò Vấp' in page.locator('#uteFooterBranches').inner_text()
        assert 'Số 1 Võ Văn Ngân' not in page.locator('#uteFooterBranches').inner_text()
        print('PASS: footer reflects active Shop API, not stale global settings')

        page.set_content('''
          <div id="posDeliveryNewCount">0</div>
          <div id="posDeliveryNewList"></div>
          <div id="deliveryInboxList"></div>
          <div id="deliveryInboxError" class="d-none"></div>
          <div id="deliveryStatNew"></div><div id="deliveryStatConfirmed"></div>
          <div id="deliveryStatShipping"></div><div id="deliveryStatDelivered"></div>
          <input id="deliveryInboxSearch" />
          <select id="deliveryInboxStatus"><option value="">All</option></select>
          <button id="deliveryInboxRefresh"></button>
        ''')
        page.evaluate('''
          window.__deliveryRows=[]; window.__fetchCount=0; window.__eventListeners={};
          window.MilkTeaApi={
            get:async function(url){window.__fetchCount++;
              return url.endsWith('/shippers') ? [{id:42,name:'Shipper test'}] : window.__deliveryRows;},
            formatVND:(x)=>`${x} đ`, put:async()=>({})
          };
          window.MilkTeaRealtime={
            on:(type,cb)=>window.__eventListeners[type]=cb,
            onReconnect:()=>{},connect:()=>{}
          };
          window.__deliveryRows=[{id:101,shopName:'UTE Tea - Thủ Đức',status:'NEW',
             recipient:'Khách thử',phone:'0900000000',addressLine:'Demo',totalAmount:55000,
             paymentMethod:'COD',paid:false,items:[],version:0}];
        ''')
        page.add_script_tag(content=(ROOT/'src/main/resources/static/js/cashier/delivery-inbox.js').read_text(encoding='utf-8'))
        page.evaluate('document.dispatchEvent(new Event("DOMContentLoaded"));')
        page.wait_for_function('document.getElementById("posDeliveryNewCount").textContent === "1"')
        assert '#101' in page.locator('#posDeliveryNewList').inner_text()
        page.evaluate('''
          window.__deliveryRows=[{id:101,shopName:'UTE Tea - Thủ Đức',status:'CONFIRMED',
             recipient:'Khách thử',phone:'0900000000',addressLine:'Demo',totalAmount:55000,
             paymentMethod:'COD',paid:false,items:[],version:1}];
          window.__eventListeners.DELIVERY_ORDER_CHANGED({type:'DELIVERY_ORDER_CHANGED'});
        ''')
        page.wait_for_function('document.getElementById("posDeliveryNewCount").textContent === "0"')
        assert 'Đã xác nhận' in page.locator('#deliveryInboxList').inner_text()
        assert not errors, errors
        print('PASS: cashier inbox loads DeliveryOrder and refreshes on commit event')
        browser.close()

if __name__ == '__main__':
    run()
