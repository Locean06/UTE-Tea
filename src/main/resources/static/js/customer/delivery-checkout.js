/* Reuses account cart/address APIs; no branch is saved in the cart. */
const DeliveryCheckout = (() => {
    const $=id=>document.getElementById(id),signedIn=!!document.body.dataset.accountId;
    let branches=[],carriers=[],quote=null,request=0,busy=false,ready=false;
    function node(tag,text,cls){const n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;}
    function message(text,error=false){$('checkoutMessage').className='alert '+(error?'alert-danger':'alert-success');$('checkoutMessage').textContent=text;}
    function option(select,value,text){const o=node('option',text);o.value=value;select.append(o);}
    function items(){return CustomerApp.getLocalCart();}
    function mode(){
        const delivery=$('fulfillmentMode').value==='DELIVERY';$('deliveryFields').classList.toggle('d-none',!delivery);$('counterCheckout').classList.toggle('d-none',delivery);
        const summary=$('cartSummary');if(summary)summary.classList.toggle('d-none',delivery);
    }
    function invalidate(){quote=null;$('deliverySubmit').disabled=true;}
    async function loadAddresses(selected){
        const addresses=await MilkTeaApi.get('/api/v1/account/addresses');$('checkoutAddress').replaceChildren();option($('checkoutAddress'),'','Chọn địa chỉ nhận hàng');
        addresses.sort((a,b)=>Number(b.defaultAddress)-Number(a.defaultAddress)).forEach(a=>option($('checkoutAddress'),a.id,a.recipient+' · '+a.addressLine));
        const preferred=selected||addresses.find(a=>a.defaultAddress)?.id;if(preferred)$('checkoutAddress').value=preferred;
        await loadBranches(false);
    }
    async function loadBranches(retain=true){
        const current=++request;invalidate();const selected=retain?$('checkoutShop').value:'';
        const address=$('checkoutAddress').value;$('checkoutShop').disabled=true;$('checkoutShop').replaceChildren();option($('checkoutShop'),'','Chọn cơ sở phục vụ');
        $('checkoutCarrier').replaceChildren();$('checkoutCarrier').disabled=true;$('branchAvailability').replaceChildren();
        $('branchQuote').textContent='Chọn cơ sở để xem tổng tiền chính xác.';
        if(!signedIn||!address||!items().length)return;
        try {
            const result=await MilkTeaApi.get('/api/v1/account/cart/branches?addressId='+encodeURIComponent(address));if(current!==request)return;branches=result;
            for(const b of branches){const proximity=b.distanceKm!=null?' · '+b.distanceKm.toLocaleString('vi-VN',{maximumFractionDigits:2})+' km':'';option($('checkoutShop'),b.id,b.name+(b.nearest?' · Gần bạn nhất':'')+proximity+(b.canServe?'':' · Không đủ món'));}
            $('checkoutShop').disabled=!branches.length;
            $('branchRankingHelp').textContent=branches.some(b=>b.rankingMethod==='COORDINATES')?'Khoảng cách đường chim bay từ tọa độ đã lưu. Cơ sở chưa có tọa độ xếp sau các khoảng cách đã biết.':branches.some(b=>b.rankingMethod==='AREA')?'Ưu tiên cơ sở cùng khu vực đã nhập; chưa có số km để so sánh chi tiết.':'Chưa đủ tọa độ/khu vực để xác định gần nhất. Danh sách theo tên; bạn tự chọn cơ sở.';
            if(branches.some(b=>String(b.id)===selected))$('checkoutShop').value=selected;
            await chooseBranch();
        } catch(e){if(current===request)message(e.message,true);}
    }
    async function chooseBranch(){
        ++request;invalidate();const b=branches.find(b=>String(b.id)===$('checkoutShop').value);$('branchAvailability').replaceChildren();$('checkoutCarrier').replaceChildren();$('checkoutCarrier').disabled=true;
        if(!b)return;
        $('branchAvailability').append(node('p',b.address,'mb-1'));
        if(!b.canServe){for(const issue of b.issues)$('branchAvailability').append(node('p','• '+issue,'text-danger mb-1'));$('branchQuote').textContent='Cơ sở này chưa phục vụ đầy đủ giỏ hàng. Vui lòng chọn cơ sở khác.';return;}
        for(const carrier of carriers.filter(c=>!c.shopId||c.shopId===b.id))option($('checkoutCarrier'),carrier.id,carrier.name+' · '+MilkTeaApi.formatVND(carrier.fee));
        $('checkoutCarrier').disabled=!$('checkoutCarrier').options.length;await refreshQuote();
    }
    async function refreshQuote(){
        const current=++request;invalidate();
        if(!$('checkoutShop').value||!$('checkoutCarrier').value)return;
        try{
            const result=await MilkTeaApi.post('/api/v1/account/cart/quote',{shopId:Number($('checkoutShop').value),addressId:Number($('checkoutAddress').value),carrierId:Number($('checkoutCarrier').value),voucherCode:$('checkoutVoucher').value.trim().toUpperCase()});
            if(current!==request)return;quote=result;renderQuote();$('deliverySubmit').disabled=busy;
        }catch(e){if(current===request){$('branchQuote').textContent=e.message;message(e.message,true);}}
    }
    function renderQuote(){
        $('branchQuote').replaceChildren();
        for(const line of quote.items||[]){$('branchQuote').append(node('p',(line.topping?'+ ':'')+line.name+' · '+line.size+' · '+line.quantity+' × '+MilkTeaApi.formatVND(line.price),'small text-break mb-2'));}
        for(const[label,value]of Object.entries({'Tiền món theo cơ sở':quote.subtotal,'Phí giao hàng':quote.shippingFee,'Giảm giá':-quote.discountAmount,'Tổng thanh toán':quote.totalAmount})){
            const row=node('div',null,'d-flex justify-content-between gap-2 mb-2');row.append(node('span',label),node('strong',MilkTeaApi.formatVND(value)));$('branchQuote').append(row);
        }
    }
    async function submit(event){
        event.preventDefault();if(!signedIn||!quote||busy)return;busy=true;$('deliverySubmit').disabled=true;let placed=null;
        try{
            const shown=quote;
            await refreshQuote();
            if(!quote)return;
            if(quote.totalAmount!==shown.totalAmount||quote.cartVersion!==shown.cartVersion){message('Giỏ, giá hoặc phí đã thay đổi. Vui lòng kiểm tra tổng tiền mới rồi xác nhận lại.',true);return;}
            const input={shopId:quote.shopId,addressId:Number($('checkoutAddress').value),carrierId:Number($('checkoutCarrier').value),voucherCode:$('checkoutVoucher').value.trim().toUpperCase(),paymentMethod:$('checkoutMethod').value,expectedTotal:quote.totalAmount,cartVersion:quote.cartVersion,
                items:items().map(l=>({productId:l.productId,quantity:l.quantity,note:l.note||'',toppingIds:l.toppingIds||[]}))};
            const signature=JSON.stringify(input);let attempt;
            try{attempt=JSON.parse(sessionStorage.getItem('ute.branchCheckoutAttempt')||'null');}catch(_){attempt=null;}
            if(!attempt||attempt.signature!==signature){attempt={signature,key:crypto.randomUUID()};sessionStorage.setItem('ute.branchCheckoutAttempt',JSON.stringify(attempt));}
            placed=await MilkTeaApi.post('/api/v1/market/orders',input,{'Idempotency-Key':attempt.key});
            await CustomerApp.clearCart();sessionStorage.removeItem('ute.branchCheckoutAttempt');
            message('Đã tạo đơn #'+placed.id+' tại '+placed.shopName+'. Tổng '+MilkTeaApi.formatVND(placed.totalAmount));
            const link=node('a',' Xem đơn giao hàng','alert-link');link.href='/marketplace#deliveryOrders';$('checkoutMessage').append(link);
            document.dispatchEvent(new CustomEvent('ute:delivery-placed',{detail:placed}));
            if(placed.paymentMethod==='VNPAY'){const result=await MilkTeaApi.post('/api/v1/market/orders/'+placed.id+'/payment-url');location.href=result.url;}
        }catch(e){message((placed?'Đơn #'+placed.id+' đã tạo. ':'')+e.message,true);if(!placed)await loadBranches();}
        finally{busy=false;$('deliverySubmit').disabled=!quote;}
    }
    async function init(){
        if(!$('branchCheckout'))return;await CustomerApp.initContext();
        $('fulfillmentMode').onchange=mode;$('checkoutAddress').onchange=()=>loadBranches(false);$('checkoutShop').onchange=()=>chooseBranch().catch(e=>message(e.message,true));
        $('checkoutCarrier').onchange=refreshQuote;$('applyCheckoutVoucher').onclick=refreshQuote;$('deliveryCheckout').onsubmit=submit;
        $('checkoutVoucher').oninput=()=>invalidate();
        $('deliveryLogin').classList.toggle('d-none',signedIn);$('newAddressDetails').hidden=!signedIn;
        if(signedIn){carriers=await MilkTeaApi.get('/api/v1/public/market/carriers');await loadAddresses();}
        else {$('checkoutAddress').disabled=true;}
        $('checkoutAddressForm').onsubmit=async event=>{
            event.preventDefault();const form=event.target,button=event.submitter;button.disabled=true;
            try{
                const data=Object.fromEntries(new FormData(form));for(const key of ['latitude','longitude'])data[key]=data[key]===''?null:Number(data[key]);data.defaultAddress=form.elements.defaultAddress.checked;
                const saved=await MilkTeaApi.post('/api/v1/account/addresses',data);form.reset();$('newAddressDetails').open=false;await loadAddresses(saved.id);
            }catch(e){message(e.message,true);}finally{button.disabled=false;}
        };
        ready=true;mode();
    }
    document.addEventListener('DOMContentLoaded',()=>init().catch(e=>message(e.message,true)));
    document.addEventListener('ute:cart-changed',event=>{if(ready){mode();if(event.detail?.pending){++request;invalidate();}else if(signedIn)loadBranches();}});
    return {refresh:()=>loadBranches()};
})();
