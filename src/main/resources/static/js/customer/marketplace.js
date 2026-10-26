(() => {
 const $=id=>document.getElementById(id),api='/api/v1/market',pub='/api/v1/public/market';
 const signedIn=!!document.body.dataset.accountId;
 let page=0,request=0,catalog=[],orders=[];
 const labels={NEW:'Mới',CONFIRMED:'Đã xác nhận',PICKED_UP:'Đã lấy hàng',SHIPPING:'Đang giao',DELIVERED:'Đã giao',DELIVERY_FAILED:'Giao thất bại',CANCELLED:'Đã hủy',RETURN_REQUESTED:'Yêu cầu trả hàng',RETURNED:'Đã trả hàng',REFUNDED:'Đã hoàn tiền'};
 function node(tag,text,cls){const n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;}
 function message(text,error=false){$('marketMessage').className='alert '+(error?'alert-danger':'alert-success');$('marketMessage').textContent=text;}
 function button(text,fn,cls='btn btn-sm btn-outline-secondary'){const b=node('button',text,cls);b.type='button';b.addEventListener('click',async()=>{b.disabled=true;try{await fn();}catch(e){message(e.message,true);}finally{b.disabled=false;}});return b;}
 function option(select,value,text){const o=node('option',text);o.value=value;select.append(o);}
 function login(){location.href='/login?redirect=/marketplace';}
 async function loadCatalog(){
  const current=++request,params=new URLSearchParams(new FormData($('marketFilters')));for(const[k,v]of [...params])if(!v)params.delete(k);params.set('page',page);params.set('size','20');
  const result=await MilkTeaApi.get(pub+'/catalog?'+params);if(current!==request)return;catalog=result.items;
  const grid=$('marketProducts');grid.replaceChildren();if(!catalog.length)grid.append(node('p','Chưa có món phù hợp.'));
  for(const p of catalog){
   const col=node('div',null,'col'),card=node('article',null,'card h-100 overflow-hidden');
   const img=node('img');img.src=p.imageUrl||'/images/ute-tea-product-fallback.svg';img.alt=p.name;img.loading='lazy';img.style.cssText='width:100%;aspect-ratio:1;object-fit:cover';img.onerror=()=>{img.onerror=null;img.src='/images/ute-tea-product-fallback.svg';};
   const content=node('div',null,'card-body d-flex flex-column gap-2');
   const title=node('a',p.name,'h6 text-decoration-none');title.href='#productReviews';title.onclick=e=>{e.preventDefault();showReviews(p.id).catch(e=>message(e.message,true));};
   content.append(title,node('span','Size '+p.size,'small text-muted'),node('strong',MilkTeaApi.formatVND(p.price)));
   content.append(button('♡ Yêu thích',async()=>{if(!signedIn)return login();await MilkTeaApi.put('/api/v1/account/favorites/'+p.id+'?enabled=true');message('Đã lưu vào yêu thích.');}));
   content.append(button('Thêm',()=>MenuSelection.open(p.id),'btn btn-primary-custom mt-auto'));
   card.append(img,content);col.append(card);grid.append(col);
  }
  $('marketPage').textContent=`Trang ${page+1} / ${Math.max(1,Math.ceil(result.total/20))} · ${result.total} món`;
  $('marketPrevious').disabled=page===0;$('marketNext').disabled=(page+1)*20>=result.total;
 }
 function renderCart(){
  const list=$('deliveryCart');list.replaceChildren();const items=CustomerApp.getLocalCart();
  if(!items.length)list.append(node('p','Giỏ hàng đang trống.','text-muted small'));
  items.forEach((line,index)=>{
   const row=node('div',null,'border-bottom py-3 text-break');
   row.append(node('strong',line.productName+' · Size '+line.size),node('p',(line.toppings||[]).map(t=>'+ '+t.name).join(', '),'small text-muted mb-1'));
   if(line.note)row.append(node('p',line.note,'small text-muted mb-1'));
   const controls=node('div',null,'d-flex flex-wrap align-items-center gap-2');
   controls.append(button('−',()=>CustomerApp.updateQuantity(index,-1)),node('span',line.quantity),button('+',()=>CustomerApp.updateQuantity(index,1)),node('strong',MilkTeaApi.formatVND(line.lineTotal),'ms-auto'),button('Sửa',()=>MenuSelection.open(line.productId,index)),button('Xóa',()=>CustomerApp.removeItem(index)));row.append(controls);list.append(row);
  });
 }
 async function showReviews(id){
  if(signedIn)await MilkTeaApi.post('/api/v1/account/viewed/'+id);
  const reviews=await MilkTeaApi.get(pub+'/products/'+id+'/reviews');const list=$('reviewList');list.replaceChildren();
  if(!reviews.length)list.append(node('p','Chưa có đánh giá. Chỉ khách đã nhận hàng và thanh toán mới có thể đánh giá.'));
  for(const r of reviews){const row=node('article',null,'border-bottom py-3 text-break');row.append(node('strong',r.stars+' ★'),node('p',r.comment,'mb-2'));
   for(const m of r.media){const media=node(m.type==='video'?'video':'img');media.src=m.url;media.style.cssText='max-width:100%;width:180px;border-radius:10px;margin:4px';if(m.type==='video'){media.controls=true;media.preload='metadata';}else{media.alt='Ảnh đánh giá';media.loading='lazy';}row.append(media);}list.append(row);
  }
  $('productReviews').scrollIntoView({behavior:'smooth',block:'start'});
 }
 async function loadOrders(){if(!signedIn)return;orders=await MilkTeaApi.get(api+'/orders');renderOrders();}
 function renderOrders(){
  const list=$('deliveryOrders');list.replaceChildren();const selected=$('deliveryStatus').value;
  for(const o of orders.filter(o=>!selected||o.status===selected)){
   const col=node('div',null,'col-12 col-lg-6'),card=node('article',null,'card p-3 h-100 text-break');
   card.append(node('h3','#'+o.id+' · '+o.shopName,'h6'),node('p',labels[o.status]+' · '+MilkTeaApi.formatVND(o.totalAmount)+' · '+(o.paid?'Đã thu tiền':'Chưa thu tiền'),'small'),node('p',o.recipient+' · '+o.phone+' · '+o.addressLine,'small text-muted'));
   for(const l of o.items)card.append(node('p',l.quantity+' × '+l.name,'small mb-1'));
   const transition=async status=>{await MilkTeaApi.put(api+'/orders/'+o.id+'/status',{status,version:o.version});await loadOrders();await loadCatalog();};
   if(o.status==='NEW')card.append(button('Hủy đơn',()=>transition('CANCELLED')));
   if(o.paymentMethod==='VNPAY'&&!o.paid&&['NEW','CONFIRMED','PICKED_UP'].includes(o.status))card.append(button('Thanh toán VNPAY',async()=>{const result=await MilkTeaApi.post(api+'/orders/'+o.id+'/payment-url');location.href=result.url;}));
   if(o.status==='DELIVERED'){
    card.append(button('Yêu cầu trả hàng',()=>transition('RETURN_REQUESTED')));
    for(const l of o.items)card.append(button('Đánh giá '+l.name,async()=>{const f=$('reviewForm');f.reset();f.dataset.delivery='true';f.elements.productId.value=l.productId;f.elements.orderId.value=o.id;$('reviewProductLabel').textContent='Đánh giá '+l.name;f.classList.remove('d-none');await showReviews(l.productId);}));
   }
   col.append(card);list.append(col);
  }
  if(!list.children.length)list.append(node('p',signedIn?'Chưa có đơn giao hàng phù hợp.':'Đăng nhập để xem lịch sử đơn giao hàng.'));
 }
 $('marketFilters').addEventListener('submit',e=>{e.preventDefault();page=0;loadCatalog().catch(e=>message(e.message,true));});
 $('marketPrevious').onclick=()=>{page--;loadCatalog().catch(e=>message(e.message,true));};$('marketNext').onclick=()=>{page++;loadCatalog().catch(e=>message(e.message,true));};
 $('deliveryStatus').onchange=renderOrders;$('refreshDeliveryOrders').onclick=()=>loadOrders().catch(e=>message(e.message,true));
 document.addEventListener('ute:cart-changed',renderCart);
 document.addEventListener('ute:delivery-placed',()=>{loadOrders().catch(e=>message(e.message,true));loadCatalog().catch(e=>message(e.message,true));});
 $('reviewForm').addEventListener('submit',async event=>{
  event.preventDefault();const form=event.target,submit=event.submitter;submit.disabled=true;
  try{
   let reviewId=form.elements.reviewId.value;
   if(!reviewId){const r=await MilkTeaApi.post(api+'/products/'+form.elements.productId.value+'/reviews',{orderId:Number(form.elements.orderId.value),delivery:form.dataset.delivery!=='false',stars:Number(form.elements.stars.value),comment:form.elements.comment.value});reviewId=r.id;form.elements.reviewId.value=reviewId;}
   const file=form.elements.file.files[0];if(file){const data=new FormData();data.append('file',file);await MilkTeaApi.upload(api+'/reviews/'+reviewId+'/media',data);form.elements.file.value='';}
   message('Đã lưu đánh giá.');await showReviews(Number(form.elements.productId.value));
  }catch(e){message(e.message,true);}finally{submit.disabled=false;}
 });
 async function init(){
  const cats=await MilkTeaApi.get('/api/v1/catalog/categories');const names=new Set();cats.forEach(c=>{if(!names.has(c.name)){names.add(c.name);option($('marketCategory'),c.id,c.name);}});
  await CustomerApp.initContext();renderCart();if(signedIn)await loadOrders();else renderOrders();
  await loadCatalog();
  const params=new URLSearchParams(location.search),counterOrder=Number(params.get('counterOrder')),reviewProduct=Number(params.get('reviewProduct'));
  if(signedIn&&counterOrder&&reviewProduct){const f=$('reviewForm');f.dataset.delivery='false';f.elements.productId.value=reviewProduct;f.elements.orderId.value=counterOrder;$('reviewProductLabel').textContent='Đánh giá món trong đơn tại quầy #'+counterOrder;f.classList.remove('d-none');await showReviews(reviewProduct);}
 }
 init().catch(e=>message(e.message,true));
})();
