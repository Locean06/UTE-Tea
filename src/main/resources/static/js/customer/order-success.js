/* UTE Tea: one accessible success dialog outside checkout panels, used for pickup and COD. */
window.UTEOrderSuccess = (() => {
    const get = id => document.getElementById(id);
    function dialogNode() {
        const candidates = typeof document.querySelectorAll === 'function'
            ? [...document.querySelectorAll('dialog#uteOrderPlacedDialog')] : [];
        // Never open a dialog nested inside a checkout section which gets hidden
        // as soon as the cart becomes empty.
        return candidates.find(node => !node.closest?.('#branchCheckout'))
            || (!candidates.length ? get('uteOrderPlacedDialog') : null);
    }
    function show({ orderId, type, branch, total, detailsUrl, note } = {}) {
        const dialog = dialogNode();
        if (!dialog || !orderId || dialog.closest?.('#branchCheckout')) return false;
        const field = id => dialog.querySelector?.('[id="' + id + '"]') || get(id);
        const required = [
            'uteOrderPlacedId', 'uteOrderPlacedType', 'uteOrderPlacedBranch',
            'uteOrderPlacedTotal', 'uteOrderPlacedNote', 'uteOrderPlacedDetails',
            'uteOrderPlacedClose'
        ];
        if (required.some(id => !field(id))) return false;

        field('uteOrderPlacedId').textContent = '#' + String(orderId);
        field('uteOrderPlacedType').textContent = type === 'DELIVERY' ? 'Giao tận nơi' : 'Nhận tại quán';
        field('uteOrderPlacedBranch').textContent = branch || 'UTE Tea';
        const amount = Number(total);
        field('uteOrderPlacedTotal').textContent = Number.isFinite(amount)
            ? MilkTeaApi.formatVND(amount) : 'Xem trong đơn hàng';
        field('uteOrderPlacedNote').textContent = note || (type === 'DELIVERY'
            ? 'Đơn COD: thanh toán khi nhận hàng, chưa ghi nhận thanh toán.'
            : 'Đơn đang chờ quán xác nhận và chuẩn bị món.');
        const destination = String(detailsUrl || '/my-orders');
        field('uteOrderPlacedDetails').href = destination.startsWith('/') && !destination.startsWith('//')
            ? destination : '/my-orders';

        try {
            if (typeof dialog.showModal === 'function') {
                if (!dialog.open) dialog.showModal();
            } else {
                dialog.setAttribute('open', '');
            }
            field('uteOrderPlacedClose').focus?.();
            return true;
        } catch (error) {
            console.error('Không mở được popup đặt hàng thành công:', error);
            return false;
        }
    }
    function close() {
        const dialog = dialogNode();
        if (dialog?.open) {
            if (typeof dialog.close === 'function') dialog.close();
            else dialog.removeAttribute('open');
        }
    }
    document.addEventListener('click', event => {
        if (event.target?.closest?.('#uteOrderPlacedClose')) close();
    });
    return { show, close };
})();
