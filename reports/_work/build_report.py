from pathlib import Path
from copy import deepcopy
from collections import defaultdict,Counter
from PIL import Image,ImageDraw,ImageFont
from docx import Document
from docx.shared import Cm,Pt,RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from lxml import etree
import json,re,hashlib,zipfile,shutil,datetime
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'reports';WORK=OUT/'_work'
TEMPLATE=next(ROOT.glob('*.docx'))
F=json.loads((WORK/'features.json').read_text());E=json.loads((WORK/'entities.json').read_text());UI=json.loads((OUT/'Anh_Giao_Dien/manifest.json').read_text())
D=Document(TEMPLATE)
original_template_hash=hashlib.sha256(TEMPLATE.read_bytes()).hexdigest()
sections=[deepcopy(s._sectPr) for s in D.sections]
# Keep the original cover layout, logo position, student table, page borders and section setup.
cover_end=D.paragraphs[26]._p
children=list(D.element.body);idx=children.index(cover_end)
for el in children[idx+1:]:D.element.body.remove(el)
# Replace all unconfirmed administrative information, without inventing names or IDs.
texts={0:'[CẦN BỔ SUNG: CƠ QUAN CHỦ QUẢN]',1:'[CẦN BỔ SUNG: TÊN TRƯỜNG]',2:'[CẦN BỔ SUNG: KHOA / BỘ MÔN]',10:'BÁO CÁO ĐỒ ÁN',11:'[CẦN BỔ SUNG: TÊN HỌC PHẦN]',12:'XÂY DỰNG HỆ THỐNG BÁN VÀ',13:'QUẢN LÝ TRÀ SỮA UTE-TEA',15:'GIẢNG VIÊN HƯỚNG DẪN: [CẦN BỔ SUNG]',16:'LỚP: [CẦN BỔ SUNG]',3:'--------------------',26:'[CẦN BỔ SUNG: ĐỊA ĐIỂM], tháng 10 năm 2026'}
for i,t in texts.items():
 p=D.paragraphs[i]
 first=next((r for r in p.runs if r.text),None);rpr=deepcopy(first._r.rPr) if first is not None and first._r.rPr is not None else None
 for r in list(p._p):
  if r.tag!=qn('w:pPr'):p._p.remove(r)
 run=p.add_run(t)
 if rpr is not None:run._r.insert(0,rpr)
for i,row in enumerate(D.tables[0].rows):
 if i==0:continue
 for j,c in enumerate(row.cells):
  for p in c.paragraphs:
   for r in list(p._p):
    if r.tag!=qn('w:pPr'):p._p.remove(r)
  c.paragraphs[0].add_run('[CẦN BỔ SUNG: '+('HỌ TÊN' if j==0 else 'MSSV')+']')
# Compact empty lines below the student table so the date remains on the cover.
for index in range(18,26):
 D.paragraphs[index].paragraph_format.line_spacing=Pt(6)
 D.paragraphs[index].paragraph_format.space_after=Pt(0)
D.paragraphs[3].alignment=WD_ALIGN_PARAGRAPH.CENTER
# Keep the cover table compact with readable administrative placeholders.
cover_table=D.tables[0];cover_table.autofit=False
for column,width in zip(cover_table.columns,[7,4.5]):column.width=Cm(width)
for row in cover_table.rows:
 for cell,width in zip(row.cells,[7,4.5]):
  cell.width=Cm(width)
  for paragraph in cell.paragraphs:
   paragraph.paragraph_format.space_after=Pt(2)
   paragraph.paragraph_format.line_spacing=1.0
   for run in paragraph.runs:run.font.size=Pt(11)
# Cover administrative logo is unconfirmed. Preserve its dimensions using an explicit placeholder.
logo=D.paragraphs[4]._p.xpath('.//a:blip')[0];rid=logo.get(qn('r:embed'))
im=Image.new('RGB',(600,600),'white');dr=ImageDraw.Draw(im);dr.rectangle((90,90,510,510),outline='#777777',width=2)
font=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',30)
dr.multiline_text((300,300),'[CẦN BỔ SUNG]\nLOGO TRƯỜNG',font=font,fill='#555555',anchor='mm',align='center',spacing=20)
im.save(WORK/'logo_placeholder.png');D.part.related_parts[rid]._blob=(WORK/'logo_placeholder.png').read_bytes()
# Use the existing final section properties as insertion anchor. Intermediate section breaks are cloned.
D.element.body.append(deepcopy(sections[-1]))
# Headers and footers retain page layout and page fields; replace all template-specific content.
for rel in list(D.part.rels.values()):
 if rel.reltype.endswith('/header') or rel.reltype.endswith('/footer'):
  part=rel.target_part
  for child in list(part.element):part.element.remove(child)
  p=OxmlElement('w:p');ppr=OxmlElement('w:pPr');jc=OxmlElement('w:jc');jc.set(qn('w:val'),'center');ppr.append(jc);p.append(ppr)
  r=OxmlElement('w:r');rp=OxmlElement('w:rPr');sz=OxmlElement('w:sz');sz.set(qn('w:val'),'20');rp.append(sz);r.append(rp)
  if rel.reltype.endswith('/header'):
   t=OxmlElement('w:t');t.text='UTE-TEA — BÁO CÁO ĐỒ ÁN';r.append(t);p.append(r)
  else:
   p.append(r)
   for kind,text in [('begin',None),(None,' PAGE '),('separate',None),(None,'1'),('end',None)]:
    rr=OxmlElement('w:r')
    if kind:x=OxmlElement('w:fldChar');x.set(qn('w:fldCharType'),kind)
    elif text==' PAGE ':x=OxmlElement('w:instrText');x.set(qn('xml:space'),'preserve');x.text=text
    else:x=OxmlElement('w:t');x.text=text
    rr.append(x);p.append(rr)
  part.element.append(p)
# Retain the template's Times New Roman, hierarchical numbering and heading styles.
for n in ['Heading 1','Heading 2','Heading 3','Heading 4']:
 D.styles[n].font.name='Times New Roman';D.styles[n].font.color.rgb=RGBColor(0,0,0)
 D.styles[n].element.get_or_add_rPr().rFonts.set(qn('w:eastAsia'),'Times New Roman')
