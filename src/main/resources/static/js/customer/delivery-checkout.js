/* Reuses account cart/address APIs; no branch is saved in the cart. */
const DeliveryCheckout = (() => {
    const $=id=>document.getElementById(id),signedIn=!!document.body.dataset.accountId;
    let branches=[],carriers=[],quote=null,request=0,busy=false,ready=false,selectedCarrier=null,completedOrder=null,checkoutAttempt=null;
    function node(tag,text,cls){const n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;}
    function message(text,error=false){$('checkoutMessage').className='alert '+(error?'alert-danger':'alert-success');$('checkoutMessage').textContent=text;}
    function clearMessage(){$('checkoutMessage').className='alert d-none';$('checkoutMessage').textContent='';}
    function errorText(error){return (error?.message||'Không kết nối được hệ thống.')+(error?.traceId?' (Mã lỗi: '+error.traceId+')':'');}
    function option(select,value,text){const o=node('option',text);o.value=value;select.append(o);}
    function items(){return CustomerApp.getLocalCart();}
    function mode(){
        const delivery=$('fulfillmentMode').value==='DELIVERY';$('deliveryFields').classList.toggle('d-none',!delivery);$('counterCheckout').classList.toggle('d-none',delivery);
        const summary=$('cartSummary');if(summary)summary.classList.toggle('d-none',delivery);
        syncCounterButton();
    }
    function syncCounterButton(){
        const button=$('checkoutBtn');
        if(button) button.disabled=!$('counterShop')?.value||!items().length;
    }
    async function loadPickupBranches(){
        const select=$('counterShop');
        if(!select)return;
        const previous=select.value;
        select.disabled=true;
        select.replaceChildren();option(select,'','Chọn chi nhánh đến lấy món');
        $('counterShopNotice').textContent='Đang tải chi nhánh...';
        syncCounterButton();
        try{
            const all=await MilkTeaApi.get('/api/v1/public/market/shops');
            const active=(Array.isArray(all)?all:[]).filter(s=>s.active);
            active.forEach(s=>option(select,s.id,s.name));
            select.disabled=!active.length;
            if(active.some(s=>String(s.id)===previous))select.value=previous;
            $('counterShopNotice').textContent=active.length
                ? 'Chọn nơi bạn đến nhận. Không tính phí giao hàng.'
                : 'Hiện không có chi nhánh nhận đơn. Vui lòng thử lại sau.';
        }catch(e){
            $('counterShopNotice').textContent='Không tải được danh sách chi nhánh: '+errorText(e);
        }
        syncCounterButton();
    }
    function invalidate(){quote=null;$('deliverySubmit').disabled=true; const dialog=$('deliveryPaymentDialog'); if(dialog?.open&&!busy)dialog.close();}
    function clearDeliveryInfo(){selectedCarrier=null;$('checkoutDeliveryInfo').textContent='';}
    function eligibleCarriers(branchId){return carriers.filter(c=>!c.shopId||String(c.shopId)===String(branchId));}
    function pickCarrier(branch){
        // Prefer internal UTE Tea delivery, never silently select a third-party shipping partner.
        const options=eligibleCarriers(branch.id).filter(c=>/(ute tea|nội bộ|noi bo|nhân viên|nhan vien|trực tiếp|truc tiep)/i.test(c.name));
        return options.find(c=>String(c.shopId)===String(branch.id))||options.find(c=>!c.shopId)||null;
    }
    function renderDeliveryInfo(branch,carrier){
        $('checkoutDeliveryInfo').textContent='Nhân viên UTE Tea giao · Phí giao hàng: '+MilkTeaApi.formatVND(carrier.fee);
    }
    async function loadAddresses(selected){
        const addresses=await MilkTeaApi.get('/api/v1/account/addresses');$('checkoutAddress').replaceChildren();option($('checkoutAddress'),'','Chọn địa chỉ nhận hàng');
        addresses.sort((a,b)=>Number(b.defaultAddress)-Number(a.defaultAddress)).forEach(a=>option($('checkoutAddress'),a.id,a.recipient+' · '+a.addressLine));
        const preferred=selected||addresses.find(a=>a.defaultAddress)?.id||addresses[0]?.id;
        if(preferred)$('checkoutAddress').value=String(preferred);
        $('selectedAddressPreview').textContent=addresses.length
            ? (addresses.find(a=>String(a.id)===$('checkoutAddress').value)?.phone||'')+' · '+(addresses.find(a=>String(a.id)===$('checkoutAddress').value)?.addressLine||'')
            : 'Chưa có địa chỉ nào. Nhấn “Thêm địa chỉ giao hàng” để bắt đầu.';
        $('newAddressDetails').open=!addresses.length&&signedIn;
        await loadBranches(false);
    }
    async function loadBranches(retain=true){
        const current=++request;invalidate();const selected=retain?$('checkoutShop').value:'';
        const address=$('checkoutAddress').value;
        // The recipient preview always tracks the selected saved address.
        const selectedAddress=[...$('checkoutAddress').options].find(o=>o.value===address);
        if(selectedAddress && address) $('selectedAddressPreview').textContent=selectedAddress.textContent;
        $('checkoutShop').disabled=true;$('checkoutShop').replaceChildren();option($('checkoutShop'),'','Chọn Chi nhánh phục vụ');$('branchAvailability').replaceChildren();
        clearDeliveryInfo();$('branchQuote').textContent='Chọn Chi nhánh để xem tổng tiền chính xác.';
        if(!signedIn||!address||!items().length)return;
        try {
            const result=await MilkTeaApi.get('/api/v1/account/cart/branches?addressId='+encodeURIComponent(address));if(current!==request)return;branches=result;
            for(const b of branches){const proximity=b.distanceKm!=null?' · '+b.distanceKm.toLocaleString('vi-VN',{maximumFractionDigits:2})+' km':'';option($('checkoutShop'),b.id,b.name+(b.nearest?' · Gần bạn nhất':'')+proximity+(b.canServe?'':' · Không đủ món'));}
            $('checkoutShop').disabled=!branches.length;
            $('branchRankingHelp').textContent='Chi nhánh được gợi ý theo vị trí và khả năng phục vụ.';
            if(branches.some(b=>String(b.id)===selected&&b.canServe&&pickCarrier(b))) {
                $('checkoutShop').value=selected;
            } else {
                // Prefer the first eligible nearby branch; users can change it manually.
                const recommended=branches.find(b=>b.canServe&&pickCarrier(b));
                if(recommended) $('checkoutShop').value=String(recommended.id);
            }
            await chooseBranch();
        } catch(e){if(current===request)message(e.message,true);}
    }
    async function chooseBranch(){
        ++request;invalidate();const b=branches.find(b=>String(b.id)===$('checkoutShop').value);$('branchAvailability').replaceChildren();clearDeliveryInfo();
        if(!b)return;
        $('branchAvailability').append(node('p',b.address,'mb-1'));
        if(!b.canServe){for(const issue of b.issues)$('branchAvailability').append(node('p','• '+issue,'text-danger mb-1'));$('branchQuote').textContent='Chi nhánh này chưa phục vụ đầy đủ giỏ hàng. Vui lòng chọn Chi nhánh khác.';return;}
        selectedCarrier=pickCarrier(b);
        if(!selectedCarrier){
            $('branchAvailability').append(node('p','• Chi nhánh này chưa có cấu hình giao hàng nội bộ.', 'text-danger mb-1'));
            $('branchQuote').textContent='Chi nhánh này chưa sẵn sàng giao hàng. Vui lòng chọn Chi nhánh khác hoặc liên hệ cửa hàng.';
            return;
        }
        renderDeliveryInfo(b,selectedCarrier);
        await refreshQuote();
    }
    async function refreshQuote(){
        const current=++request;invalidate();
        if(!$('checkoutShop').value||!selectedCarrier?.id)return;
        const code=$('checkoutVoucher').value.trim().toUpperCase();
        $('checkoutVoucher').value=code;
        if(code&&!/^[A-Z0-9_-]{3,32}$/.test(code)){
            $('checkoutVoucherNotice').textContent='Mã chỉ gồm 3–32 ký tự chữ, số, dấu _ hoặc -.';
            $('checkoutVoucherNotice').className='small text-danger mt-1 mb-0';
            $('branchQuote').textContent='Sửa mã để tính lại tổng tiền.';
            return;
        }
        $('branchQuote').textContent='Đang tính tổng tiền…';
        try{
            const result=await MilkTeaApi.post('/api/v1/account/cart/quote',{shopId:Number($('checkoutShop').value),addressId:Number($('checkoutAddress').value),carrierId:Number(selectedCarrier.id),voucherCode:code});
            if(current!==request)return;
            quote=result;clearMessage();renderQuote();$('deliverySubmit').disabled=busy;
        }catch(e){
            if(current!==request)return;
            $('branchQuote').textContent='Chưa thể tính tổng tiền.';
            if(code && (!e.status || (e.status>=400 && e.status<500))){
                $('checkoutVoucherNotice').className='small text-danger mt-1 mb-0';
                $('checkoutVoucherNotice').textContent=errorText(e);
            }else{
                $('checkoutVoucherNotice').className='small text-muted mt-1 mb-0';
                $('checkoutVoucherNotice').textContent=code?'Không thể kiểm tra mã lúc này.':'';
                message(errorText(e),true);
            }
        }
    }
    function renderQuote(){
        $('branchQuote').replaceChildren();
        const code=$('checkoutVoucher').value.trim().toUpperCase();
        $('checkoutVoucherNotice').className='small '+(code?'text-success':'text-muted')+' mt-1 mb-0';
        $('checkoutVoucherNotice').textContent=code?'Đã áp dụng '+code+' · Giảm '+MilkTeaApi.formatVND(quote.discountAmount):'';
        const rows=[['Tiền món',quote.subtotal],['Phí giao',quote.shippingFee]];
        if(Number(quote.discountAmount)>0)rows.push(['Giảm giá'+(code?' ('+code+')':''),-Number(quote.discountAmount)]);
        rows.push(['Tổng thanh toán',quote.totalAmount]);
        for(const [label,value] of rows){
            const row=node('div',null,'d-flex justify-content-between gap-2');
            row.append(node('span',label),node('strong',MilkTeaApi.formatVND(value)));
            $('branchQuote').append(row);
        }
    }
    function showPayment(event){
        event.preventDefault();
        if(completedOrder){message('Đơn #'+completedOrder.id+' đã được tạo. Vui lòng xem trong Đơn hàng đã đặt.');return;}
        if(!signedIn||!quote||busy||!selectedCarrier?.id)return;
        const dialog=$('deliveryPaymentDialog');
        $('deliveryPaymentForm').reset();
        $('deliveryPaymentError').classList.add('d-none');
        $('deliveryPaymentBranch').textContent='Chi nhánh: '+(branches.find(b=>b.id===quote.shopId)?.name||'UTE Tea');
        $('deliveryPaymentTotal').textContent=MilkTeaApi.formatVND(quote.totalAmount);
        $('deliveryPaymentVoucher').textContent=$('checkoutVoucher').value.trim().toUpperCase()||'Không áp dụng';
        $('deliveryPaymentDiscount').textContent='-'+MilkTeaApi.formatVND(quote.discountAmount);
        if(typeof dialog.showModal==='function') dialog.showModal();
        else message('Trình duyệt chưa hỗ trợ cửa sổ xác nhận. Vui lòng sử dụng phiên bản mới hơn.',true);
    }
    async function submit(event){
        event.preventDefault();
        const dialog=$('deliveryPaymentDialog');
        if(completedOrder||!dialog.open||!signedIn||!quote||busy||!selectedCarrier?.id)return;
        const method=new FormData(event.target).get('paymentMethod');
        if(!['COD','VNPAY'].includes(method))return;
        busy=true;
        $('deliveryPaymentConfirm').disabled=true;
        $('deliveryPaymentBack').disabled=true;
        $('deliverySubmit').disabled=true;
        let placed=null;
        try{
            const shown=quote;
            await refreshQuote();
            if(!quote){$('deliveryPaymentError').textContent='Không thể làm mới báo giá. Vui lòng quay lại và thử lại.';$('deliveryPaymentError').classList.remove('d-none');return;}
            if(Number(quote.totalAmount)!==Number(shown.totalAmount)||quote.cartVersion!==shown.cartVersion){message('Giỏ, giá hoặc phí đã thay đổi. Vui lòng kiểm tra tổng tiền mới rồi xác nhận lại.',true);if(dialog.open)dialog.close();return;}
            const input={shopId:quote.shopId,addressId:Number($('checkoutAddress').value),carrierId:Number(selectedCarrier.id),voucherCode:$('checkoutVoucher').value.trim().toUpperCase(),paymentMethod:method,expectedTotal:quote.totalAmount,cartVersion:quote.cartVersion,
                items:items().map(l=>({productId:l.productId,quantity:l.quantity,note:l.note||'',toppingIds:l.toppingIds||[]}))};
            const signature=JSON.stringify(input);let attempt;
            try{attempt=JSON.parse(sessionStorage.getItem('ute.branchCheckoutAttempt')||'null');}catch(_){attempt=checkoutAttempt;}
            if(!attempt||attempt.signature!==signature){attempt={signature,key:globalThis.crypto?.randomUUID?.()||'DELIVERY_'+Date.now()+'_'+Math.random().toString(36).slice(2)};}
            checkoutAttempt=attempt;
            try{sessionStorage.setItem('ute.branchCheckoutAttempt',JSON.stringify(attempt));}catch(_){/* Private mode: keep the key in memory. */}
            placed=await MilkTeaApi.post('/api/v1/market/orders',input,{'Idempotency-Key':attempt.key});
            completedOrder=placed;
            if(dialog.open)dialog.close();
            // Even if clearing the synchronized account cart fails, do not offer a second checkout.
            let cartWarning='';
            try{
                const cleared=await CustomerApp.clearCartIfVersion(input.cartVersion);
                checkoutAttempt=null;
                try{sessionStorage.removeItem('ute.branchCheckoutAttempt');}catch(_){}
                if(!cleared)cartWarning=' Giỏ hàng đã thay đổi ở tab khác nên được giữ nguyên; hãy kiểm tra trước khi đặt đơn tiếp.';
            }catch(err){cartWarning=' Không đồng bộ được giỏ, hãy kiểm tra lại trước khi đặt đơn khác.';}
            message('Đã tạo đơn #'+placed.id+' tại '+placed.shopName+'. Tổng '+MilkTeaApi.formatVND(placed.totalAmount)+cartWarning,!!cartWarning);
            const link=node('a',' Xem đơn giao hàng','alert-link');link.href='/my-orders?tab=delivery&order='+encodeURIComponent(placed.id);$('checkoutMessage').append(link);
            document.dispatchEvent(new CustomEvent('ute:delivery-placed',{detail:placed}));
            if(placed.paymentMethod==='VNPAY'){
                try{const result=await MilkTeaApi.post('/api/v1/market/orders/'+placed.id+'/payment-url');location.href=result.url;}
                catch(err){message('Đơn #'+placed.id+' đã tạo nhưng chưa mở được VNPAY: '+err.message+'. Vào Đơn hàng đã đặt để thanh toán lại.',true);$('checkoutMessage').append(link);}
            } else {
                window.UTEOrderSuccess?.show({
                    orderId: placed.id,
                    type: 'DELIVERY',
                    branch: placed.shopName || branches.find(b=>String(b.id)===String(input.shopId))?.name,
                    total: placed.totalAmount,
                    detailsUrl: '/my-orders?tab=delivery&order=' + encodeURIComponent(placed.id),
                    note: 'Đơn COD đã được tiếp nhận. Bạn thanh toán khi nhận hàng.' + cartWarning
                });
            }
        }catch(e){
            message((placed?'Đơn #'+placed.id+' đã tạo. ':'')+e.message,true);
            if(dialog.open){$('deliveryPaymentError').textContent=e.message||'Không thể tạo đơn. Vui lòng thử lại.';$('deliveryPaymentError').classList.remove('d-none');}
            if(placed){const link=node('a',' Xem đơn','alert-link');link.href='/my-orders?tab=delivery&order='+encodeURIComponent(placed.id);$('checkoutMessage').append(link);}
            else await loadBranches();
        }finally{busy=false;$('deliveryPaymentConfirm').disabled=false;$('deliveryPaymentBack').disabled=false;$('deliverySubmit').disabled=Boolean(completedOrder)||!quote;}
    }
    async function init(){
        if(!$('branchCheckout'))return;await CustomerApp.initContext();
        $('fulfillmentMode').onchange=mode;$('checkoutAddress').onchange=()=>loadBranches(false);$('checkoutShop').onchange=()=>chooseBranch().catch(e=>message(e.message,true));
        $('counterShop').onchange=()=>{clearMessage();syncCounterButton();};
        $('applyCheckoutVoucher').onclick=refreshQuote;$('deliveryCheckout').onsubmit=showPayment;
        $('deliveryPaymentForm').onsubmit=submit;
        $('deliveryPaymentBack').onclick=()=>{if(!busy&&$('deliveryPaymentDialog').open)$('deliveryPaymentDialog').close();};
        $('deliveryPaymentDialog').addEventListener('cancel',event=>{if(busy)event.preventDefault();});
        $('checkoutVoucher').oninput=()=>{invalidate();clearMessage();$('branchQuote').textContent='Bấm Áp dụng để cập nhật tổng tiền.';$('checkoutVoucherNotice').className='small text-muted mt-1 mb-0';$('checkoutVoucherNotice').textContent='';};
        $('checkoutVoucher').onkeydown=event=>{if(event.key==='Enter'){event.preventDefault();refreshQuote();}};
        const promo=new URLSearchParams(location.search).get('promo');
        if(promo && /^[A-Z0-9_-]{3,32}$/.test(promo.toUpperCase())) $('checkoutVoucher').value=promo.toUpperCase();
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
        await loadPickupBranches();
        ready=true;mode();
    }
    document.addEventListener('DOMContentLoaded',()=>init().catch(e=>message(e.message,true)));
    document.addEventListener('ute:cart-changed',event=>{if(ready){mode();if(event.detail?.pending){++request;invalidate();}else if(signedIn)loadBranches();}});
    return {refresh:()=>loadBranches()};
})();
