from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,math,textwrap
OUT=Path('reports/So_Do');OUT.mkdir(exist_ok=True)
FONT='/System/Library/Fonts/Supplemental/Arial.ttf';BOLD='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
def f(n=25,b=False):return ImageFont.truetype(BOLD if b else FONT,n)
def wrap(text,width,draw,font):
 lines=[]
 for paragraph in text.split('\n'):
  line=''
  for word in paragraph.split():
   candidate=(line+' '+word).strip()
   if draw.textlength(candidate,font=font)>width and line:lines.append(line);line=word
   else:line=candidate
  lines.append(line)
 return '\n'.join(lines)
def label(draw,box,text,size=25,b=False):
 x0,y0,x1,y1=box;raw=text
 while True:
  font=f(size,b);text=wrap(raw,x1-x0-28,draw,font);bb=draw.multiline_textbbox((0,0),text,font=font,spacing=6,align='center')
  if bb[3]-bb[1]<=y1-y0-14 or size<=13:break
  size-=1
 draw.multiline_text(((x0+x1)/2-(bb[2]-bb[0])/2,(y0+y1)/2-(bb[3]-bb[1])/2-5),text,font=font,fill='#1a1a1a',spacing=6,align='center')
def arrow(d,points):
 d.line(points,fill='#222222',width=3)
 a,b=points[-2:];ang=math.atan2(b[1]-a[1],b[0]-a[0]);r=13
 d.polygon([b,(b[0]-r*math.cos(ang-.45),b[1]-r*math.sin(ang-.45)),(b[0]-r*math.cos(ang+.45),b[1]-r*math.sin(ang+.45))],fill='#222222')
def box(d,xy,text,size=25):
 d.rounded_rectangle(xy,radius=16,fill='#fbfcfa',outline='#222222',width=3);label(d,xy,text,size)
def activity(feature,i):
 steps=feature['steps'];W=1420;top=85;row=115;H=top+(len(steps)+4)*row+70
 im=Image.new('RGB',(W,H),'white');d=ImageDraw.Draw(im);cx=495
 d.ellipse((cx-16,top-16,cx+16,top+16),fill='black')
 y=top+70;box(d,(115,y,875,y+78),steps[0],24);arrow(d,[(cx,top+18),(cx,y)])
 diamondY=y+216;halfW=300;halfH=83
 d.polygon([(cx,diamondY-halfH),(cx+halfW,diamondY),(cx,diamondY+halfH),(cx-halfW,diamondY)],fill='white',outline='black',width=3)
 label(d,(cx-235,diamondY-60,cx+235,diamondY+60),feature['condition'],23)
 arrow(d,[(cx,y+78),(cx,diamondY-halfH)])
 errX=1020;errY=diamondY-60
 box(d,(errX,errY,1390,errY+122),feature.get('false_output','Từ chối / thông báo lỗi\n'+feature['error'].split('.')[0]),20)
 arrow(d,[(cx+halfW,diamondY),(errX,diamondY)]);label(d,(cx+halfW+5,diamondY-46,errX-5,diamondY-6),'Sai',21)
 yy=diamondY+halfH+60;prev=diamondY+halfH
 for n,step in enumerate(steps[1:]):
  box(d,(115,yy,875,yy+82),step,24);arrow(d,[(cx,prev),(cx,yy)])
  if n==0:label(d,(cx+15,prev+5,cx+140,yy-5),'Đúng',21)
  prev=yy+82;yy+=row
 endY=yy+25;arrow(d,[(cx,prev),(cx,endY-23)])
 d.ellipse((cx-23,endY-23,cx+23,endY+23),fill='white',outline='black',width=3);d.ellipse((cx-14,endY-14,cx+14,endY+14),fill='black')
 arrow(d,[(1205,errY+122),(1205,endY),(cx+24,endY)])
 im=im.crop((0,0,W,endY+75));name=f'HD_{i:02d}';im.save(OUT/(name+'.png'),dpi=(200,200))
 lines=['flowchart TD','  S((Bắt đầu))',f'  P0["{steps[0]}"]',f'  D{{"{feature["condition"]}"}}','  E["'+feature.get('false_output','Thông báo lỗi; không xác nhận thành công').replace(chr(34),chr(39))+'"]','  F((Kết thúc))','  S --> P0 --> D','  D -- Sai --> E --> F']
 prev='D'
 for n,step in enumerate(steps[1:],1):
  lines.append(f'  P{n}["{step.replace(chr(34),chr(39))}"]');lines.append(f'  {prev} '+('-- Đúng -->' if n==1 else '-->')+f' P{n}');prev=f'P{n}'
 lines.append(f'  {prev} --> F');(OUT/(name+'.mmd')).write_text('\n'.join(lines))
 return name
