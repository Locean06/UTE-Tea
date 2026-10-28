/* DeliveryOrder (online delivery) is independent of POS orders in DonHang. */
const CashierDeliveryInbox = (() => {
    const byId = id => document.getElementById(id);
    const currency = value => MilkTeaApi.formatVND(value ?? 0);
    const labels = {NEW:'Chờ xác nhận',CONFIRMED:'Đã xác nhận',SHIPPING:'Đang giao',DELIVERED:'Đã giao',DELIVERY_FAILED:'Giao thất bại',CANCELLED:'Đã hủy',RETURN_REQUESTED:'Yêu cầu hoàn',RETURNED:'Đã trả',REFUNDED:'Đã hoàn'};
    let orders=[],shippers=[],working=false,loading=false,reloadQueued=false;
    function el(tag,text,cls){const x=document.createElement(tag);if(text!==undefined)x.textContent=text;if(cls)x.className=cls;return x;}
    function showError(error){const box=byId('deliveryInboxError');if(box){box.classList.remove('d-none');box.textContent=error?.message||'Không thể tải dữ liệu. Vui lòng thử lại.';}}
    function dataLine(card,title,value){const wrap=el('div');wrap.append(el('small',title),el('span',value ?? '—'));card.append(wrap);}
    function makeOrder(order){
        const card=el('article',undefined,'delivery-inbox-order');
        const top=el('div',undefined,'delivery-inbox-order-top');
        top.append(el('strong','Đơn giao #'+order.id+' · '+(order.shopName||'UTE Tea')));
        const pill=el('span',labels[order.status]||order.status,'delivery-inbox-pill');pill.dataset.status=order.status;top.append(pill);card.append(top);
        const details=el('div',undefined,'delivery-inbox-order-data');
        dataLine(details,'Người nhận',order.recipient);
        dataLine(details,'Số điện thoại',order.phone);
        dataLine(details,'Địa chỉ giao',order.addressLine);
        dataLine(details,'Tổng thanh toán',currency(order.totalAmount));
        dataLine(details,'Thanh toán',order.paymentMethod==='COD'?'COD - '+(order.paid?'Đã thu':'Thu khi giao'):(order.paid?'Đã thanh toán':'Chờ VNPAY'));
        dataLine(details,'Nhân viên giao',order.shipperId?'#'+order.shipperId:'Chưa phân công');
        dataLine(details,'Tiến độ pha chế',({PENDING:'Chờ bếp nhận',PREPARING:'Bếp đang pha',READY:'Đã pha xong'})[order.preparationStatus]||'Chưa có');
        card.append(details);
        if(order.items?.length){card.append(el('p',order.items.map(x=>(x.name||'Món')+' ×'+x.quantity).join(' · '),'delivery-inbox-items'));}
        const actions=el('div',undefined,'delivery-inbox-order-actions');
        if(order.status==='NEW'){
            const button=el('button','Xác nhận đơn');button.type='button';button.onclick=()=>update(order,button,'confirm');actions.append(button);
        }
        if(order.status==='CONFIRMED'){
            const select=el('select');select.setAttribute('aria-label','Chọn nhân viên giao hàng');
            const placeholder=el('option','Chọn nhân viên giao hàng');placeholder.value='';select.append(placeholder);
            for(const shipper of shippers){const option=el('option',shipper.name);option.value=String(shipper.id);if(shipper.id===order.shipperId)option.selected=true;select.append(option);}
            const button=el('button',order.shipperId?'Đổi người giao':'Phân công giao hàng');button.type='button';button.onclick=()=>update(order,button,'assign',select.value);
            if(!shippers.length){
                select.disabled=true;button.disabled=true;
                actions.append(el('span','Chưa có nhân viên giao hàng nội bộ đang hoạt động. Bật SHIPPER_BOOTSTRAP_ENABLED để tạo tài khoản demo.','small text-danger'));
            } else {
                actions.append(el('span',order.shipperId?'Được đổi người giao trước khi nhận hàng.':'Có thể phân công ngay khi xác nhận, không cần chờ bếp.','small text-muted'));
            }
            actions.append(select,button);
        }
        if(order.status==='SHIPPING'){actions.append(el('span','Nhân viên giao hàng đang thực hiện đơn.','small text-muted'));}
        if(order.status==='DELIVERY_FAILED'){actions.append(el('span','Giao thất bại: liên hệ quản lý để xử lý đơn.','small text-muted'));}
        if(!actions.childNodes.length)actions.append(el('span','Đơn đã được xử lý hoặc kết thúc.','small text-muted'));
        card.append(actions);return card;
    }
    function updateStats(){
        const groups={NEW:'deliveryStatNew',CONFIRMED:'deliveryStatConfirmed',SHIPPING:'deliveryStatShipping',DELIVERED:'deliveryStatDelivered'};
        for(const [status,id] of Object.entries(groups))if(byId(id))byId(id).textContent=orders.filter(o=>o.status===status).length;
        if(byId('posDeliveryNewCount'))byId('posDeliveryNewCount').textContent=orders.filter(o=>o.status==='NEW').length;
    }
    function renderPreview(){
        const list=byId('posDeliveryNewList');if(!list)return;list.replaceChildren();
        const pending=orders.filter(o=>o.status==='NEW').slice(0,4);
        if(!pending.length){list.append(el('div','Không có đơn giao hàng mới cần xác nhận.'));return;}
        for(const o of pending){const row=el('div',undefined,'pos-delivery-live-item');
            const info=el('div');info.append(el('strong','#'+o.id+' · '+(o.recipient||'Khách hàng')),el('small',(o.shopName||'UTE Tea')+' · '+currency(o.totalAmount)));
            const link=el('a','Xử lý','btn btn-sm btn-outline-secondary');link.href='/cashier/delivery-orders';row.append(info,link);list.append(row);
        }
    }
    function renderList(){
        const list=byId('deliveryInboxList');if(!list)return;list.replaceChildren();
        const text=(byId('deliveryInboxSearch')?.value||'').trim().toLocaleLowerCase('vi');
        const status=byId('deliveryInboxStatus')?.value||'';
        const filtered=orders.filter(o=>(!status||o.status===status)&&(!text||String(o.id).includes(text)||[o.recipient,o.phone,o.addressLine,o.shopName].some(v=>String(v||'').toLocaleLowerCase('vi').includes(text))));
        if(!filtered.length){list.append(el('p','Chưa có đơn giao hàng phù hợp.','text-muted'));return;}
        for(const o of filtered)list.append(makeOrder(o));
    }
    async function load(){
        // Coalesce WebSocket + polling + manual refresh to prevent stale responses
        // overwriting a newly created or newly assigned order.
        if(loading){reloadQueued=true;return;}
        loading=true;
        const refresh=byId('deliveryInboxRefresh');if(refresh)refresh.disabled=true;
        try{
            do{
                reloadQueued=false;
                const [list,active]=await Promise.all([
                    MilkTeaApi.get('/api/v1/cashier/delivery-orders'),
                    byId('deliveryInboxList')?MilkTeaApi.get('/api/v1/cashier/delivery-orders/shippers'):Promise.resolve([])
                ]);
                orders=Array.isArray(list)?list:[];shippers=Array.isArray(active)?active:[];
                updateStats();renderPreview();renderList();
                const error=byId('deliveryInboxError');if(error)error.classList.add('d-none');
            }while(reloadQueued);
        }catch(e){
            // Keep the last known orders visible on transient connection problems.
            if(byId('posDeliveryNewList')&&!orders.length)byId('posDeliveryNewList').textContent='Không thể tải đơn giao hàng. Kiểm tra kết nối API/database.';
            showError(e);
        }finally{
            loading=false;
            if(refresh)refresh.disabled=false;
            if(reloadQueued){reloadQueued=false;void load();}
        }
    }
    async function update(order,button,type,accountId){
        if(working)return;
        if(type==='assign'&&!accountId){alert('Chọn nhân viên trước khi phân công.');return;}
        working=true;button.disabled=true;
        try{
            if(type==='confirm')await MilkTeaApi.put('/api/v1/market/orders/'+order.id+'/status',{status:'CONFIRMED',version:order.version});
            else await MilkTeaApi.put('/api/v1/market/orders/'+order.id+'/shipper',{accountId:Number(accountId),version:order.version});
            await load();
        }catch(e){await load();showError(e);}finally{working=false;button.disabled=false;}
    }
    function init(){
        if(!byId('deliveryInboxList')&&!byId('posDeliveryNewList'))return;
        byId('deliveryInboxRefresh')?.addEventListener('click',load);
        byId('deliveryInboxSearch')?.addEventListener('input',renderList);
        byId('deliveryInboxStatus')?.addEventListener('change',renderList);
        // Snapshot is always fetched from SQL; the event only triggers an immediate refresh.
        if (typeof MilkTeaRealtime !== 'undefined') {
            window.MilkTeaContext = window.MilkTeaContext || {};
            window.MilkTeaContext.role = document.getElementById('posRole')?.dataset.role || 'CASHIER';
            MilkTeaRealtime.on('DELIVERY_ORDER_CHANGED', load);
            MilkTeaRealtime.onReconnect(load);
            MilkTeaRealtime.connect();
        }
        load();
        document.addEventListener('visibilitychange' ,()=>{if(!document.hidden)load();});
        setInterval(()=>{if(!document.hidden)load();},30000);
    }
    document.addEventListener('DOMContentLoaded',init);
    return {reload:load};
})();
