/* Private customer order history: never request store-wide staff endpoints. */
(() => {
    'use strict';
    const $ = id => document.getElementById(id);
    if (!$('myOrdersList')) return;
    const params = new URLSearchParams(window.location.search);
    const state = { delivery: [], counter: [], tab: params.get('tab') === 'counter' ? 'counter' : 'delivery', busy: false };
    const emphasisOrder = params.get('order') || '';
    const deliveryLabels = {
        NEW: 'Đã đặt', CONFIRMED: 'Đã xác nhận', SHIPPING: 'Đang giao',
        DELIVERED: 'Đã giao', DELIVERY_FAILED: 'Giao thất bại', CANCELLED: 'Đã hủy',
        RETURN_REQUESTED: 'Đang yêu cầu trả', RETURNED: 'Đã trả hàng', REFUNDED: 'Đã hoàn tiền'
    };
    const counterLabels = {
        CHO_THANH_TOAN: 'Chờ thanh toán', CHO_XAC_NHAN: 'Chờ xác nhận',
        CHO_CHE_BIEN: 'Chờ pha chế', DANG_CHE_BIEN: 'Đang pha chế',
        HOAN_THANH: 'Hoàn thành', DA_HUY: 'Đã hủy'
    };
    const deliveryDone = new Set(['DELIVERED','RETURNED','REFUNDED']);
    const deliveryOther = new Set(['CANCELLED','DELIVERY_FAILED']);
    const counterDone = new Set(['HOAN_THANH']);
    const counterOther = new Set(['DA_HUY']);

    const el = (tag, text, className) => {
        const result = document.createElement(tag);
        if (text !== undefined && text !== null) result.textContent = String(text);
        if (className) result.className = className;
        return result;
    };
    const money = amount => MilkTeaApi.formatVND(amount ?? 0);
    const date = value => {
        if (!value) return 'Chưa rõ thời gian';
        const parsed = new Date(value);
        return Number.isNaN(parsed.valueOf()) ? 'Chưa rõ thời gian' : new Intl.DateTimeFormat('vi-VN', {
            day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'
        }).format(parsed);
    };
    const statusGroup = (o, tab) => {
        if (tab === 'counter') {
            if (counterDone.has(o.status)) return 'done';
            if (counterOther.has(o.status)) return 'other';
        } else {
            if (deliveryDone.has(o.status)) return 'done';
            if (deliveryOther.has(o.status)) return 'other';
        }
        return 'pending';
    };
    const setError = text => {
        const error = $('myOrdersError');
        error.classList.toggle('d-none', !text);
        error.textContent = text || '';
    };
    function detailLine(name,value) {
        const line = el('div', null, 'ute-myorders-detail-line');
        line.append(el('span',name),el('strong',value));
        return line;
    }
    function action(label, handler, style='btn btn-outline-secondary') {
        const button = el('button',label,style);button.type='button';
        button.addEventListener('click',async () => {
            button.disabled=true;
            try { await handler(); } catch (e) { setError(e.message || 'Không thể thực hiện thao tác.'); }
            finally { button.disabled=false; }
        });
        return button;
    }
    function detailContent(o, tab) {
        const details = el('div',null,'ute-myorders-details');
        if (tab === 'delivery') {
            details.append(detailLine('Cơ sở',o.shopName || 'UTE Tea'));
            details.append(detailLine('Người nhận',o.recipient || '—'));
            details.append(detailLine('Số điện thoại',o.phone || '—'));
            details.append(detailLine('Địa chỉ nhận',o.addressLine || '—'));
            details.append(detailLine('Thanh toán', (o.paymentMethod === 'COD' ? 'Thanh toán khi nhận (COD)' : 'VNPAY') + (o.paid ? ' · Đã thanh toán' : ' · Chưa thanh toán')));
            details.append(detailLine('Tiền món',money(o.subtotal)));
            details.append(detailLine('Phí giao hàng',money(o.shippingFee)));
            if (o.status === 'CONFIRMED') details.append(detailLine('Tiến độ pha chế', ({PENDING:'Chờ bếp nhận',PREPARING:'Đang pha chế',READY:'Đã pha xong'})[o.preparationStatus] || 'Chờ cập nhật'));
            if (o.voucherCode) details.append(detailLine('Mã đã dùng',o.voucherCode));
            details.append(detailLine('Giảm giá','-'+money(o.discountAmount)));
        } else {
            details.append(detailLine('Hình thức',o.tableName ? 'Tại bàn · '+o.tableName : 'Tại quầy / mang về'));
            details.append(detailLine('Thanh toán',o.paid ? 'Đã thanh toán' : 'Chưa thanh toán'));
            details.append(detailLine('Tiền món',money(o.subtotal)));
            details.append(detailLine('Giảm giá',money(o.discountAmount)));
        }
        const lines = el('div',null,'ute-myorders-detail-items');
        lines.append(el('strong','Các món đã đặt','d-block mb-2 small'));
        for (const line of o.items || []) {
            const amount = line.lineTotal ?? (Number(line.price ?? line.unitPrice ?? 0) * Number(line.quantity || 1));
            const item = detailLine(`${line.quantity || 1} × ${line.name || line.productName || 'Món uống'}${line.size ? ' · '+line.size : ''}`,money(amount));
            lines.append(item);
            if (line.note) lines.append(el('div','Ghi chú: '+line.note,'small text-muted pb-1'));
        }
        details.append(lines);
        if (tab === 'delivery') {
            const actions = el('div',null,'ute-myorders-detail-actions');
            if (o.status === 'NEW') {
                actions.append(action('Hủy đơn',async () => {
                    if (!window.confirm('Bạn muốn hủy đơn #'+o.id+'?')) return;
                    await MilkTeaApi.put('/api/v1/market/orders/'+encodeURIComponent(o.id)+'/status',{status:'CANCELLED',version:o.version});
                    await loadOrders();
                }));
            }
            if (o.paymentMethod === 'VNPAY' && !o.paid && (o.status === 'NEW' || o.status === 'CONFIRMED')) {
                actions.append(action('Thanh toán VNPAY',async () => {
                    const result = await MilkTeaApi.post('/api/v1/market/orders/'+encodeURIComponent(o.id)+'/payment-url');
                    window.location.href=result.url;
                },'btn btn-primary-custom'));
            }
            if (o.status === 'DELIVERED') {
                actions.append(action('Yêu cầu trả hàng',async () => {
                    if (!window.confirm('Bạn muốn gửi yêu cầu trả đơn #'+o.id+'?')) return;
                    await MilkTeaApi.put('/api/v1/market/orders/'+encodeURIComponent(o.id)+'/status',{status:'RETURN_REQUESTED',version:o.version});
                    await loadOrders();
                }));
                actions.append(action('Đánh giá sản phẩm',async () => {window.location.href='/marketplace#deliveryOrders';}));
            }
            if (actions.children.length) details.append(actions);
        }
        return details;
    }
    function renderCard(o,tab) {
        const id = tab === 'delivery' ? o.id : o.orderId;
        const label = (tab === 'delivery' ? deliveryLabels : counterLabels)[o.status] || o.status || 'Đang xử lý';
        const category = statusGroup(o,tab);
        const card = el('article',null,'ute-myorders-card');
        if (emphasisOrder === String(id) && tab === 'delivery') card.classList.add('highlight');
        const head=el('div',null,'ute-myorders-card-head');
        const heading=el('div');
        heading.append(el('strong','#'+id,'ute-myorders-card-code'),el('span',date(o.createdAt),'ute-myorders-card-date d-block'));
        head.append(heading,el('span',label,'ute-myorders-pill '+category));
        card.append(head);
        card.append(el('div',tab==='delivery'?(o.shopName||'UTE Tea'):'Đơn hàng tại quầy','ute-myorders-card-name'));
        const parts = (o.items||[]).slice(0,3).map(item=>(item.quantity||1)+' × '+(item.name||item.productName||'Món uống'));
        const remainder = (o.items||[]).length - parts.length;
        card.append(el('p',parts.join(' · ')+(remainder>0?` · +${remainder} món khác`:''),'ute-myorders-card-items'));
        const foot = el('div',null,'ute-myorders-card-foot');
        const total=el('div',null,'ute-myorders-card-total');
        total.append(el('span','Tổng thanh toán'),el('strong',money(o.totalAmount)));
        const toggle=el('button','Xem chi tiết','btn btn-outline-secondary');toggle.type='button';
        foot.append(total,toggle);card.append(foot);
        const details=detailContent(o,tab);
        details.hidden=true;card.append(details);
        toggle.addEventListener('click',()=>{
            details.hidden=!details.hidden;
            toggle.textContent=details.hidden?'Xem chi tiết':'Thu gọn';
            toggle.setAttribute('aria-expanded',String(!details.hidden));
        });
        toggle.setAttribute('aria-expanded','false');
        if (emphasisOrder === String(id) && tab === 'delivery') {
            details.hidden=false;toggle.textContent='Thu gọn';toggle.setAttribute('aria-expanded','true');
        }
        return card;
    }
    function render() {
        const tab=state.tab;
        document.querySelectorAll('.ute-myorders-tabs [role="tab"]').forEach(button=>{
            const selected=button.dataset.tab===tab;
            button.classList.toggle('active',selected);
            button.setAttribute('aria-selected',String(selected));
        });
        const source=state[tab];
        const query=$('myOrdersSearch').value.trim().toLocaleLowerCase('vi');
        const status=$('myOrdersStatus').value;
        const filtered=source.filter(o=>{
            if (status !== 'all' && statusGroup(o,tab)!==status) return false;
            const id=tab==='delivery'?o.id:o.orderId;
            const content=[id,tab==='delivery'?o.shopName:'UTE Tea',...(o.items||[]).map(l=>l.name||l.productName)].join(' ').toLocaleLowerCase('vi');
            return content.includes(query);
        });
        const list=$('myOrdersList');list.replaceChildren();
        filtered.forEach(o=>list.append(renderCard(o,tab)));
        $('myOrdersEmpty').classList.toggle('d-none',filtered.length>0);
    }
    function updateCounters(){
        const all=[...state.delivery.map(o=>({o,tab:'delivery'})),...state.counter.map(o=>({o,tab:'counter'}))];
        $('myOrdersTotal').textContent=String(all.length);
        $('myOrdersPending').textContent=String(all.filter(x=>statusGroup(x.o,x.tab)==='pending').length);
        $('myOrdersDone').textContent=String(all.filter(x=>statusGroup(x.o,x.tab)==='done').length);
        $('myOrdersDeliveryCount').textContent=String(state.delivery.length);
        $('myOrdersCounterCount').textContent=String(state.counter.length);
    }
    async function loadOrders(){
        if (state.busy) return;
        state.busy=true;setError('');$('myOrdersLoading').classList.remove('d-none');$('myOrdersRefresh').disabled=true;
        try {
            // Both endpoints filter orders for the signed-in account on the server.
            const results=await Promise.allSettled([
                MilkTeaApi.get('/api/v1/market/orders'),
                MilkTeaApi.get('/api/v1/customer/orders')
            ]);
            state.delivery=results[0].status==='fulfilled'&&Array.isArray(results[0].value)?results[0].value:[];
            state.counter=results[1].status==='fulfilled'&&Array.isArray(results[1].value)?results[1].value:[];
            if (results.some(result=>result.status==='rejected')) {
                setError('Không tải được đầy đủ lịch sử đơn hàng. Hãy bấm Làm mới để thử lại.');
            }
            updateCounters();render();
        } finally {$('myOrdersLoading').classList.add('d-none');$('myOrdersRefresh').disabled=false;state.busy=false;}
    }
    document.querySelectorAll('.ute-myorders-tabs [role="tab"]').forEach(button=>button.addEventListener('click',()=>{
        state.tab=button.dataset.tab;$('myOrdersStatus').value='all';
        const next=new URL(window.location.href);next.searchParams.set('tab',state.tab);next.searchParams.delete('order');
        history.replaceState(null,'',next.pathname+next.search);render();
    }));
    $('myOrdersSearch').addEventListener('input',render);
    $('myOrdersStatus').addEventListener('change',render);
    $('myOrdersRefresh').addEventListener('click',()=>loadOrders().catch(e=>setError(e.message||'Không tải được đơn hàng.')));
    loadOrders().catch(e=>{$('myOrdersLoading').classList.add('d-none');setError(e.message||'Không thể tải lịch sử đơn hàng.');});
    document.addEventListener('visibilitychange', () => {if (!document.hidden) void loadOrders().catch(e=>setError(e.message));});
    window.setInterval(() => {if (!document.hidden) void loadOrders().catch(e=>setError(e.message));},30000);
})();
