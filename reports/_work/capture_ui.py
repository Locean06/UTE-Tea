from pathlib import Path
import json,shutil,time,uuid,urllib.parse
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'reports/Anh_Giao_Dien';BASE='http://localhost:18081'
shots=[];events=[]
with sync_playwright() as p:
 args={'headless':True,'args':['--no-sandbox','--disable-dev-shm-usage']}
 chrome=shutil.which('chromium') or shutil.which('google-chrome')
 if not chrome and Path('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome').exists():chrome='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
 if chrome:args['executable_path']=chrome
 browser=p.chromium.launch(**args)
 def context(role=None):
  c=browser.new_context(viewport={'width':1366,'height':920},device_scale_factor=1)
  if role:
   csrf=c.request.get(BASE+'/api/v1/auth/csrf').json()['data']
   r=c.request.post(BASE+'/api/v1/auth/login',data={'email':role+'@milktea.vn','password':'Password@123'},headers={csrf['headerName']:csrf['token']})
   assert r.status==200,('login',role,r.status)
  return c
 def api(c,path,data=None):
  if data is None:
   r=c.request.get(BASE+path)
  else:
   csrf=c.request.get(BASE+'/api/v1/auth/csrf').json()['data']
   r=c.request.post(BASE+path,data=data,headers={csrf['headerName']:csrf['token'],'Idempotency-Key':str(uuid.uuid4())})
  events.append({'path':path,'status':r.status})
  if r.status>=400:raise RuntimeError((path,r.status,r.text()[:180]))
  return r.json().get('data')
 admin=context('admin');customer=context('customer');cashier=context('cashier');kitchen=context('kitchen');public=context()
 product=next(x for x in api(public,'/api/v1/public/market/catalog?q='+urllib.parse.quote('Trà Sữa Truyền Thống')+'&size=100')['items'] if x['name']=='Trà Sữa Truyền Thống' and x['size']=='M')
 table=api(admin,'/api/v1/cashier/tables')[0]
 def create(method='CASH',dine=False):
  body={'shopId':1,'paymentMethod':method,'items':[{'productId':product['id'],'quantity':1,'note':'Ít đá'}]}
  path='/api/v1/orders/counter'
  if dine:body.update(tableId=table['id'],qrCode=table['qrCode']);path='/api/v1/orders/table'
  return api(customer,path,body)
 done=create();api(admin,f"/api/v1/cashier/orders/{done['orderId']}/confirm",{})
 api(admin,f"/api/v1/cashier/orders/{done['orderId']}/payments",{'method':'CASH'})
 api(kitchen,f"/api/v1/kitchen/orders/{done['orderId']}/start",{})
 api(kitchen,f"/api/v1/kitchen/orders/{done['orderId']}/complete",{})
 tableOrder=create('BANK_TRANSFER',True);api(admin,f"/api/v1/cashier/orders/{tableOrder['orderId']}/confirm",{})
 pending=create('BANK_TRANSFER');api(customer,f"/api/v1/orders/{pending['orderId']}/payment-notices",{'method':'BANK_TRANSFER','reference':'CUSTOMER_TRANSFERRED_'+str(pending['orderId'])})
 def shot(c,key,title,path,front,purpose,components,actions,setup=None,group='Khách hàng',note=''):
  page=c.new_page();response=page.goto(BASE+path,wait_until='networkidle');assert response and response.status<400,(path,response.status if response else None)
  page.wait_for_timeout(900)
  if setup:setup(page)
  page.wait_for_timeout(350)
  assert 'Trang đang tạm thời gián đoạn' not in page.locator('body').inner_text(),path
  page.screenshot(path=str(OUT/(key+'.png')),full_page=False)
  shots.append(dict(key=key,title=title,path=path.split('?qr=')[0],front=front,purpose=purpose,components=components,actions=actions,group=group,note=note))
  page.close();print('CAPTURED',key,flush=True)
 shot(public,'01_dang_nhap','Đăng nhập','/login','templates/auth/login.html','Xác thực tài khoản và chuyển vào chức năng phù hợp vai trò.','Tab đăng nhập/đăng ký, email, mật khẩu, quên mật khẩu.','Nhập thông tin và gửi đăng nhập; chuyển sang đăng ký hoặc khôi phục.')
 shot(public,'02_dang_ky','Đăng ký tài khoản','/login','templates/auth/login.html','Khởi tạo tài khoản khách chờ xác thực email.','Họ tên, email, số điện thoại, mật khẩu và nhập lại mật khẩu.','Nhập dữ liệu; kiểm tra định dạng; gửi đăng ký.',setup=lambda q:q.locator('#register-tab').click())
 shot(public,'03_quen_mat_khau','Yêu cầu khôi phục mật khẩu','/login','templates/auth/login.html','Yêu cầu mã xác minh khi quên mật khẩu.','Hộp thoại email khôi phục và nút gửi mã.','Nhập email, gửi yêu cầu hoặc quay lại.',setup=lambda q:q.get_by_text('Quên mật khẩu',exact=False).first.click())
 shot(customer,'04_trang_chu','Trang chủ và thực đơn','/','templates/customer/menu.html','Truy cập menu chính và lựa chọn món.','Banner thương hiệu, danh mục, thẻ món, giá và thanh điều hướng.','Lọc/tìm món, mở hộp tùy chọn và xem giỏ.')
 shot(customer,'05_catalogue','Danh sách món và bộ lọc','/marketplace','templates/customer/marketplace.html; static/js/customer/marketplace.js','Tra cứu menu dùng chung và các biến thể.','Ô tìm kiếm, sắp xếp, lọc giá/danh mục, grid món và phân trang.','Thay bộ lọc; xem đánh giá; thêm món vào giỏ.')
 shot(customer,'06_chon_mon','Chọn size, topping và ghi chú','/cart','templates/fragments/product-options.html; static/js/customer/menu-selection.js','Cấu hình món trước khi ghi vào giỏ.','Tên món, size, số lượng, topping được phép và ghi chú.','Chọn tùy chọn, chỉnh số lượng và thêm vào giỏ.',setup=lambda q:q.evaluate('id=>MenuSelection.open(id)',product['id']))
 csrf=customer.request.get(BASE+'/api/v1/auth/csrf').json()['data'];saved=api(customer,'/api/v1/account/cart')
 r=customer.request.put(BASE+'/api/v1/account/cart',data={'items':[{'productId':product['id'],'quantity':2,'note':'Ít đường','toppingIds':[]}]},headers={csrf['headerName']:csrf['token'],'If-Match':saved['version']});assert r.status==200
 shot(customer,'07_gio_hang','Giỏ hàng và kiểm tra tổng tiền','/cart','templates/customer/cart.html; static/js/customer/delivery-checkout.js','Kiểm tra món và tổng tiền do backend tính.','Danh sách món, tăng/giảm/xóa/sửa, tạm tính, giảm giá chung và Gửi đơn hàng.','Chỉnh giỏ và bấm Gửi đơn; chưa chọn phương thức ở form chính.','',note='Checkout hiện tại không có ô chọn hình thức, cơ sở hoặc nhập mã giảm giá.')
 shot(customer,'08_chon_thanh_toan','Hộp chọn phương thức thanh toán','/cart','templates/fragments/delivery-checkout.html','Chọn tiền mặt hoặc chuyển khoản sau khi gửi giỏ.','Dialog, tổng thanh toán, hai phương thức, Quay lại và Xác nhận gửi đơn.','Chọn phương thức và xác nhận; quay lại chưa tạo đơn.',setup=lambda q:q.locator('#checkoutBtn').click())
 shot(customer,'09_ban_qr','Checkout theo bàn đã quét QR','/cart?qr='+table['qrCode'],'templates/customer/cart.html; static/js/customer/customer.js','Tự xác định ngữ cảnh bàn hợp lệ.','Tên bàn, giỏ món, tổng tiền và thao tác gửi đơn.','Sửa giỏ và gửi đơn theo đúng bàn.','',note='Ảnh không hiển thị URL hay chuỗi QR/token.')
 shot(customer,'10_lich_su','Lịch sử đơn của khách','/orders','templates/customer/orders.html; static/js/order/orders.js','Theo dõi các đơn của tài khoản đăng nhập.','Mã đơn, món, trạng thái, tiền, các thao tác theo điều kiện.','Xem chi tiết, theo dõi, yêu cầu hủy/đánh giá khi đủ điều kiện.')
 shot(customer,'11_chung_tu','Xác nhận đơn và thông tin chuyển khoản','/order-confirmation?order='+str(pending['orderId']),'templates/customer/order-confirmation.html','Xem chứng từ riêng của đơn đã tạo.','Mã đơn, trạng thái, tổng tiền, món và thông tin thanh toán.','Thông báo chuyển khoản để thu ngân kiểm tra; không tự làm PAID.')
 shot(customer,'12_ho_so','Thông tin tài khoản','/profile','templates/profile.html','Quản lý hồ sơ cá nhân.','Họ tên, email, điện thoại, avatar, đổi mật khẩu.','Sửa hồ sơ và gửi cập nhật; sử dụng chức năng mật khẩu tương ứng.')
 shot(customer,'13_tuy_chon','Yêu thích, món đã xem và địa chỉ lưu','/api/v1/account/preferences','templates/customer/account-data.html','Tra cứu các dữ liệu bổ trợ theo tài khoản.','Các nhóm yêu thích, lịch sử xem và địa chỉ đã lưu.','Bật/tắt yêu thích; xem món; sửa/xóa địa chỉ.','',note='Địa chỉ là dữ liệu tương thích; không bắt buộc trong checkout mới.')
 shot(cashier,'14_thu_ngan','Bảng điều khiển thu ngân','/cashier','templates/cashier/index.html; static/js/cashier/cashier.js','Tiếp nhận và xử lý đơn theo cơ sở được phân công.','Các tab công việc, mã đơn, phương thức/trạng thái tiền, bàn và hành động.','Xác nhận khách, ghi thu, xem chứng từ và xử lý yêu cầu.',group='Thu ngân')
 shot(kitchen,'15_bep','Hàng đợi chế biến','/kitchen','templates/kitchen/orders.html; static/js/kitchen/kitchen.js','Thực hiện các đơn được điều phối vào bếp.','Mã đơn, bàn, món, trạng thái và nút bắt đầu/hoàn tất.','Bắt đầu, hoàn tất, xem hoặc ghi sự cố theo UI.',group='Nhân viên bếp')
 for key,title,path,purpose,components,actions,front in [
 ('16_admin_bao_cao','Dashboard và báo cáo','/admin/reports','Tổng hợp dữ liệu kinh doanh thử nghiệm.','Bộ lọc ngày, KPI, biểu đồ, top sản phẩm và cảnh báo.','Lọc thời gian, xem số liệu và các nhóm thống kê.','templates/admin/reports.html; static/js/admin/dashboard.js'),
 ('17_admin_mon','Quản lý danh mục và món','/admin/catalog','Quản lý menu, giá và trạng thái phục vụ.','Bảng danh mục/món, bộ lọc, nút tạo/sửa và cấu hình công thức.','Tra cứu, tạo/sửa món hoặc mở cấu hình.','templates/admin/catalog.html'),
 ('18_admin_nhan_vien','Quản lý nhân viên','/admin/staff','Quản lý tài khoản vận hành.','Danh sách nhân viên, thông tin vai trò và trạng thái.','Tạo/sửa, khóa/mở và thao tác được quyền.','templates/admin/staff.html'),
 ('19_admin_ban','Quản lý bàn','/admin/tables','Duy trì bàn và lượt phục vụ.','Tên bàn, trạng thái, phiên hiện tại và nút thao tác.','Thêm/sửa bàn; các chức năng QR thuộc quản trị.','templates/admin/tables.html'),
 ('20_admin_don','Quản lý đơn và thu tiền','/admin/orders','Theo dõi đơn toàn phạm vi quản trị.','Đơn, trạng thái, hóa đơn và các thao tác theo điều kiện.','Lọc, xem chi tiết, xử lý hủy/hoàn theo quyền.','templates/admin/orders.html'),
 ('21_admin_kho','Nguyên liệu và tồn kho','/admin/inventory','Kiểm soát nguyên liệu tại KHO/BEP.','Danh sách tồn, ngưỡng, định mức, giá vốn và tác vụ kho.','Lọc kho, nhập, điều chỉnh hoặc xem công thức.','templates/admin/inventory.html'),
 ('22_admin_chung_tu','Chứng từ vận hành kho','/admin/documents','Tra cứu nhập, sơ chế và kiểm kê/kết ca.','Các nhóm chứng từ, bộ lọc ngày và dữ liệu chi tiết.','Đổi nhóm, lọc thời gian và xem phiếu.','templates/admin/documents.html'),
 ('23_admin_cai_dat','Cài đặt hệ thống','/admin/settings','Quản lý thương hiệu và chính sách chung.','Tên/địa chỉ, logo, cấu hình giảm giá và tài khoản nhận tiền.','Sửa và lưu cấu hình; upload logo khi dịch vụ hợp lệ.','templates/admin/settings.html'),
 ('24_portal_admin','Quản lý cơ sở và phân công','/merchant','Quản lý module cơ sở và dữ liệu mở rộng.','Chọn Shop, cấu hình, tài khoản, phân công và nhóm nghiệp vụ cũ.','Tạo/sửa cơ sở, gán quản lý/thu ngân/bếp và quản lý dữ liệu trong scope.','templates/market/portal.html')]:
  shot(admin,key,title,path,front,purpose,components,actions,group='Quản trị viên')
 for role,email,key in [('VENDOR','vendor1@ute-tea.demo','25_vendor'),('MANAGER','manager@ute-tea.demo','26_manager'),('SHIPPER','shipper@ute-tea.demo','27_shipper')]:
  c=browser.new_context(viewport={'width':1366,'height':920});csrf=c.request.get(BASE+'/api/v1/auth/csrf').json()['data'];r=c.request.post(BASE+'/api/v1/auth/login',data={'email':email,'password':'Password@123'},headers={csrf['headerName']:csrf['token']});assert r.status==200
  shot(c,key,'Cổng nghiệp vụ '+role,'/merchant','templates/market/portal.html; static/js/market/portal.js','Hiển thị đúng phần chức năng cho vai trò mở rộng hiện có.','Các nhóm công việc có data-roles; phạm vi Shop/đơn theo service.','Thao tác trong scope được phân công; dữ liệu giao hàng là module cũ.',group='Vai trò cơ sở và phân hệ mở rộng',note='Giao diện có thật; không đồng nghĩa checkout hiện tại tạo mới đơn giao hàng.')
  c.close()
 for c in [admin,customer,cashier,kitchen,public]:c.close()
 browser.close()
(OUT/'manifest.json').write_text(json.dumps(shots,ensure_ascii=False,indent=2))
(ROOT/'reports/_work/screenshot_api_checks.json').write_text(json.dumps(events,ensure_ascii=False,indent=2))
print('COMPLETE',len(shots))
