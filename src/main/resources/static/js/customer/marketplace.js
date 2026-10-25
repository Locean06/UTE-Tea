(() => {
 const $=id=>document.getElementById(id),api='/api/v1/market',pub='/api/v1/public/market';
 const signedIn=!!document.body.dataset.accountId;
 let page=0,request=0,catalog=[],cart={version:'0',items:[]},carriers=[],orders=[];
 const labels={NEW:'Mới',CONFIRMED:'Đã xác nhận',SHIPPING:'Đang giao',DELIVERED:'Đã giao',DELIVERY_FAILED:'Giao thất bại',CANCELLED:'Đã hủy',RETURN_REQUESTED:'Yêu cầu trả hàng',RETURNED:'Đã trả hàng',REFUNDED:'Đã hoàn tiền'};
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
   const content=node('div',null,'card-body d-flex flex-column gap-2');content.append(node('h3',p.name+' · '+p.size,'h6'),node('p',p.shopName,'small text-muted mb-0'),node('strong',MilkTeaApi.formatVND(p.price)),node('p',p.rating.toFixed(1)+' ★ · '+p.favorites+' yêu thích','small mb-0'));
   content.append(button('Đánh giá',()=>showReviews(p.id)),button('♡ Yêu thích',async()=>{if(!signedIn)return login();await MilkTeaApi.put('/api/v1/account/favorites/'+p.id+'?enabled=true');message('Đã lưu vào yêu thích.');}));
   if(p.shopId){content.append(button(p.stock>0?'Thêm vào giỏ':'Hết hàng',async()=>{if(!signedIn)return login();if(p.stock<1)return;const line=cart.items.find(l=>l.productId===p.id);if(line)line.quantity++;else cart.items.push({productId:p.id,quantity:1,toppingIds:[]});await saveCart();if(signedIn)await MilkTeaApi.post('/api/v1/account/viewed/'+p.id);},'btn btn-primary-custom mt-auto'));}
   else {const a=node('a','Đặt nhận tại quầy','btn btn-outline-secondary mt-auto');a.href='/?product='+p.id+'#uteCatalog';content.append(a);}
   card.append(img,content);col.append(card);grid.append(col);
  }
  $('marketPage').textContent=`Trang ${page+1} / ${Math.max(1,Math.ceil(result.total/20))} · ${result.total} món`;
  $('marketPrevious').disabled=page===0;$('marketNext').disabled=(page+1)*20>=result.total;
 }
 function cartInput(){return{items:cart.items.map(l=>({productId:l.productId,quantity:l.quantity,note:l.note||'',toppingIds:l.toppingIds||[]}))};}
 async function loadCart(){cart=await MilkTeaApi.get('/api/v1/account/cart');renderCart();}
 async function saveCart(){try{cart=await MilkTeaApi.put('/api/v1/account/cart',cartInput(),{'If-Match':cart.version});}catch(e){await loadCart();throw e;}renderCart();}
 function renderCart(){
  const count=cart.items.reduce((sum,line)=>sum+line.quantity,0);document.querySelectorAll('#cartCountBadge,[data-cart-count]').forEach(badge=>{badge.textContent=count;badge.style.display=count?'inline-block':'none';});
  const list=$('deliveryCart');list.replaceChildren();const shops=new Map();
  for(const l of cart.items.filter(l=>l.shopId)){
   shops.set(l.shopId,l.shopName);const row=node('div',null,'border-bottom py-2');
   row.append(node('p',l.productName+' · '+l.shopName,'small mb-1'),node('strong',l.quantity+' × '+MilkTeaApi.formatVND(l.unitPrice),'small me-2'),button('−',async()=>{l.quantity--;if(l.quantity<=0)cart.items=cart.items.filter(x=>x!==l);await saveCart();}),button('+',async()=>{l.quantity++;await saveCart();}));list.append(row);
  }
  if(!shops.size)list.append(node('p','Chưa có món giao hàng.','text-muted small'));
  const selected=$('checkoutShop').value;$('checkoutShop').replaceChildren();shops.forEach((name,id)=>option($('checkoutShop'),id,name));if(shops.has(Number(selected)))$('checkoutShop').value=selected;
  renderCarriers();
 }
 function renderCarriers(){const id=Number($('checkoutShop').value);$('checkoutCarrier').replaceChildren();for(const c of carriers.filter(c=>!c.shopId||c.shopId===id))option($('checkoutCarrier'),c.id,c.name+' · '+MilkTeaApi.formatVND(c.fee));}
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
   if(o.paymentMethod==='VNPAY'&&!o.paid&&['NEW','CONFIRMED'].includes(o.status))card.append(button('Thanh toán VNPAY',async()=>{const result=await MilkTeaApi.post(api+'/orders/'+o.id+'/payment-url');location.href=result.url;}));
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
 $('checkoutShop').onchange=renderCarriers;$('deliveryStatus').onchange=renderOrders;$('refreshDeliveryOrders').onclick=()=>loadOrders().catch(e=>message(e.message,true));
 $('deliveryCheckout').addEventListener('submit',async event=>{
  event.preventDefault();if(!signedIn)return login();const submit=event.submitter;submit.disabled=true;let placedOrder=null;
  try{
   const shopId=Number($('checkoutShop').value),items=cart.items.filter(l=>l.shopId===shopId).map(l=>({productId:l.productId,quantity:l.quantity}));
   if(!items.length)throw Error('Chọn ít nhất một món giao hàng.');
   if(cart.items.some(l=>l.shopId===shopId&&l.toppingIds?.length))throw Error('Đơn giao hàng hiện chọn từng biến thể món, không kèm topping của POS.');
   const input={shopId,addressId:Number($('checkoutAddress').value),carrierId:Number($('checkoutCarrier').value),voucherCode:$('checkoutVoucher').value.trim().toUpperCase(),paymentMethod:$('checkoutMethod').value,items};
   const signature=JSON.stringify(input);let attempt=JSON.parse(sessionStorage.getItem('ute.deliveryAttempt')||'null');if(!attempt||attempt.signature!==signature){attempt={signature,key:crypto.randomUUID()};sessionStorage.setItem('ute.deliveryAttempt',JSON.stringify(attempt));}
   const order=await MilkTeaApi.post(api+'/orders',input,{'Idempotency-Key':attempt.key});
   placedOrder=order;message('Đã tạo đơn #'+order.id+' · Tổng '+MilkTeaApi.formatVND(order.totalAmount));
   cart.items=cart.items.filter(l=>l.shopId!==shopId);await saveCart();sessionStorage.removeItem('ute.deliveryAttempt');await loadOrders();await loadCatalog();
   if(order.paymentMethod==='VNPAY'){const result=await MilkTeaApi.post(api+'/orders/'+order.id+'/payment-url');location.href=result.url;}
  }catch(e){message((placedOrder?'Đơn #'+placedOrder.id+' đã tạo. ':'')+e.message,true);}finally{submit.disabled=false;}
 });
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
  const [shops,cats,shipping]=await Promise.all([MilkTeaApi.get(pub+'/shops'),MilkTeaApi.get('/api/v1/catalog/categories'),MilkTeaApi.get(pub+'/carriers')]);carriers=shipping;
  shops.forEach(s=>option($('marketShop'),s.id,s.name));cats.forEach(c=>option($('marketCategory'),c.id,c.name));
  const selected=new URLSearchParams(location.search).get('shop');if(selected)$('marketShop').value=selected;
  if(signedIn){await loadCart();const addresses=await MilkTeaApi.get('/api/v1/account/addresses');addresses.sort((a,b)=>Number(b.defaultAddress)-Number(a.defaultAddress)).forEach(a=>option($('checkoutAddress'),a.id,a.recipient+' · '+a.addressLine));await loadOrders();}
  else {$('deliveryCart').append(button('Đăng nhập để lưu giỏ',async()=>login()));renderOrders();}
  await loadCatalog();
  const params=new URLSearchParams(location.search),counterOrder=Number(params.get('counterOrder')),reviewProduct=Number(params.get('reviewProduct'));
  if(signedIn&&counterOrder&&reviewProduct){const f=$('reviewForm');f.dataset.delivery='false';f.elements.productId.value=reviewProduct;f.elements.orderId.value=counterOrder;$('reviewProductLabel').textContent='Đánh giá món trong đơn tại quầy #'+counterOrder;f.classList.remove('d-none');await showReviews(reviewProduct);}
 }
 init().catch(e=>message(e.message,true));
})();