F=json.loads(Path('reports/_work/features.json').read_text())
for i,v in enumerate(F,1):v['diagram']=activity(v,i)
Path('reports/_work/features.json').write_text(json.dumps(F,ensure_ascii=False,indent=2))
# Architecture built directly from the current packages and integrations.
im=Image.new('RGB',(1700,1250),'white');d=ImageDraw.Draw(im)
nodes={'browser':(480,40,1220,170),'security':(35,240,430,430),'controller':(540,245,1170,355),'service':(540,485,1170,595),'repo':(540,725,1170,835),'sql':(540,1000,1170,1140),'ws':(1230,245,1670,420),'mail':(1230,485,1670,620),'cloud':(1230,725,1670,860),'legacy':(35,650,430,845)}
texts={'browser':'Trình duyệt\nThymeleaf + HTML/CSS + JavaScript/Bootstrap','security':'Spring Security\nJWT cookie + CSRF\nCurrentActor / quyền cơ sở','controller':'MVC Controller / REST Controller\nHTTP request, DTO, ApiResponse','service':'Service / nghiệp vụ\nTransaction, kiểm tra trạng thái, snapshot','repo':'Repository / Spring Data JPA\nKhóa bản ghi, truy vấn, quan hệ Entity','sql':'SQL Server (local/prod)\nH2 (demo/test riêng)','ws':'WebSocket / STOMP\nRealtimeEventPublisher\nPhát sự kiện sau commit','mail':'JavaMailSender → SMTP Gmail\nOTP sau commit','cloud':'Cloudinary\nẢnh món, logo, avatar, review','legacy':'Module tương thích\nDeliveryOrder / VNPAY\nKhông là checkout POS mới'}
for k,xy in nodes.items():box(d,xy,texts[k],27)
arrow(d,[(850,170),(850,245)]);arrow(d,[(430,330),(540,300)]);arrow(d,[(850,355),(850,485)]);arrow(d,[(850,595),(850,725)]);arrow(d,[(850,835),(850,1000)])
arrow(d,[(1170,540),(1230,540)]);arrow(d,[(1170,560),(1205,560),(1205,790),(1230,790)]);arrow(d,[(1170,510),(1205,510),(1205,330),(1230,330)]);arrow(d,[(430,730),(540,570)])
im.save(OUT/'Kien_Truc.png',dpi=(200,200))
(OUT/'Kien_Truc.mmd').write_text('flowchart TD\nBrowser[Trình duyệt Thymeleaf/JS] --> Security[Spring Security JWT/CSRF]\nSecurity --> Controller\nController --> Service\nService --> Repository\nRepository --> SQLServer[SQL Server hoặc H2 demo/test]\nService --> SMTP[JavaMailSender SMTP]\nService --> Cloudinary\nService --> Realtime[STOMP sau commit]\nService --> Legacy[DeliveryOrder và VNPAY tương thích]')
def er(name,rows,edges):
 W=1700;H=930;im=Image.new('RGB',(W,H),'white');d=ImageDraw.Draw(im);positions={}
 for i,(key,text) in enumerate(rows):
  col=i%3;row=i//3;x=35+col*555;y=55+row*280;positions[key]=(x,y,x+495,y+150)
  box(d,positions[key],text,25)
 for a,b,rel in edges:
  A=positions[a];B=positions[b]
  if B[1]>A[1]:points=[((A[0]+A[2])/2,A[3]),((B[0]+B[2])/2,B[1])]
  else:points=[(A[2],(A[1]+A[3])/2),(B[0],(B[1]+B[3])/2)]
  arrow(d,points);mid=((points[0][0]+points[-1][0])/2,(points[0][1]+points[-1][1])/2)
  d.rectangle((mid[0]-68,mid[1]-18,mid[0]+68,mid[1]+18),fill='white');label(d,(mid[0]-70,mid[1]-23,mid[0]+70,mid[1]+23),rel,20)
 im.save(OUT/(name+'.png'),dpi=(200,200))
 (OUT/(name+'.mmd')).write_text('erDiagram\n'+'\n'.join(f'  {a} ||--o{{ {b} : "{rel}"' for a,b,rel in edges))

