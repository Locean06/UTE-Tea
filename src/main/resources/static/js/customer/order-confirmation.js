// Dedicated single-order confirmation. Never list table history in an anonymous browser.
(() => {
    'use strict';
    const root = document.getElementById('uteOrderConfirmation');
    if (!root) return;
    const orderId = String(root.dataset.order || '');
    const storedId = sessionStorage.getItem('uteTea.recentOrderId');
    const storedToken = sessionStorage.getItem('uteTea.recentOrderToken');
    const proofHeaders = storedId === orderId && storedToken ? {'X-Counter-Order-Token': storedToken} : {};
    const $ = id => document.getElementById(id);
    const states = {CHO_THANH_TOAN: 'Chờ thanh toán', CHO_XAC_NHAN: 'Chờ xác nhận', CHO_CHE_BIEN: 'Chờ pha chế', DANG_CHE_BIEN: 'Đang pha chế', HOAN_THANH: 'Hoàn thành', DA_HUY: 'Đã hủy'};
    let order = null;

    function error(message) {
        $('confirmationLoading').classList.add('d-none');
        $('confirmationDetails').classList.add('d-none');
        $('confirmationError').textContent = message;
        $('confirmationError').classList.remove('d-none');
    }
    function render(o) {
        order = o;
        $('confirmationLoading').classList.add('d-none');
        $('confirmationError').classList.add('d-none');
        $('confirmationDetails').classList.remove('d-none');
        $('confirmCode').textContent = 'ORD' + String(o.orderId).padStart(3, '0');
        $('confirmStatus').textContent = states[o.status] || o.status || 'Đang xử lý';
        $('confirmPayment').textContent = o.paid ? 'Đã thu tiền' : 'Chưa xác nhận';
        $('confirmTotal').textContent = MilkTeaApi.formatVND(o.totalAmount);
        $('confirmBranch').textContent = (o.shopName || 'Cơ sở phục vụ') + (o.tableName ? ' · Bàn '+o.tableName : ' · Mang về');
        const transfer = o.paymentRequestMethod === 'BANK_TRANSFER';
        $('confirmCashMessage').classList.toggle('d-none',transfer || o.paid);
        const bank = $('confirmBankDetails'); bank.replaceChildren(); bank.classList.toggle('d-none',!transfer || o.paid);
        if (transfer && !o.paid) {
            const values = o.bankName && o.bankAccountNumber && o.bankAccountHolder
                ? ['Ngân hàng: '+o.bankName,'Số tài khoản: '+o.bankAccountNumber,'Chủ tài khoản: '+o.bankAccountHolder,'Số tiền: '+MilkTeaApi.formatVND(o.totalAmount),'Nội dung: '+o.transferContent,'Đang chờ thu ngân xác minh giao dịch.']
                : ['Cơ sở chưa cấu hình đầy đủ tài khoản ngân hàng. Vui lòng liên hệ thu ngân; đơn vẫn chờ thanh toán.'];
            for (const text of values) {const p=document.createElement('p');p.textContent=text;bank.append(p);}
            if (o.vietQrUrl) {const img=document.createElement('img');img.src=o.vietQrUrl;img.alt='QR chuyển khoản đúng đơn hàng';img.style.maxWidth='100%';img.width=280;bank.append(img);}
        }
        const list = $('confirmationItems');
        list.replaceChildren();
        (o.items || []).forEach(item => {
            const li = document.createElement('li');
            const name = document.createElement('span');
            name.textContent = `${item.productName || 'Thức uống'} × ${item.quantity || 1}`;
            li.appendChild(name);
            const price = document.createElement('strong');
            price.textContent = MilkTeaApi.formatVND(item.totalAmount ?? item.lineTotal ?? 0);
            li.appendChild(price);
            list.appendChild(li);
        });
        $('confirmationPaymentActions').classList.toggle('d-none', Boolean(o.paid) || !transfer || o.status === 'DA_HUY');
    }
    async function refresh() {
        if (!/^\d+$/.test(orderId) || !orderId) { error('Mã đơn hàng không hợp lệ.'); return; }
        try {
            // For signed-in owners the HttpOnly JWT suffices. Guests must hold the opaque
            // token issued for this particular order, not the public table/QR identifier.
            const data = await MilkTeaApi.get('/api/v1/orders/' + encodeURIComponent(orderId), proofHeaders);
            render(data);
        } catch (err) {
            error('Không thể xác minh đơn hàng này. Vui lòng đăng nhập hoặc mở lại trên đúng trình duyệt vừa đặt món.');
        }
    }
    $('confirmationTransferBtn').addEventListener('click', async () => {
        if (!order || order.paid) return;
        const btn = $('confirmationTransferBtn');
        btn.disabled = true;
        try {
            await MilkTeaApi.post('/api/v1/orders/' + encodeURIComponent(orderId) + '/payment-notices', {method: 'BANK_TRANSFER',reference:'CUSTOMER_TRANSFERRED_'+orderId}, {...proofHeaders,'Idempotency-Key':'notice-'+orderId});
            MilkTeaApi.showToast('Đã gửi thông báo chuyển khoản. Thu ngân sẽ kiểm tra.', 'success');
            await refresh();
        } catch (err) {
            MilkTeaApi.showToast(err.message || 'Chưa gửi được thông báo chuyển khoản', 'error');
        } finally { btn.disabled = false; }
    });
    refresh();
    const poll = setInterval(() => { if (document.visibilityState === 'visible') refresh(); }, 15000);
    window.addEventListener('pagehide', () => clearInterval(poll), {once: true});
})();