D.core_properties.title='UTE-TEA — Kiến trúc, sơ đồ hoạt động và giao diện hệ thống'
D.core_properties.subject='Báo cáo đồ án dựa trên mã nguồn UTE-Tea'
D.core_properties.author='[CẦN BỔ SUNG: NHÓM SINH VIÊN]';D.core_properties.last_modified_by='UTE-TEA'
D.core_properties.keywords='UTE-Tea; Spring Boot; SQL Server; POS; báo cáo đồ án'
D.core_properties.comments='Biên soạn từ bản mẫu; nội dung được đối chiếu với source code. Thông tin hành chính chưa xác nhận dùng placeholder.'
# Settings: mark Word fields for refresh; no cached page numbers from the old project survive.
setting=D.settings.element
for old in list(setting):
 if old.tag in [qn('w:attachedTemplate'),qn('w:docVars')]:setting.remove(old)
upd=setting.find(qn('w:updateFields'))
if upd is None:upd=OxmlElement('w:updateFields');setting.append(upd)
upd.set(qn('w:val'),'true')
figure=defaultdict(int);table_count=defaultdict(int);code_count=defaultdict(int);excerpt_manifest=[];current_chapter=0

def para(text='',bold=False,size=None,style=None,align=None):
 p=D.add_paragraph(style=style)
 if text:
  r=p.add_run(text);r.bold=bold
  if size:r.font.size=Pt(size)
 if align is not None:p.alignment=align
 if style is None:
  p.paragraph_format.first_line_indent=Cm(.8);p.paragraph_format.line_spacing=1.35;p.paragraph_format.space_after=Pt(5)
 return p

def title(text):
 p=para(text,bold=True,size=15,align=WD_ALIGN_PARAGRAPH.CENTER);p.paragraph_format.first_line_indent=Cm(0);return p

def heading(text,level):return D.add_paragraph(text,style='Heading '+str(level))

def field(p,instruction,cached=''):
 for kind,text in [('begin',None),(None,instruction),('separate',None),(None,cached),('end',None)]:
  r=OxmlElement('w:r')
  if kind:
   x=OxmlElement('w:fldChar');x.set(qn('w:fldCharType'),kind)
   if kind=='begin':x.set(qn('w:dirty'),'true')
  elif text==instruction:
   x=OxmlElement('w:instrText');x.set(qn('xml:space'),'preserve');x.text=text
  else:x=OxmlElement('w:t');x.text=text
  r.append(x);p._p.append(r)

def caption(kind,text):
 counters={'Hình':figure,'Bảng':table_count,'Đoạn mã':code_count};ids={'Hình':'Hinh','Bảng':'Bang','Đoạn mã':'Ma'}
 counter=counters[kind];counter[current_chapter]+=1;n=counter[current_chapter]
 p=D.add_paragraph(style='Caption');p.add_run(f'{kind} {current_chapter}.')
 field(p,' SEQ '+ids[kind]+(' \\r 1' if n==1 else '')+' \\* ARABIC ',str(n));p.add_run('. '+text)
 p.paragraph_format.keep_with_next=(kind!='Hình');return p

def table(headers,rows,widths=None,fontsize=10.5):
 t=D.add_table(rows=1,cols=len(headers));t.style='Table Grid';t.autofit=False
 if widths is None:widths=[16/len(headers)]*len(headers)
 for c,w,h in zip(t.rows[0].cells,widths,headers):
  c.width=Cm(w);c.text=h
  shd=OxmlElement('w:shd');shd.set(qn('w:fill'),'E7E6E6');c._tc.get_or_add_tcPr().append(shd)
 for row in rows:
  cells=t.add_row().cells
  for c,w,txt in zip(cells,widths,row):c.width=Cm(w);c.text=str(txt)
 for i,row in enumerate(t.rows):
  for c,w in zip(row.cells,widths):
   c.width=Cm(w)
   for p in c.paragraphs:
    p.paragraph_format.space_after=Pt(3);p.paragraph_format.space_before=Pt(3);p.paragraph_format.line_spacing=1.1
    for r in p.runs:r.font.name='Times New Roman';r.font.size=Pt(fontsize);r.bold=(i==0)
 repeat=OxmlElement('w:tblHeader');t.rows[0]._tr.get_or_add_trPr().append(repeat)
 return t

def picture(path,width=15.5):
 p=D.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.paragraph_format.keep_with_next=True
 p.add_run().add_picture(str(path),width=Cm(width));return p

def section_end(i):
 p=D.add_paragraph();p._p.get_or_add_pPr().append(deepcopy(sections[i]))

