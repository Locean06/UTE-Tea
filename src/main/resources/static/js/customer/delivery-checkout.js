// Shared checkout; the payment choice is shown only after the customer sends the cart.
const BranchCheckout = (() => {
    const $ = id => document.getElementById(id);
    let quote = null, busy = false, sequence = 0, ready = false;
    function node(tag, text, cls) { const n = document.createElement(tag); n.textContent = text; if (cls) n.className = cls; return n; }
    function message(text) { $('checkoutMessage').className = 'alert alert-warning'; $('checkoutMessage').textContent = text; }
    function items() { return CustomerApp.getLocalCart().map(i => ({productId:i.productId,quantity:i.quantity,note:i.note || '',toppingIds:i.toppingIds || (i.toppings || []).map(t=>t.productId || t.id)})); }
    function input() { const ctx = window.MilkTeaContext; return {shopId:ctx.tableId ? ctx.shopId : undefined,items:items()}; }
    function invalidate() {
        quote = null; sequence++; $('checkoutBtn').disabled = true;
        if (!busy && $('checkoutPaymentDialog').open) $('checkoutPaymentDialog').close();
    }
    async function refresh() {
        invalidate(); const current = sequence;
        $('checkoutRetry').classList.add('d-none');
        if (!$('checkoutProducts').querySelector('#cartContainer, #deliveryCart')) $('checkoutProducts').replaceChildren(...CustomerApp.getLocalCart().map(i=>node('p',`${i.productName} · ${i.size} × ${i.quantity}`,'small mb-2')));
        if (!items().length) { $('branchQuote').textContent = 'Thêm món để xem tổng tiền.'; $('checkoutMessage').classList.add('d-none'); return; }
        $('branchQuote').textContent = 'Đang tính tổng tiền...';
        try {
            const result = await MilkTeaApi.post('/api/v1/public/checkout/quote',input());
            if (current !== sequence) return;
            quote = result; $('checkoutMessage').classList.add('d-none'); $('branchQuote').replaceChildren();
            for (const [label,value] of [['Tạm tính',quote.subtotal],['Giảm giá',-quote.discountAmount],['Tổng thanh toán',quote.totalAmount]]) {
                const row = node('div','','d-flex justify-content-between gap-2 mb-2'); row.append(node('span',label),node('strong',MilkTeaApi.formatVND(value))); $('branchQuote').append(row);
            }
            $('checkoutBtn').disabled = busy;
        } catch (e) {
            if (current !== sequence) return;
            message(e.status === 404 ? 'Ứng dụng chưa cập nhật chức năng đặt hàng. Vui lòng tải lại trang hoặc liên hệ nhân viên.' : e.message);
            $('branchQuote').textContent = 'Chưa thể tính tiền. Giỏ hàng của bạn vẫn được giữ.';
            $('checkoutRetry').classList.remove('d-none');
        }
    }
    async function init() {
        if (!$('branchCheckout')) return;
        const cart = $('cartContainer') || $('deliveryCart');
        if (cart) {
            const previousPanel = cart.id === 'cartContainer' ? cart.closest('.row') : cart.closest('.card');
            $('checkoutProducts').replaceChildren(cart);
            if (previousPanel && !previousPanel.contains($('branchCheckout'))) previousPanel.hidden = true;
        }
        const dialog = $('checkoutPaymentDialog');
        $('checkoutRetry').onclick = () => (ready ? refresh() : init()).catch(e=>message(e.message));
        $('branchOrderForm').onsubmit = event => {
            event.preventDefault(); if (busy || !quote || dialog.open) return;
            $('checkoutPaymentForm').reset();
            $('checkoutPaymentError').classList.add('d-none');
            $('checkoutPaymentTotal').textContent = MilkTeaApi.formatVND(quote.totalAmount);
            dialog.showModal();
        };
        $('checkoutPaymentBack').onclick = () => { if (!busy) dialog.close(); };
        dialog.addEventListener('cancel',event=>{if(busy)event.preventDefault();});
        $('checkoutPaymentForm').onsubmit = async event => {
            event.preventDefault(); if (busy || !quote) return;
            const method = new FormData(event.target).get('paymentMethod');
            if (!['CASH','BANK_TRANSFER'].includes(method)) return;
            busy = true;
            $('checkoutPaymentError').classList.add('d-none');
            $('checkoutBtn').disabled = true;
            $('checkoutPaymentConfirm').disabled = true;
            $('checkoutPaymentBack').disabled = true;
            try {
                const order = await CustomerApp.submitOrder({...input(),shopId:quote.shopId,expectedTotal:quote.totalAmount,paymentMethod:method,fulfillmentMode:window.MilkTeaContext.tableId ? 'DINE_IN' : 'TAKEAWAY'});
                if (order) dialog.close();
            } catch (e) {
                const text = e.message || 'Chưa thể gửi đơn hàng. Vui lòng thử lại.';
                $('checkoutPaymentError').textContent = text;
                $('checkoutPaymentError').classList.remove('d-none');
            }
            finally {
                busy = false;
                $('checkoutPaymentConfirm').disabled = false;
                $('checkoutPaymentBack').disabled = false;
                $('checkoutBtn').disabled = !quote;
            }
        };
        await CustomerApp.initContext();
        const ctx = window.MilkTeaContext;
        $('checkoutTable').classList.toggle('d-none', !ctx.tableId);
        $('checkoutTable').textContent = ctx.tableId ? 'Bàn: ' + ctx.tableName : '';
        ready = true; await refresh();
    }
    document.addEventListener('DOMContentLoaded',()=>init().catch(e=>{message(e.message);$('checkoutRetry').classList.remove('d-none');}));
    document.addEventListener('ute:cart-changed',e=>{if(ready){if(e.detail?.pending)invalidate();else refresh();}});
    return {refresh};
})();
