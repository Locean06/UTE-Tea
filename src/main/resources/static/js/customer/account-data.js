/* Render account-owned data as text, never HTML. */
(() => {
    const api = '/api/v1/account';
    const form = document.getElementById('addressForm');
    function element(tag, text, className) {
        const node = document.createElement(tag);
        if (text != null) node.textContent = text;
        if (className) node.className = className;
        return node;
    }
    function action(text, handler) {
        const button = element('button', text, 'btn btn-sm btn-outline-secondary me-2 mb-2');
        button.type = 'button'; button.addEventListener('click', () => handler().catch(showError)); return button;
    }
    function showError(error) {
        const message = document.getElementById('customerDataMessage');
        message.className = 'alert alert-danger'; message.textContent = error.message;
    }
    async function load() {
        const addresses = await MilkTeaApi.get(api + '/addresses');
        const list = document.getElementById('addressList'); list.replaceChildren();
        if (!addresses.length) list.append(element('p', 'Chưa có địa chỉ.'));
        for (const a of addresses) {
            const row = element('div', null, 'border-bottom py-3');
            row.append(element('strong', a.recipient + (a.defaultAddress ? ' · Mặc định' : '')),
                element('p', a.phone + ' · ' + a.addressLine, 'small text-break mb-2'));
            row.append(action('Sửa', async () => {
                for (const key of ['id','recipient','phone','addressLine']) form.elements[key].value = a[key];
                form.elements.defaultAddress.checked = a.defaultAddress;
            }), action('Đặt mặc định', async () => { await MilkTeaApi.put(api + '/addresses/' + a.id, {...a, defaultAddress:true}); await load(); }),
            action('Xóa', async () => { await MilkTeaApi.delete(api + '/addresses/' + a.id); await load(); }));
            list.append(row);
        }
        for (const [path, target] of [['favorites','favoriteList'],['viewed','viewedList']]) {
            const items = await MilkTeaApi.get(api + '/' + path);
            const container = document.getElementById(target); container.replaceChildren();
            if (!items.length) container.append(element('p', 'Chưa có món.', 'text-muted small'));
            for (const item of items) {
                const row = element('div', null, 'd-flex flex-wrap align-items-center gap-2 border-bottom py-2');
                const link = element('a', item.product.name + ' · ' + MilkTeaApi.formatVND(item.product.price), 'text-break me-auto');
                link.href = '/?product=' + item.product.id + '#uteCatalog'; row.append(link);
                if (path === 'favorites') row.append(action('Bỏ yêu thích', async () => { await MilkTeaApi.put(api+'/favorites/'+item.product.id+'?enabled=false'); await load(); }));
                container.append(row);
            }
        }
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const values = new FormData(form), id = values.get('id');
        const input = {recipient:values.get('recipient'),phone:values.get('phone'),addressLine:values.get('addressLine'),defaultAddress:form.elements.defaultAddress.checked};
        try { await MilkTeaApi[id ? 'put' : 'post'](api+'/addresses'+(id?'/'+id:''), input); form.reset(); await load(); }
        catch (error) { showError(error); }
    });
    load().catch(showError);
})();