def code_excerpt(record,limit=38):
 start=record['start'];lines=(ROOT/record['file']).read_text().splitlines()[record['start']-1:record['end']]
 if len(lines)<=limit:segments=[(0,len(lines))]
 else:
  pivot=next((i for i,l in enumerate(lines[13:-12],13) if any(x in l for x in ['throw BusinessException','if (','if(','for (','for('])),len(lines)//2)
  mid=max(14,min(pivot,len(lines)-24));segments=[(0,13),(mid,mid+11),(len(lines)-12,len(lines))]
  merged=[]
  for a,b in segments:
   if merged and a<=merged[-1][1]:merged[-1]=(merged[-1][0],max(b,merged[-1][1]))
   else:merged.append((a,b))
  segments=merged
 p=para('Nguồn: '+record['file']+'; '+record['class']+'.'+record['method']+'; dòng '+str(record['start'])+'–'+str(record['end'])+'.',size=9.5)
 p.paragraph_format.first_line_indent=Cm(0);p.paragraph_format.keep_with_next=True
 for n,(a,b) in enumerate(segments):
  if n:
   omitted=segments[n-1][1];p=para(f'[Đã lược bỏ các dòng {start+omitted}–{start+a-1} của phương thức gốc.]',size=9)
   p.paragraph_format.first_line_indent=Cm(0)
  for line in lines[a:b]:
   p=D.add_paragraph();p.paragraph_format.line_spacing=Pt(12.2);p.paragraph_format.space_after=Pt(0);p.paragraph_format.space_before=Pt(0);p.paragraph_format.left_indent=Cm(.25)
   p.paragraph_format.keep_together=True;p.paragraph_format.keep_with_next=False
   shd=OxmlElement('w:shd');shd.set(qn('w:fill'),'F3F3F3');p._p.get_or_add_pPr().append(shd)
   r=p.add_run(line);r.font.name='Consolas';r.font.size=Pt(9)
  excerpt_manifest.append({'source':record['file'],'method':record['method'],'start':start+a,'end':start+b-1,'sha256':hashlib.sha256('\n'.join(lines[a:b]).encode()).hexdigest()})

# Front matter, matching the template's order.
title('NHẬN XÉT CỦA GIẢNG VIÊN HƯỚNG DẪN')
para('[CẦN BỔ SUNG: Nhận xét và đánh giá của giảng viên hướng dẫn.]')
for _ in range(11):para('................................................................................................................')
D.add_page_break();title('LỜI MỞ ĐẦU')
for t in [
'Đồ án UTE-Tea nghiên cứu và xây dựng hệ thống bán trà sữa gắn với vận hành tại quầy và tại bàn. Bài toán không dừng ở việc hiển thị thực đơn: một đơn hàng cần có cấu hình món, topping, cơ sở phục vụ, hóa đơn, phương thức nhận tiền và trạng thái chế biến nhất quán. Đồng thời, nguyên liệu được quản lý bằng định mức, tồn ở kho tổng/bếp và các chứng từ nhập, sơ chế, hao hụt, kiểm kê.',
'Báo cáo này được biên soạn trên bản Word tham khảo, bảo toàn khung ba chương gồm kiến trúc ứng dụng, sơ đồ hoạt động các chức năng và giao diện hệ thống. Nội dung được đối chiếu trực tiếp với Controller, Service, Repository, Entity, DTO, template và JavaScript của UTE-Tea; tài liệu trong docs/skills chỉ đóng vai trò bổ trợ. Các phần chưa có bằng chứng mã nguồn không được trình bày như chức năng đã hoàn tất.',
'Phạm vi checkout khách hàng hiện tại chỉ phục vụ mang về hoặc tại quán theo ngữ cảnh bàn hợp lệ. Các ô chọn hình thức/cơ sở và nhập mã giảm giá đã được loại khỏi giao diện. Sau khi nhấn Gửi đơn hàng, khách chọn tiền mặt hoặc chuyển khoản trong hộp thoại rồi xác nhận. Việc chọn phương thức hoặc thông báo đã chuyển khoản chưa đủ để ghi nhận thanh toán; khoản thu cần được thu ngân xác nhận trong hệ thống.',
'Ngoài luồng POS chính, mã nguồn còn giữ module cơ sở, các vai trò VENDOR/MANAGER/SHIPPER, lịch sử DeliveryOrder và tích hợp VNPAY của module cũ. Báo cáo phân biệt rõ những thành phần này với checkout hiện hành, tránh suy diễn khả năng giao tận nơi hoặc đối soát ngân hàng tự động. Ảnh giao diện được chụp từ ứng dụng demo H2 tách biệt, không đại diện cho số liệu kinh doanh thật.',
'Các thông tin hành chính chưa được chủ nhiệm đồ án xác nhận được đánh dấu [CẦN BỔ SUNG]. Đây là các trường cần hoàn thiện trước khi nộp; không tự sử dụng tên giảng viên, lớp hoặc danh sách sinh viên từ tài liệu tham khảo.'
]:para(t)
for name,instruction in [('MỤC LỤC',' TOC \\o "1-3" \\h \\z \\u '),('DANH MỤC HÌNH',' TOC \\h \\z \\c "Hinh" '),('DANH MỤC BẢNG',' TOC \\h \\z \\c "Bang" '),('DANH MỤC ĐOẠN MÃ',' TOC \\h \\z \\c "Ma" ')]:
 D.add_page_break();title(name);p=D.add_paragraph();field(p,instruction,'[Cập nhật trường trong Word để hiển thị danh mục và số trang.]')
section_end(1)
current_chapter=1
heading('KIẾN TRÚC ỨNG DỤNG',1)
heading('Sơ đồ kiến trúc ứng dụng',2)
para('UTE-Tea là ứng dụng nguyên khối phân tầng trên Spring Boot. Tầng trình bày kết hợp các trang Thymeleaf render phía server với JavaScript gọi REST API. Spring Security xử lý JWT/CSRF và quyền truy cập trước khi yêu cầu vào Controller; Service giữ điều kiện nghiệp vụ và transaction; Repository/JPA truy vấn các Entity ánh xạ SQL Server. H2 được dùng cho demo/test với cấu hình tách biệt.')
picture(OUT/'So_Do/Kien_Truc.png');caption('Hình','Kiến trúc UTE-Tea đối chiếu theo các package hiện có')
para('Các tích hợp được nhận diện từ dependency và class thực tế gồm SMTP qua JavaMailSender, Cloudinary cho media và WebSocket/STOMP cho thông báo sau commit. VNPAY còn nằm ở phân hệ DeliveryOrder cũ; không phải lựa chọn trong hộp thanh toán POS hiện tại. Các đường nối biểu diễn trách nhiệm, không hàm ý hệ thống được triển khai thành nhiều microservice.')
heading('Mô tả kiến trúc ứng dụng',2)
heading('Công nghệ và tổ chức mã nguồn',3)
caption('Bảng','Công nghệ được xác nhận từ pom.xml và mã nguồn')
table(['Thành phần','Bằng chứng','Vai trò'],[
('Java 21','pom.xml: java.version và compiler release','Ngôn ngữ backend và môi trường biên dịch'),('Spring Boot 4.1.1','parent/dependency/plugin trong pom.xml','Khởi tạo ứng dụng, embedded web server và cấu hình ngoài'),('Spring MVC + Thymeleaf','controller/; templates/','Render trang HTML và cung cấp các API HTTP'),('Spring Security / JWT / BCrypt','SecurityConfig; JwtAuthenticationFilter; JwtService','Xác thực, phân quyền, chống CSRF và hash mật khẩu'),('Spring Data JPA / Hibernate','repository/; entity/','ORM, truy vấn và khóa bản ghi trong transaction'),('Microsoft SQL Server','mssql-jdbc; database/sqlserver/','CSDL quan hệ dùng cho local/prod theo cấu hình'),('H2','application-demo.yml; application-test.yml','Demo/test; không là bằng chứng migration SQL Server đã chạy thật'),('Bootstrap + JavaScript','layouts/main.html; static/js/','Responsive UI, dialog, fetch API và cập nhật màn hình'),('WebSocket / STOMP','WebSocketConfig; RealtimeEventPublisher','Thông báo đơn, tiền, phiên bàn và thay đổi cấu hình'),('Spring Mail / Cloudinary','MailService; ProductImageService; ReviewService','Dịch vụ ngoài hệ thống; cần cấu hình hợp lệ để chạy thật')],[3.7,5.2,7.1])
para('Phiên bản trong bảng phản ánh pom.xml của workspace, không phải tuyên bố đây là phiên bản mới nhất trên Internet. Spring Boot cung cấp cách đóng gói và chạy ứng dụng độc lập cùng cấu hình ngoài [TL1]. Việc đối chiếu phiên bản/tương thích của đồ án được thực hiện trên dependency của dự án, không lấy phiên bản từ báo cáo mẫu.')
caption('Bảng','Trách nhiệm các lớp và thư mục')
table(['Tầng','Thư mục','Trách nhiệm và ví dụ'],[
('Controller','src/main/java/vn/edu/ute/milktea/controller','Nhận request, @Valid, CurrentActor, headers; AuthController, OrderController, InventoryController.'),('Service','.../service','Áp quy tắc, transaction, trạng thái, tính tiền; OrderService, PaymentService, AuthService.'),('Repository','.../repository','JPA interface, truy vấn, pessimistic locking; OrderRepository, StockRepository.'),('Entity','.../entity','Bảng, cột và quan hệ; Order, Invoice, Payment, DiningTable, Stock.'),('DTO','.../dto','Hợp đồng đầu vào/đầu ra, validation; OrderDto, AuthDto, InventoryDto.'),('Security','.../security','JWT cookie/tokenVersion, capability guest, CSRF và kiểm tra role.'),('Frontend','src/main/resources/templates và static','Trang Thymeleaf, CSS/JS; giỏ, dialog thanh toán, polling và STOMP.'),('Database','database/sqlserver','Schema, seed và các migration tăng dần; không chạy script DROP cho dữ liệu đang vận hành.')],[2.2,4.6,9.2])
heading('Luồng xử lý yêu cầu và dữ liệu',3)
for t in [
'Đối với trang HTML, browser gửi request đến CustomerPageController hoặc StaffPageController. Controller lấy dữ liệu từ repository/service, đưa vào Model và trả tên template. Thymeleaf kết hợp layouts/fragments với dữ liệu server để trả trang hoàn chỉnh. Các thao tác tiếp theo trên trang dùng MilkTeaApi trong static/js/common/api.js, thay vì submit form truyền thống ở mọi màn hình.',
'Đối với API thay đổi dữ liệu, JavaScript lấy/cập nhật CSRF token, gửi JSON hoặc multipart cùng cookie xác thực và header chống gửi lặp khi có. Filter JWT xây dựng CurrentActor từ tài khoản đang hoạt động và email đã xác thực; các token guest chỉ có phạm vi phiên hoặc một chứng từ. Controller xác thực DTO và chuyển thao tác vào Service. Service khóa bản ghi cần thiết, kiểm tra điều kiện và gọi Repository trong cùng transaction.',
'Khi tạo đơn, giá sản phẩm và topping được lấy lại từ database theo cơ sở phục vụ. Frontend không có quyền quyết định đơn giá và totalAmount. OrderItem lưu snapshot tên/size/giá, Invoice lưu snapshot giảm giá; vì vậy thay giá menu sau này không làm đổi hóa đơn đã phát sinh. expectedTotal chỉ dùng để phát hiện tổng hiển thị đã thay đổi, không được sử dụng làm công thức tính tiền.',
'Thông báo realtime được đăng ký sau commit để giao diện không nhìn thấy trạng thái chưa được lưu. Lệnh nghiệp vụ vẫn chạy qua HTTP và Service; STOMP không thay thế transaction. Khi socket mất kết nối, client có cơ chế tải lại/polling tùy trang. Email xác thực cũng được gửi sau khi tài khoản/OTP đã commit để SMTP thất bại không xóa dữ liệu chờ xác thực.'
]:para(t)
heading('Tác nhân, phạm vi và mức độ triển khai',3)
caption('Bảng','Vai trò có trong Role.java và cách sử dụng thực tế')
table(['Vai trò','Phạm vi được xác nhận','Giới hạn cần ghi rõ'],[
('CUSTOMER / guest','Menu, giỏ, quote, tạo đơn, chứng từ, lịch sử tài khoản, hồ sơ, đánh giá có điều kiện.','Guest không được xem lịch sử chỉ nhờ ID bàn; cần chứng từ riêng cho một đơn.'),('CASHIER','POS, hàng đợi, xác nhận đơn, thu tiền, bàn/phiên, hủy/hoàn theo route.','Các lệnh đơn/thu tiền kiểm tra cơ sở; không suy ra mọi module hủy/kho đã đồng nhất phạm vi.'),('KITCHEN','Đơn cần pha, start/complete, tồn/hao hụt/sơ chế/kiểm kê theo PreAuthorize.','Trạng thái toàn đơn; tiêu hao dùng snapshot và tồn nguyên liệu theo KHO/BEP.'),('ADMIN','Menu, nhân viên, bàn, kho, cài đặt, thống kê, phạm vi quản trị.','Không có entity Brand/luồng phản hồi kiểm duyệt review chỉ vì báo cáo mẫu có.'),('VENDOR / MANAGER','Shop owner hoặc được phân công; portal cơ sở, menu, voucher và dữ liệu tương thích.','Không áp quyền MANAGER của marketplace vào mọi route POS một cách suy đoán.'),('SHIPPER','Phân hệ DeliveryOrder lịch sử có API gán và chuyển trạng thái.','Không phải lựa chọn của checkout hiện tại; POST tạo DeliveryOrder từ khách đã bị chặn.')],[3,6.6,6.4])
para('Bảng trên mô tả phạm vi đã đối chiếu ở SecurityConfig, @PreAuthorize và service. Các nội dung cần cải thiện được giữ như hạn chế kỹ thuật: một số module cũ chưa đồng nhất kiểm tra cơ sở; Stock hiện định danh theo nguyên liệu/vị trí, không có ShopId ở mọi dòng. Không mô tả toàn bộ kho như một hệ thống tồn nguyên liệu phân tán theo chi nhánh khi schema chưa thể hiện điều đó.')
heading('Bảo mật, transaction và chống xử lý trùng',3)
for t in [
'Spring Security của ứng dụng servlet sử dụng filter chain để chặn hoặc chuyển tiếp yêu cầu trước DispatcherServlet; thứ tự filter ảnh hưởng đến việc xác thực và phân quyền [TL2]. Trong UTE-Tea, JwtAuthenticationFilter đặt trước UsernamePasswordAuthenticationFilter; SecurityConfig khai báo phạm vi route theo role và giữ CSRF cho các mutation HTTP. Các API auth public không đồng nghĩa được bỏ CSRF.',
'Mật khẩu tài khoản được hash bằng PasswordEncoder/BCrypt. JWT lưu trong cookie HttpOnly; tokenVersion và các cờ active/emailVerified được đối chiếu với DB ở mỗi request có xác thực. Token guest là chuỗi opaque lưu hash, có thời hạn và phạm vi cụ thể. ID bàn hoặc sessionId do client gửi không tự trở thành bằng chứng quyền truy cập.',
'OTP sáu chữ số sinh bằng SecureRandom và chỉ lưu HMAC cùng salt, email, purpose, thời hạn và số lần thử. OTP_SECRET phải là khóa riêng hợp lệ; báo cáo không đọc hay đưa giá trị .env vào tài liệu. REGISTER_VERIFY và PASSWORD_RESET là hai mục đích khác nhau. Mã đúng được đánh dấu usedAt; mã sai/hết hạn có cập nhật trạng thái và giữ bộ đếm lỗi nghiệp vụ trong transaction.',
'Order dùng Idempotency-Key và fingerprint của nội dung để phát hiện gửi lặp; khóa cơ sở/bàn giúp tuần tự các thao tác có liên quan. Payment có khóa invoice/order, kiểm tra khoản thu đã tồn tại và không nhận số tiền do frontend quyết định. Một lần replay đúng không được chèn thêm hóa đơn hoặc khoản thu. Khách ẩn danh không được dùng key như quyền đọc lịch sử của người khác.',
'Transaction DB không đồng nhất với lời gọi SMTP/Cloudinary. MailService đăng ký gửi sau commit để tài khoản pending có thể gửi lại khi mail lỗi. ProductImageService có xử lý dọn ảnh mới khi rollback và xóa ảnh cũ sau commit theo nhánh source. Chưa có bằng chứng giao dịch thật của ngân hàng/Cloudinary trong phiên tạo báo cáo; chỉ xác nhận pipeline và các kiểm tra đã có trong code.'
]:para(t)
heading('Mô hình dữ liệu và quan hệ chính',3)
para('Tên bảng/cột sau đây lấy từ annotation JPA và SQL script của workspace. Khóa chính bảo vệ tính duy nhất của bản ghi; khóa ngoại ràng buộc quan hệ tham chiếu giữa các bảng [TL3]. Các ảnh là lược đồ quan hệ chọn lọc nhằm đọc hiểu; từ điển Entity phía sau giữ đầy đủ trường và nguồn để đối chiếu.')
picture(OUT/'So_Do/ER_Don_Thanh_Toan.png');caption('Hình','Các bảng và quan hệ cốt lõi của đơn, bàn và thanh toán')
picture(OUT/'So_Do/ER_Kho.png');caption('Hình','Các bảng kho và chứng từ; quan hệ chọn lọc từ JPA')
caption('Bảng','Nhóm dữ liệu và tính chất lưu trữ')
table(['Nhóm','Các Entity tiêu biểu','Ý nghĩa'],[
('Tài khoản/OTP','Account, EmailVerificationOtp, PasswordResetToken, AccountCart','Tách trạng thái active và emailVerified; vé/token lưu hash, giỏ có revision.'),('Menu','Product, Category, ProductRecipe','Biến thể theo tên/size/menuCode, giá theo Shop và công thức nguyên liệu.'),('Bàn','DiningTable, TableSession, TableSessionAccess, SessionCart','Lượt phục vụ OPEN/CLOSED và quyền truy cập scoped theo phiên.'),('Đơn/tiền','Order, OrderItem, OrderItemTopping, Invoice, Payment, PaymentNotice','Snapshot và trạng thái đơn độc lập khoản thu thực tế.'),('Kho','Material, Stock, StockMovement, ImportReceipt, PreparationBatch, ShiftSettlement','Nguyên liệu THO/SOCHE, vị trí KHO/BEP và lịch sử chứng từ.'),('Ngoại lệ','CancellationRequest, RefundRequest, BusinessAudit','Yêu cầu, quyết định, khoản hoàn và dấu vết xử lý.'),('Mở rộng/lịch sử','Shop, Voucher, DeliveryOrder, ProductReview, ReviewMedia','Phạm vi cơ sở; module giao hàng cũ; đánh giá liên kết bằng chứng mua.')],[3.1,6.2,6.7])
heading('Từ điển Entity đối chiếu bảng và cột',3)
for ent in sorted(E,key=lambda e:(e['file'].split('/entity/')[1].split('/')[0],e['table'])):
 caption('Bảng',f"Ánh xạ {ent['class']} → {ent['table']}")
 para('Nguồn: '+ent['file']+'.',size=9.5)
 rows=[]
 for item in ent['fields']:
  ann=item['annotation'];constraint=[]
  if item['pk']:constraint.append('PK')
  if item['relation']:constraint.append('Quan hệ JPA')
  if 'nullable = false' in ann or 'nullable=false' in ann:constraint.append('NOT NULL')
  if 'unique = true' in ann or 'unique=true' in ann:constraint.append('UNIQUE')
  if '@Version' in ann:constraint.append('Optimistic version')
  for k in ['length','precision','scale']:
   m=re.search(k+r'\s*=\s*(\d+)',ann)
   if m:constraint.append(k+'='+m.group(1))
  if '@Enumerated' in ann:constraint.append('Enum STRING')
  if '@ManyToOne' in ann:constraint.append('N:1')
  if '@OneToOne' in ann:constraint.append('1:1')
  if '@OneToMany' in ann:constraint.append('1:N; không là cột scalar')
  if '@ManyToMany' in ann:constraint.append('N:N qua bảng nối')
  if item.get('embedded'):
   cp=ROOT/ent['file'];idfile=next((ROOT/'src/main/java').rglob(item['type']+'.java'),None)
   cols=re.findall(r'@Column\(name\s*=\s*"([^"]+)"',idfile.read_text()) if idfile else []
   constraint.append('Thành phần: '+', '.join(cols))
  rows.append((item['name'],item['column'],item['type'], '; '.join(constraint) or 'Theo mặc định JPA/annotation; không suy diễn ràng buộc bổ sung'))
 table(['Thuộc tính Java','Cột/ánh xạ','Kiểu Java','Ràng buộc/quan hệ'],rows,[3.0,3.8,3.0,6.2],9.8)
 para('Các cột FK thuộc @JoinColumn; quan hệ dạng tập hợp được trình bày là ánh xạ, không coi tên collection là cột vật lý. Khóa nhúng được đối chiếu thêm với lớp Id tương ứng.',size=10)
para('Shop.managers dùng bảng nối ShopManager với ShopId/AccountId, không có một Entity độc lập tên ShopManager. Các lớp Id/enum không phải bảng riêng. SQL script cũng có các ràng buộc/index và dữ liệu lịch sử; vì vậy số class Entity không được đồng nhất một cách máy móc với toàn bộ đối tượng trong database triển khai.')
heading('Luồng trạng thái nghiệp vụ và giới hạn tích hợp',3)
caption('Bảng','Trạng thái và bằng chứng trong mã hiện tại')
table(['Nội dung','Luồng/điều kiện','Bằng chứng'],[
('Đơn khách','CHO_XAC_NHAN → CHO_CHE_BIEN → DANG_CHE_BIEN → HOAN_THANH; nhánh DA_HUY theo điều kiện.','OrderStatus; OrderService; CancellationService'),('Đơn POS','Nhân viên tạo CHO_THANH_TOAN; ghi thu/điều phối cập nhật theo logic hiện có.','OrderService.createCounterOrder; PaymentService.recordReceipt'),('Thanh toán','CASH/BANK_TRANSFER; chưa có Payment tương ứng thì response PENDING_PAYMENT. Notice không làm PAID.','PaymentMethod; PaymentRepository; mapToOrderResponse'),('Phiên bàn','Xem QR không mở phiên; đơn đầu hoặc thu ngân mở; chỉ đóng thủ công khi điều kiện thỏa.','TableContextService; TableSessionService'),('Ngân hàng','Thông tin cấu hình quản trị; VietQR theo số tiền/nội dung; thu ngân xác minh khoản thu.','GlobalSettings; OrderService.vietQr; PaymentService'),('VNPAY','Có chữ ký/IPN ở module DeliveryOrder cũ; không nằm trong dialog checkout mới.','VnpayService; VnpayController; MarketController.checkout'),('Cloudinary/SMTP','Có code tích hợp và xử lý lỗi; cấu hình bên ngoài quyết định khả năng dùng thực.','MailService; ProductImageService; ReviewService')],[3.0,8.0,5.0])
heading('Phạm vi kiểm chứng và tài liệu nguồn',3)
para('Phiên biên soạn đã chạy một demo H2 riêng ở cổng 18081 để chụp 27 giao diện và thực hiện các lệnh mẫu tạo đơn, ghi thu, xác nhận và chế biến. SMTP bị vô hiệu trong demo chụp ảnh; .env và dữ liệu đang vận hành không được đọc để đưa vào báo cáo. Các ảnh là ảnh thật của ứng dụng với dữ liệu mẫu, không phải ảnh do AI vẽ.')
para('Các báo cáo Surefire hiện có trong target/surefire-reports được dùng như bằng chứng kiểm thử đã lưu; không tự khẳng định mọi chức năng đã được E2E. Chưa xác minh SQL Server thật, giao dịch ngân hàng, email tới hộp thư thật hay upload Cloudinary trong phiên biên soạn. Những điều kiện này được nêu tại từng nhóm chức năng và bảng kiểm tra kèm theo.')
for t in [
'[TL1] Spring Boot, tài liệu chính thức: https://docs.spring.io/spring-boot/index.html. Truy cập ngày 27/10/2026; dùng cho khái niệm cấu hình/chạy ứng dụng, phiên bản đồ án vẫn lấy từ pom.xml.',
'[TL2] Spring Security, Servlet Architecture: https://docs.spring.io/spring-security/reference/servlet/architecture.html. Truy cập ngày 27/10/2026; tham khảo mô hình filter chain.',
'[TL3] Microsoft Learn, Primary and foreign key constraints: https://learn.microsoft.com/en-us/sql/relational-databases/tables/primary-and-foreign-key-constraints. Truy cập ngày 27/10/2026.',
'[TL4] Mã nguồn UTE-Tea: src/main/java, src/main/resources, database/sqlserver, tests và src/test/java. Các trích đoạn trong Chương 2 có đường dẫn và số dòng cụ thể.',
'[TL5] Tài liệu bổ trợ của dự án: skills/domain_system.md, skills/workflow_system.md, skills/system_architecture.md, docs/CHECKOUT_PAYMENT_FIX.md và docs/STARTUP_FIX.md; các quyết định mới được đối chiếu lại với source hiện tại.'
]:para(t,size=10.5)
section_end(2)
current_chapter=2
heading('SƠ ĐỒ HOẠT ĐỘNG CÁC CHỨC NĂNG',1)
para('Chương trình bày theo thứ tự tác nhân → chức năng → mô tả → sơ đồ hoạt động → mã xử lý → giải thích, giữ cấu trúc của tài liệu tham khảo. Có '+str(len(F))+' nhóm chức năng được đối chiếu. Chỉ những nhánh có mã nguồn mới được mô tả; các module tương thích được đánh dấu riêng. Sơ đồ rút gọn các kiểm tra liên quan thành nút quyết định đọc hiểu, không bổ sung một bước nghiệp vụ chưa tồn tại.')
groups=list(dict.fromkeys(f['group'] for f in F));matrix=[]
for gi,group in enumerate(groups,1):
 heading(group,2)
 if group==groups[-1]:para('Nhóm này phản ánh VENDOR/MANAGER/SHIPPER và module marketplace/lịch sử còn trong source. Nó không thay đổi phạm vi mua mang về/tại quán của checkout hiện tại. Một API legacy còn tồn tại không được xem là bằng chứng dịch vụ giao hàng mới đang vận hành.')
 for fi,v in enumerate([f for f in F if f['group']==group],1):
  v['report_section']=f'2.{gi}.{fi}';heading(v['title'],3)
  heading('Mô tả chức năng',4)
  caption('Bảng','Đặc tả: '+v['title'])
  table(['Tiêu chí','Nội dung'],[
   ('Mục đích',v['goal']),('Tác nhân',v['group']),('Điều kiện trước',v['pre']),('Đầu vào',v['inputs']),('Kết quả / điều kiện sau',v['post']),('Trường hợp lỗi',v['error']),('Trạng thái xác minh',v['status'])],[3.1,12.9],11)
  for i,step in enumerate(v['steps'],1):para(f'Bước {i}. '+step+'.')
  heading('Sơ đồ hoạt động',4)
  picture(OUT/'So_Do'/f"{v['diagram']}.png",13.6);caption('Hình','Hoạt động: '+v['title'])
  heading('Code xử lý chức năng',4)
  caption('Đoạn mã',v['source']['class']+'.'+v['source']['method']+' — '+v['title'])
  code_excerpt(v['source'])
  heading('Giải thích code',4)
  para(v['note'])
  calls=[]
  for obj,fn in re.findall(r'\b(\w+)\.(\w+)\(',v['source']['code']):
   if any(x in obj.lower() for x in ['repository','service','encoder','publisher']) and (obj,fn) not in calls:calls.append((obj,fn))
  selected=calls[:4]
  if selected:para('Các điểm phối hợp thể hiện trực tiếp trong trích đoạn/phương thức: '+ '; '.join(obj+'.'+fn+'()' for obj,fn in selected)+'. Đường dẫn giao diện liên quan: src/main/resources/'+v['front'].replace('; ','; src/main/resources/')+'.',size=10.5)
  else:para('Method liên quan: '+v['key']+'. Controller: '+v['controller']+'. Giao diện: src/main/resources/'+v['front'].replace('; ','; src/main/resources/')+'.',size=10.5)
  matrix.append([len(matrix)+1,group,v['title'],v['controller'],v['key'],v['front'],v['entities'],v['status'],v['report_section']])
section_end(3)
current_chapter=3
heading('GIAO DIỆN HỆ THỐNG',1)
para('Tất cả ảnh trong chương được chụp trực tiếp từ UTE-Tea chạy trên H2 demo tách biệt. Dữ liệu món, tài khoản và đơn trong ảnh là dữ liệu minh họa. Không sử dụng hình giao diện của dự án khác; không dùng ảnh AI dựng giao diện. Một số tác vụ dùng modal hoặc tab trong trang hiện có, không tự đặt tên một route trang độc lập.')
for group in list(dict.fromkeys(s['group'] for s in UI)):
 heading('Giao diện '+group.lower(),2)
 for screen in [s for s in UI if s['group']==group]:
  heading(screen['title'],3)
  para(screen['purpose'])
  picture(OUT/'Anh_Giao_Dien'/f"{screen['key']}.png",16);caption('Hình',screen['title']+' — '+screen['path'])
  caption('Bảng','Mô tả thành phần giao diện '+screen['title'].lower())
  table(['Nội dung','Mô tả'],[('Mục đích',screen['purpose']),('Thành phần chính',screen['components']),('Thao tác',screen['actions']),('Đường dẫn',screen['path']),('File giao diện','src/main/resources/'+screen['front'].replace('; ','; src/main/resources/')),('Bằng chứng','Ảnh chụp Chromium từ demo H2; ngày 27/10/2026. '+screen['note'])],[3.1,12.9],11)
heading('Đánh giá mức độ hoàn thiện và nội dung cần bổ sung',2)
para('Báo cáo phản ánh các màn hình đã mở được và các chức năng được xác minh từ mã. Chức năng ảnh/video, ngân hàng, SMTP và VNPAY cần cấu hình dịch vụ ngoài; không xác nhận hoạt động thực tế chỉ từ việc có nút trên UI. Quyền cơ sở của POS được thực hiện ở một số service chính, trong khi những module cũ cần tiếp tục rà soát để đồng nhất. Các hạn chế này là nhận xét kỹ thuật, không được thay bằng kết quả hoàn thành giả định.')
caption('Bảng','Nội dung chưa đủ bằng chứng hoặc cần xác nhận')
table(['Nội dung','Trạng thái','Việc cần làm'],[
('Thông tin hành chính','[CẦN BỔ SUNG]','Xác nhận trường/khoa/logo, học phần, giảng viên, lớp, họ tên/MSSV và địa điểm.'),('SQL Server thật','Chưa kiểm chứng trong phiên báo cáo','Áp dụng migration an toàn và kiểm tra ở môi trường triển khai.'),('SMTP/Gmail thật','Không sử dụng credentials thật trong phiên chụp ảnh','Cấu hình và thử gửi thư tại môi trường do người dùng quản lý.'),('Ngân hàng/VietQR','Có code cấu hình; chưa đối soát thật','Điền thông tin hợp lệ và thu ngân kiểm tra giao dịch; không tự gán PAID.'),('Cloudinary/VNPAY','Có code; chưa xác minh dịch vụ thật','Kiểm chứng với tài khoản dịch vụ phù hợp, không đưa secret vào báo cáo.'),('Các chức năng không có source','Không nhận là đã triển khai','Không thêm module Brand riêng, cổng mô phỏng, kiểm duyệt/phản hồi review hay route chi tiết sản phẩm giả.'),('Mục lục và caption','Có trường Word tự động','Cập nhật toàn bộ trường và kiểm tra trang trước khi in/nộp.')],[4.0,4.1,7.9],10.5)
para('Danh sách đối chiếu chi tiết nằm trong reports/Bang_Doi_Chieu_Chuc_Nang.md. Tài liệu ghi rõ giới hạn kiểm tra và các mục còn thiếu tại reports/Bao_Cao_Kiem_Tra.md. Không thay placeholder hành chính bằng dữ liệu mẫu khi chưa được xác nhận.')
# Remove unused old relationships (screenshots/code/comments) from the package.
used=set()
for part in [D.part]+[r.target_part for r in D.part.rels.values() if not r.is_external and (r.reltype.endswith('/header') or r.reltype.endswith('/footer'))]:
 try:
  for el in part.element.iter():
   for attr in ['embed','link','id']:
    val=el.get(qn('r:'+attr))
    if val:used.add(val)
 except AttributeError:pass
for relid,rel in list(D.part.rels.items()):
 if rel.reltype.endswith('/image') and relid not in used:D.part.drop_rel(relid)
 elif any(t in rel.reltype.lower() for t in ['comments','people','customxml','attachedtemplate']):D.part.drop_rel(relid)
 elif rel.is_external:D.part.drop_rel(relid)
# Set final main section properties from template, preserving A4 margins and page-number settings.
final=D.element.body.find(qn('w:sectPr'))
# Word's package metadata may carry the old title even after body replacement. Rewrite only metadata, never source/template.
raw=OUT/'Bao_Cao_UTE_Tea.docx';D.save(raw)
tmp=WORK/'cleaned.docx'
with zipfile.ZipFile(raw) as zin,zipfile.ZipFile(tmp,'w',zipfile.ZIP_DEFLATED) as zout:
 for item in zin.infolist():
  data=zin.read(item.filename)
  if item.filename=='docProps/app.xml':
   el=etree.fromstring(data);ns={'e':'http://schemas.openxmlformats.org/officeDocument/2006/extended-properties','v':'http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes'}
   for tag in ['Pages','Words','Characters','Lines','Paragraphs','CharactersWithSpaces']:
    for node in el.findall('e:'+tag,ns):node.text='0'
   for node in el.findall('e:TitlesOfParts//v:lpstr',ns):node.text='UTE-TEA — Báo cáo đồ án'
   for tag in ['Manager','Company']:
    for node in el.findall('e:'+tag,ns):node.text=''
   data=etree.tostring(el,xml_declaration=True,encoding='UTF-8',standalone=True)
  zout.writestr(item,data)
shutil.move(tmp,raw)
# Markdown feature traceability matrix is the required machine-reviewable companion artifact.
headers=['STT','Vai trò','Tên chức năng','Controller','Service','File frontend','Entity liên quan','Trạng thái xác minh','Mục trong báo cáo']
md='# Bảng đối chiếu chức năng UTE-Tea\n\nNguồn chính: source code tại thời điểm 27/10/2026. Đây là '+str(len(F))+' nhóm chức năng; nhóm có thể bao gồm các thao tác CRUD liên quan. Không dùng tên file đơn thuần làm bằng chứng vận hành thực tế.\n\n'
md+='| '+' | '.join(headers)+' |\n|'+ '|'.join(['---']*len(headers))+'|\n'
for row in matrix:md+='| '+' | '.join(str(x).replace('|','/').replace('\n',' ') for x in row)+' |\n'
md+='\n## Ranh giới kiểm chứng\n\n- Source Controller/Service/Entity là bằng chứng đã có implementation; không tương đương đã kiểm thử bên ngoài.\n- Checkout mới không nhận giao hàng và không có ô nhập mã giảm giá; legacy DeliveryOrder/VNPAY được ghi riêng.\n- Không khẳng định có Brand, duyệt/phản hồi review hay trạng thái từng món khi chưa có source tương ứng.\n'
(OUT/'Bang_Doi_Chieu_Chuc_Nang.md').write_text(md)
(WORK/'excerpts.json').write_text(json.dumps(excerpt_manifest,ensure_ascii=False,indent=2))
(WORK/'document_inventory.json').write_text(json.dumps({'template':TEMPLATE.name,'template_sha256':original_template_hash,'features':len(F),'screenshots':len(UI),'entities':len(E),'figures':dict(figure),'tables':dict(table_count),'code':dict(code_count),'excerpts':len(excerpt_manifest)},ensure_ascii=False,indent=2))
# Integrity gate: there must not be any template-project text in the resulting XML.
with zipfile.ZipFile(raw) as z:
 bad=[]
 for n in z.namelist():
  if n.endswith('.xml'):
   txt=z.read(n).decode('utf-8',errors='ignore')
   if re.search(r'oneshop|mỹ phẩm|my pham|NGÔ MINH KHÁNH|24110248|WEPR330479_11',txt,re.I):bad.append(n)
 if bad:raise RuntimeError('Template content remains: '+str(bad))
assert hashlib.sha256(TEMPLATE.read_bytes()).hexdigest()==original_template_hash
print(json.dumps(json.loads((WORK/'document_inventory.json').read_text()),ensure_ascii=False,indent=2))
print('DOCX created:',raw,raw.stat().st_size)
