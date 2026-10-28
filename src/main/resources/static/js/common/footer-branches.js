/* Footer branch names/addresses come from Shop, the same source as customer checkout. */
(() => {
    async function render() {
        const host = document.getElementById('uteFooterBranches');
        if (!host) return;
        try {
            const response = await MilkTeaApi.get('/api/v1/public/market/shops');
            if (!Array.isArray(response)) throw new Error('Dữ liệu cơ sở không hợp lệ');
            host.replaceChildren();
            if (!response.length) {
                const item = document.createElement('li');
                item.className = 'small';
                item.textContent = 'Chưa có cơ sở đang hoạt động. Vui lòng liên hệ UTE Tea.';
                host.append(item);
                return;
            }
            for (const branch of response) {
                const item = document.createElement('li');
                item.className = 'small mb-2';
                const name = document.createElement('strong');
                name.className = 'd-block';
                name.textContent = branch.name || 'UTE Tea';
                const address = document.createElement('span');
                address.textContent = branch.address || 'Địa chỉ đang cập nhật';
                item.append(name, address);
                host.append(item);
            }
        } catch (error) {
            // Failure to load optional footer data must never break the storefront.
            host.replaceChildren();
            const item = document.createElement('li');
            item.className = 'small';
            item.textContent = 'Danh sách cơ sở tạm thời chưa tải được. Xem cơ sở khi đặt hàng.';
            host.append(item);
        }
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', render, {once:true});
    else render();
})();
