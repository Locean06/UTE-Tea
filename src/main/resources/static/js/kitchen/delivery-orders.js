/* Đơn giao tận nơi: tách khỏi đơn POS, KITCHEN nhận sau khi thu ngân xác nhận. */
(() => {
    'use strict';
    const $ = id => document.getElementById(id);
    const elem = (tag, text, className) => {
        const node = document.createElement(tag);
        if (text !== undefined && text !== null) node.textContent = String(text);
        if (className) node.className = className;
        return node;
    };
    let loading = false;
    let retry = false;
    function label(status) {
        return ({PENDING:'Chờ tiếp nhận',PREPARING:'Đang pha chế',READY:'Đã hoàn tất'})[status] || 'Chờ tiếp nhận';
    }
    async function update(order, status, button) {
        button.disabled = true;
        try {
            await MilkTeaApi.put('/api/v1/kitchen/delivery-orders/' + encodeURIComponent(order.id) + '/preparation', {
                status, version: order.version
            });
            await load();
        } catch (err) {
            const error = $('kitchenDeliveryError');
            error.textContent = err.message || 'Không thể cập nhật đơn; vui lòng làm mới';
            error.classList.remove('d-none');
            await load();
        } finally { button.disabled = false; }
    }
    function card(order) {
        const card = elem('article', undefined, 'col-12 col-lg-6');
        const box = elem('div', undefined, 'card p-3 h-100');
        box.append(elem('h3', 'Đơn giao #' + order.id + ' · ' + order.shopName, 'h6 fw-bold'));
        box.append(elem('p', 'Trạng thái: ' + label(order.preparationStatus), 'small text-muted'));
        const list = elem('ul', undefined, 'small ps-3 mb-3');
        for (const item of order.items || []) {
            list.append(elem('li', (item.quantity || 0) + ' × ' + (item.name || 'Món') +
                (item.size ? ' · ' + item.size : '') + (item.note ? ' · ' + item.note : '')));
        }
        box.append(list);
        const action = order.preparationStatus === 'PENDING' ? ['Bắt đầu pha', 'PREPARING'] :
            order.preparationStatus === 'PREPARING' ? ['Đã pha xong', 'READY'] : null;
        if (action) {
            const button = elem('button', action[0], 'btn btn-primary-custom btn-sm');
            button.type = 'button';
            button.addEventListener('click', () => update(order, action[1], button));
            box.append(button);
        } else box.append(elem('p', 'Đang chờ nhân viên giao nhận món.', 'small text-success mb-0'));
        card.append(box);
        return card;
    }
    async function load() {
        if (loading) {retry=true;return;}
        loading=true;
        try {
            const orders = await MilkTeaApi.get('/api/v1/kitchen/delivery-orders');
            const list = $('kitchenDeliveryOrders');
            list.replaceChildren();
            if (!Array.isArray(orders) || !orders.length) {
                list.append(elem('p', 'Chưa có đơn giao hàng đã xác nhận cần pha chế.', 'text-muted small'));
            } else for (const order of orders) list.append(card(order));
        } catch (err) {
            const error=$('kitchenDeliveryError');
            error.textContent='Không tải được đơn giao hàng: ' + (err.message || 'Lỗi kết nối');
            error.classList.remove('d-none');
        } finally {
            loading=false;
            if(retry){retry=false;void load();}
        }
    }
    document.addEventListener('DOMContentLoaded', () => {
        if (!$('kitchenDeliveryOrders')) return;
        $('kitchenDeliveryRefresh').addEventListener('click',load);
        if (typeof MilkTeaRealtime !== 'undefined') {
            MilkTeaRealtime.on('DELIVERY_ORDER_CHANGED',load);
            MilkTeaRealtime.onReconnect(load);
        }
        void load();
        document.addEventListener('visibilitychange', () => {if (!document.hidden) void load();});
        window.setInterval(() => {if (!document.hidden) void load();},30000);
    });
})();