def custom_er(name,canvas,nodes,edges):
 im=Image.new('RGB',canvas,'white');d=ImageDraw.Draw(im)
 for key,(xy,text) in nodes.items():box(d,xy,text,27)
 for points,relation,position in edges:
  arrow(d,points)
  x,y=position;d.rectangle((x-55,y-18,x+55,y+18),fill='white');label(d,(x-60,y-23,x+60,y+23),relation,22)
 im.save(OUT/(name+'.png'),dpi=(200,200))
N={
 'Account':((650,30,1150,170),'TaiKhoan\nPK MaTK'),
 'Shop':((50,30,550,170),'Shop\nPK Id'),
 'Order':((650,330,1150,470),'DonHang\nPK MaDH; FK MaTK, MaPhien, ShopId'),
 'Table':((50,330,550,470),'Ban\nPK MaBan; FK ShopId'),
 'Session':((50,630,550,770),'PhienBan\nPK MaPhien; FK MaBan'),
 'Invoice':((650,630,1150,770),'HoaDon\nPK MaHD; FK MaDH (unique)'),
 'Item':((1250,330,1750,470),'ChiTietDonHang\nPK MaChiTiet; FK MaDH, MaMon'),
 'Topping':((1250,630,1750,770),'ChiTietDonHangTopping\nFK MaChiTiet, MaMonTopping'),
 'Payment':((650,930,1150,1070),'ThanhToan\nPK MaTT; FK MaHD (unique)'),
 'Notice':((1250,930,1750,1070),'ThongBaoThanhToan\nFK MaHD')}
custom_er('ER_Don_Thanh_Toan',(1800,1120),N,[
 ([(900,170),(900,330)],'1 : N',(900,250)),
 ([(550,100),(650,400)],'1 : N',(600,250)),
 ([(300,470),(300,630)],'1 : N',(300,550)),
 ([(550,700),(600,700),(600,400),(650,400)],'1 : N',(600,555)),
 ([(900,470),(900,630)],'1 : 0..1',(900,550)),
 ([(1150,400),(1250,400)],'1 : N',(1200,400)),
 ([(1500,470),(1500,630)],'1 : N',(1500,550)),
 ([(900,770),(900,930)],'1 : 0..1',(900,850)),
 ([(1150,700),(1200,700),(1200,1000),(1250,1000)],'1 : N',(1200,850))])
(OUT/'ER_Don_Thanh_Toan.mmd').write_text('erDiagram\n  Account ||--o{ Order : MaTK\n  Shop ||--o{ Order : ShopId\n  DiningTable ||--o{ TableSession : MaBan\n  TableSession |o--o{ Order : MaPhien\n  Order ||--o| Invoice : MaDH\n  Order ||--o{ OrderItem : MaDH\n  OrderItem ||--o{ OrderItemTopping : MaChiTiet\n  Invoice ||--o| Payment : MaHD\n  Invoice ||--o{ PaymentNotice : MaHD')
N={
 'Material':((600,30,1100,170),'NguyenLieu\nPK MaNL; THO / SOCHE'),
 'Stock':((0,360,500,500),'TonNguyenLieu\nPK nhúng: nguyên liệu + vị trí'),
 'Movement':((600,360,1100,500),'LichSuKho\nFK MaNL'),
 'Recipe':((1200,360,1700,500),'CongThucMon\nFK MaMon, MaNL'),
 'Import':((0,680,500,820),'PhieuNhap\nChứng từ nhập nguyên liệu'),
 'ImportItem':((600,680,1100,820),'ChiTietNhap\nFK MaPhieu, MaNL'),
 'Snapshot':((1200,680,1700,820),'DinhMucDonHang\nFK đơn hàng và MaNL')}
custom_er('ER_Kho',(1800,900),N,[
 ([(850,170),(850,250),(250,250),(250,360)],'1 : N',(250,305)),
 ([(850,170),(850,360)],'1 : N',(850,280)),
 ([(1100,100),(1450,100),(1450,360)],'1 : N',(1450,250)),
 ([(1100,100),(1750,100),(1750,750),(1700,750)],'1 : N',(1750,590)),
 ([(500,750),(600,750)],'1 : N',(550,750))])
(OUT/'ER_Kho.mmd').write_text('erDiagram\n  Material ||--o{ Stock : MaNL\n  Material ||--o{ StockMovement : MaNL\n  Material ||--o{ ProductRecipe : MaNL\n  Material ||--o{ OrderIngredientSnapshot : MaNL\n  ImportReceipt ||--o{ ImportReceiptItem : MaPhieu')
print('Generated',len(F),'activity diagrams + architecture + verified ER subsets')
