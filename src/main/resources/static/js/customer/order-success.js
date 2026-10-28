/* UTE Tea: success dialog for completed order creation (not a payment receipt). */
window.UTEOrderSuccess = (() => {
    const get = id => document.getElementById(id);
    function show({ orderId, type, branch, total, detailsUrl, note } = {}) {
        const dialog = get('uteOrderPlacedDialog');
        if (!dialog || !orderId) return false;
        get('uteOrderPlacedId').textContent = '#' + String(orderId);
        get('uteOrderPlacedType').textContent = type === 'DELIVERY' ? 'Giao tận nơi' : 'Nhận tại quán';
        get('uteOrderPlacedBranch').textContent = branch || 'UTE Tea';
        const amount = Number(total);
        get('uteOrderPlacedTotal').textContent = Number.isFinite(amount)
            ? MilkTeaApi.formatVND(amount) : 'Xem trong đơn hàng';
        get('uteOrderPlacedNote').textContent = note || (type === 'DELIVERY'
            ? 'Đơn COD: thanh toán khi nhận hàng, chưa ghi nhận thanh toán.'
            : 'Đơn đang chờ quán xác nhận và chuẩn bị món.');
        const destination = String(detailsUrl || '/my-orders');
        get('uteOrderPlacedDetails').href = destination.startsWith('/') && !destination.startsWith('//')
            ? destination : '/my-orders';
        if (typeof dialog.showModal === 'function') {
            if (!dialog.open) dialog.showModal();
        } else {
            dialog.setAttribute('open', '');
        }
        return true;
    }
    function close() {
        const dialog = get('uteOrderPlacedDialog');
        if (dialog?.open) {
            if (typeof dialog.close === 'function') dialog.close();
            else dialog.removeAttribute('open');
        }
    }
    document.addEventListener('click', event => {
        if (event.target.closest('#uteOrderPlacedClose')) close();
    });
    return { show, close };
})();
