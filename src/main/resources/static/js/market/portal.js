(() => {
 const $=id=>document.getElementById(id),api='/api/v1/market';let user,shops=[],accounts=[];
 const states={NEW:'Mới',CONFIRMED:'Đã xác nhận',SHIPPING:'Đang giao',DELIVERED:'Đã giao',DELIVERY_FAILED:'Giao thất bại',CANCELLED:'Đã hủy',RETURN_REQUESTED:'Yêu cầu trả hàng',RETURNED:'Đã trả hàng',REFUNDED:'Đã hoàn tiền'};
 function node(tag,text,cls){const n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;}
 function message(text,error=false){$('portalMessage').className='alert '+(error?'alert-danger':'alert-success');$('portalMessage').textContent=text;}
 function button(text,fn){const b=node('button',text,'btn btn-sm btn-outline-secondary m-1');b.type='button';b.onclick=async()=>{b.disabled=true;try{await fn();}catch(e){message(e.message,true);}finally{b.disabled=false;}};return b;}
 function option(select,value,text){const o=node('option',text);o.value=value;select.append(o);}
 const shop=()=>Number($('portalShop').value)||null,query=()=>shop()?'?shopId='+shop():'';
 function requireShop(){if(!shop())throw Error('Vui lòng chọn một cửa hàng.');return shop();}
 function fill(form,data){for(const[key,value]of Object.entries(data)){const field=form.elements[key];if(!field)continue;if(field.type==='checkbox')field.checked=!!value;else field.value=value??'';}}
 function bind(id,save){const f=$(id);f.addEventListener('submit',async e=>{e.preventDefault();const b=e.submitter;b.disabled=true;try{await save(Object.fromEntries(new FormData(f)),e);message('Đã lưu.');await refresh();}catch(error){message(error.message,true);}finally{b.disabled=false;}});}
 async function loadShops(){
  shops=await MilkTeaApi.get(api+'/shops');const selected=$('portalShop').value;$('portalShop').replaceChildren();option($('portalShop'),'','Tất cả phạm vi được phép');shops.forEach(s=>option($('portalShop'),s.id,s.name+(s.active?'':' · Chờ kích hoạt')));if(shops.some(s=>String(s.id)===selected))$('portalShop').value=selected;
  else if(['VENDOR','MANAGER'].includes(user.role)&&shops.length)$('portalShop').value=shops[0].id;
 }
 async function refresh(){
  const selected=shops.find(s=>s.id===shop());if(selected){fill($('shopForm'),selected);fill($('shopConfigForm'),selected);}
  const [summary,orders]=await Promise.all([MilkTeaApi.get(api+'/summary'+query()),MilkTeaApi.get(api+'/orders'+query())]);
  $('portalSummary').textContent=`${summary.orders} đơn · ${summary.delivered} đã giao · ${summary.failed} giao thất bại · Thực thu đơn đã giao: ${MilkTeaApi.formatVND(summary.paidRevenue)} · Chiết khấu: ${MilkTeaApi.formatVND(summary.platformCommission)}`;
  renderOrders(orders);
  if(shop()&&['VENDOR','MANAGER','ADMIN'].includes(user.role)){
   const [categories,products]=await Promise.all([MilkTeaApi.get(api+'/shops/'+shop()+'/categories'),MilkTeaApi.get(api+'/shops/'+shop()+'/products')]);
   $('portalCategories').replaceChildren();$('productCategory').replaceChildren();
   categories.forEach(c=>{$('portalCategories').append(button(c.name+(c.active?'':' · Ngừng dùng'),async()=>fill($('categoryForm'),c)));option($('productCategory'),c.id,c.name);});
   $('portalProducts').replaceChildren();products.forEach(p=>{const col=node('div',null,'col-12 col-md-6'),card=node('div',null,'border rounded p-3 h-100 text-break');card.append(node('strong',p.name+' · '+p.size),node('p',MilkTeaApi.formatVND(p.price)+' · Tồn '+p.stock,'small mb-1'),button('Sửa món',async()=>fill($('productForm'),p)));col.append(card);$('portalProducts').append(col);});
   const customers=await MilkTeaApi.get(api+'/shops/'+shop()+'/customers');$('portalCustomers').replaceChildren();customers.forEach(c=>{const row=node('div',null,'border-bottom py-2 text-break');row.append(node('span','#'+c.id+' · '+c.email),button(c.active?'Hạn chế tại shop':'Cho mua tại shop',async()=>{await MilkTeaApi.put(api+'/shops/'+shop()+'/customers/'+c.id+'?enabled='+!c.active);await refresh();}));$('portalCustomers').append(row);});
  }else{$('portalCategories').replaceChildren();$('portalProducts').replaceChildren();$('productCategory').replaceChildren();}
  if(['VENDOR','MANAGER','ADMIN'].includes(user.role)&&(shop()||user.role==='ADMIN')){
   const vouchers=await MilkTeaApi.get(api+'/vouchers'+query());$('portalVouchers').replaceChildren();vouchers.forEach(v=>{const row=node('div',null,'border-bottom py-2 text-break');row.append(node('span',v.code+' · Giảm '+MilkTeaApi.formatVND(v.discountAmount)+' · '+v.usedCount+'/'+v.usageLimit+' lượt · '+(v.active?'Hoạt động':'Đã tắt')));if(v.active)row.append(button('Tắt mã',async()=>{await MilkTeaApi.delete(api+'/vouchers/'+v.id);await refresh();}));$('portalVouchers').append(row);});
   const carriers=await MilkTeaApi.get(api+'/carriers'+query());$('portalCarriers').replaceChildren();carriers.forEach(c=>{$('portalCarriers').append(button(c.name+' · '+MilkTeaApi.formatVND(c.fee)+(c.active?'':' · Ngừng dùng'),async()=>fill($('carrierForm'),c)));});
  }
 }
 function renderOrders(orders){
  $('portalOrders').replaceChildren();if(!orders.length)$('portalOrders').append(node('p','Chưa có đơn trong phạm vi này.'));
  for(const o of orders){
   const col=node('div',null,'col-12 col-lg-6'),card=node('article',null,'card p-3 h-100 text-break');
   card.append(node('h3','#'+o.id+' · '+o.shopName,'h6'),node('p',states[o.status]+' · '+MilkTeaApi.formatVND(o.totalAmount)+' · '+(o.paid?'Đã thu tiền':'Chưa thu tiền'),'small'),node('p',o.recipient+' · '+o.phone+' · '+o.addressLine,'small text-muted'));
   o.items.forEach(l=>card.append(node('p',l.quantity+' × '+l.name,'small mb-1')));
   const seller=['VENDOR','MANAGER','ADMIN'].includes(user.role),shipper=['SHIPPER','ADMIN'].includes(user.role);
   async function transition(status,reference){await MilkTeaApi.put(api+'/orders/'+o.id+'/status',{status,version:o.version,reference});await refresh();}
   if(seller&&o.status==='NEW')card.append(button('Xác nhận',()=>transition('CONFIRMED')));
   if(seller&&['NEW','CONFIRMED','DELIVERY_FAILED'].includes(o.status))card.append(button('Hủy và hoàn tồn',()=>transition('CANCELLED')));
   if(seller&&o.status==='RETURN_REQUESTED')card.append(button('Đã nhận hàng trả',()=>transition('RETURNED')));
   if(shipper&&o.status==='CONFIRMED')card.append(button('Bắt đầu giao',()=>transition('SHIPPING')));
   if(shipper&&o.status==='SHIPPING')card.append(button('Giao thành công',()=>transition('DELIVERED')),button('Giao thất bại',()=>transition('DELIVERY_FAILED')));
   if(seller&&o.status==='CONFIRMED'){
    const form=node('form',null,'d-flex flex-wrap gap-2 mt-2'),input=node('input',null,'form-control');input.type='number';input.required=true;input.min='1';input.placeholder='Mã tài khoản Shipper';input.setAttribute('aria-label','Mã tài khoản Shipper');input.style.maxWidth='220px';const submit=node('button','Phân công','btn btn-outline-secondary');submit.type='submit';form.append(input,submit);form.onsubmit=async e=>{e.preventDefault();submit.disabled=true;try{await MilkTeaApi.put(api+'/orders/'+o.id+'/shipper',{accountId:Number(input.value),version:o.version});await refresh();}catch(error){message(error.message,true);}finally{submit.disabled=false;}};card.append(form,node('small',o.shipperId?'Shipper #'+o.shipperId:'Chưa phân công shipper','text-muted'));
   }
   if(user.role==='ADMIN'&&o.paid&&['RETURNED','CANCELLED'].includes(o.status)){
    if(o.paymentMethod==='VNPAY')card.append(node('p','Hoàn tiền VNPAY: cần xác nhận từ cổng thanh toán; không đánh dấu hoàn bằng nút tiền mặt.','small text-muted'));
    else {const reference=node('input',null,'form-control my-2');reference.placeholder='Tham chiếu phiếu hoàn tiền thực tế';reference.maxLength=120;reference.setAttribute('aria-label','Tham chiếu hoàn tiền');card.append(reference,button('Ghi nhận đã hoàn tiền mặt',()=>transition('REFUNDED',reference.value)));}
   }
   col.append(card);$('portalOrders').append(col);
  }
 }
 bind('shopForm',async(data,e)=>{if(e.submitter.value==='edit')await MilkTeaApi.put(api+'/shops/'+requireShop(),data);else await MilkTeaApi.post(api+'/shops',data);user=await MilkTeaApi.get('/api/v1/auth/me');await loadShops();applyRoles();});
 bind('shopConfigForm',async data=>{const selected=shops.find(s=>s.id===requireShop());await MilkTeaApi.put(api+'/shops/'+requireShop()+'/config',{active:user.role==='ADMIN'?!!data.active:selected.active,commissionPercent:Number(data.commissionPercent),managerCommissionEnabled:!!data.managerCommissionEnabled});await loadShops();});
 bind('managerForm',async(data,e)=>MilkTeaApi.put(api+'/shops/'+requireShop()+'/managers/'+data.accountId+'?enabled='+e.submitter.value));
 bind('categoryForm',async data=>{await MilkTeaApi[data.id?'put':'post'](api+'/shops/'+requireShop()+'/categories'+(data.id?'/'+data.id:''),{name:data.name,active:!!data.active});$('categoryForm').reset();});
 bind('productForm',async data=>{const p={categoryId:Number(data.categoryId),name:data.name,size:data.size,description:data.description,price:Number(data.price),stock:Number(data.stock),active:!!data.active,catalogVersion:data.catalogVersion?Number(data.catalogVersion):null};const saved=await MilkTeaApi[data.id?'put':'post'](api+'/shops/'+requireShop()+'/products'+(data.id?'/'+data.id:''),p);$('productForm').elements.id.value=saved.id;$('productForm').elements.catalogVersion.value=saved.catalogVersion;const file=$('productImage').files[0];if(file){const body=new FormData();body.append('file',file);await MilkTeaApi.upload(api+'/shops/'+requireShop()+'/products/'+saved.id+'/image',body);$('productImage').value='';}});
 bind('voucherForm',async data=>{await MilkTeaApi.post(api+'/vouchers'+query(),{...data,discountAmount:Number(data.discountAmount),minimumAmount:Number(data.minimumAmount),usageLimit:Number(data.usageLimit),perAccountLimit:Number(data.perAccountLimit),startsAt:new Date(data.startsAt).toISOString(),endsAt:new Date(data.endsAt).toISOString(),active:true});$('voucherForm').reset();});
 bind('carrierForm',async data=>{await MilkTeaApi[data.id?'put':'post'](api+'/carriers'+(data.id?'/'+data.id:'')+query(),{name:data.name,fee:Number(data.fee),active:!!data.active});$('carrierForm').reset();});
 function applyRoles(){document.querySelectorAll('[data-roles]').forEach(e=>e.classList.toggle('d-none',!e.dataset.roles.split(' ').includes(user.role)));$('portalRole').textContent=user.fullName+' · '+user.role;}
 async function loadAccounts(){
  if(user.role!=='ADMIN')return;accounts=await MilkTeaApi.get(api+'/accounts');$('portalAccounts').replaceChildren();$('managerAccount').replaceChildren();
  for(const a of accounts){if(a.role==='MANAGER')option($('managerAccount'),a.id,a.email+' · #'+a.id);const row=node('div',null,'border-bottom py-2 text-break');row.append(node('p','#'+a.id+' · '+a.email+' · '+a.role,'small mb-2'));
   if(a.role!=='ADMIN'&&a.id!==user.id){const select=node('select',null,'form-select mb-2');select.setAttribute('aria-label','Vai trò của '+a.email);['CUSTOMER','VENDOR','MANAGER','SHIPPER','CASHIER','KITCHEN','ADMIN'].forEach(r=>option(select,r,r));select.value=a.role;
    row.append(select,button('Lưu vai trò',async()=>{await MilkTeaApi.put(api+'/accounts/'+a.id,{role:select.value,active:a.active});await loadAccounts();}),button(a.active?'Khóa tài khoản':'Mở khóa',async()=>{await MilkTeaApi.put(api+'/accounts/'+a.id,{role:a.role,active:!a.active});await loadAccounts();}));
   }$('portalAccounts').append(row);
  }
 }
 $('portalShop').onchange=()=>{for(const id of ['categoryForm','productForm','carrierForm'])$(id).reset();refresh().catch(e=>message(e.message,true));};$('portalRefresh').onclick=()=>refresh().catch(e=>message(e.message,true));
 (async()=>{user=await MilkTeaApi.get('/api/v1/auth/me');applyRoles();await loadShops();await loadAccounts();await refresh();})().catch(e=>message(e.message,true));
})();
