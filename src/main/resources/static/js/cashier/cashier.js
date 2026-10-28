/**
 * UTE Tea Cashier POS · Menu, checkout & table selector
 * Tuân thủ frontend_rule.md và acceptance_tests.md
 */

const CashierApp = (() => {
    let products = [];
    let selectedCategory = "";
    let filtersReady = false;
    let counterCart = [];
    let pendingCounterOrder = null;
    let submitting = false;
    let creationKey = null;
    let creationFingerprint = null;
    let realtimeReady = false;
    let latestQuote = null;
    let quotedItems = null;
    let quoteRequest = 0;
    let quoteTimer = null;
    let lastPaymentMethod = null;
    let activeBranches = [];
    let bankAvailable = false;
    let pickupLoading = false;
    // randomUUID is unavailable on non-HTTPS LAN hosts. The key is for deduplication, not auth.
    const newRequestKey = () => globalThis.crypto?.randomUUID?.()
        || 'POS_' + Date.now() + '_' + Math.random().toString(36).slice(2);

    async function init() {
        window.MilkTeaContext = { role: document.getElementById('posRole')?.dataset.role || 'CASHIER' };
        MilkTeaRealtime.connect();

        if (!realtimeReady) { setupRealtime(); realtimeReady = true; }
        setupProducts();
        setupPaymentControls();
        renderCounterCart();
        await Promise.all([loadBranches(), loadBankSettings(), loadPickupOrders()]);
        refreshSubmitState();
        document.addEventListener('visibilitychange', () => { if (!document.hidden) void loadPickupOrders(); });
        setInterval(() => { if (!document.hidden) void loadPickupOrders(); }, 30000);
    }

    function selectedShopId() { return document.getElementById('posShopId')?.value || ''; }

    function refreshSubmitState() {
        const button = document.getElementById('counterSubmitBtn');
        if (!button) return;
        const canQuote = !!latestQuote && quotedItems === JSON.stringify(cartItems());
        button.disabled = submitting || !selectedShopId() || (!pendingCounterOrder && (!counterCart.length || !canQuote));
        if (pendingCounterOrder) {
            button.textContent = 'Ghi nhận thanh toán đơn #' + pendingCounterOrder.orderId;
        } else {
            button.innerHTML = '<i class="bi bi-check-circle-fill" aria-hidden="true"></i> Xác nhận thu tiền &amp; tạo đơn';
        }
    }

    async function loadBranches() {
        const select = document.getElementById('posShopId');
        const status = document.getElementById('posShopStatus');
        if (!select) return;
        try {
            const list = await MilkTeaApi.get('/api/v1/public/market/shops');
            activeBranches = (Array.isArray(list) ? list : []).filter(s => s.active);
            select.replaceChildren(new Option('Chọn chi nhánh đang bán hàng', ''));
            for (const shop of activeBranches) select.add(new Option(shop.name, String(shop.id)));
            if (activeBranches.length === 1) select.value = String(activeBranches[0].id);
            select.disabled = !activeBranches.length;
            status.textContent = activeBranches.length ? 'Đơn sẽ được ghi nhận tại chi nhánh đã chọn, phí giao bằng 0đ.' : 'Chưa có chi nhánh đang hoạt động. Liên hệ Admin để kích hoạt.';
        } catch (error) {
            select.replaceChildren(new Option('Không tải được chi nhánh', ''));
            select.disabled = true;
            status.textContent = 'Lỗi tải chi nhánh: ' + (error?.message || 'Không có kết nối');
        }
        select.addEventListener('change', refreshSubmitState);
        refreshSubmitState();
    }

    async function loadBankSettings() {
        const account = document.getElementById('posBankAccount');
        const holder = document.getElementById('posBankHolder');
        const title = document.getElementById('posBankTitle');
        const image = document.getElementById('posBankQr');
        const bankOption = document.querySelector('#counterPayMethod option[value="BANK_TRANSFER"]');
        if (!account) return;
        try {
            const settings = await MilkTeaApi.get('/api/v1/public/shop-info');
            const bank = String(settings?.bankName || '').trim();
            const number = String(settings?.bankAccountNumber || '').trim();
            const name = String(settings?.bankAccountHolder || '').trim();
            bankAvailable = !!(bank && number && name);
            title.textContent = bankAvailable ? 'Chuyển khoản ' + bank : 'Chưa có tài khoản nhận tiền';
            account.textContent = bankAvailable ? 'STK: ' + number : 'Admin cần cấu hình ngân hàng trong Settings.';
            holder.textContent = bankAvailable ? 'Chủ tài khoản: ' + name : '';
            // Static QR is for one MB account only; never display it for a different recipient.
            const mb = /(^|\W)(mb|mbbank|quan doi)(\W|$)/i.test(bank);
            image.hidden = !(mb && number === '0934197505' && name.toUpperCase() === 'KIEN NGUYEN XUAN LUU');
        } catch (error) {
            bankAvailable = false;
            title.textContent = 'Không thể tải tài khoản nhận tiền';
            account.textContent = error?.message || 'Kiểm tra kết nối hệ thống.';
            holder.textContent = '';
            image.hidden = true;
        }
        if (bankOption) bankOption.disabled = !bankAvailable;
        if (!bankAvailable && document.getElementById('counterPayMethod').value === 'BANK_TRANSFER') {
            document.getElementById('counterPayMethod').value = 'CASH';
        }
        updatePaymentFields();
    }

    async function loadPickupOrders() {
        if (pickupLoading) return;
        pickupLoading = true;
        try {
            const list = await MilkTeaApi.get('/api/v1/cashier/orders?tab=confirm');
            const incoming = (Array.isArray(list) ? list : []).filter(o => o.status === 'CHO_XAC_NHAN' && !o.staffCreated);
            const pendingCount = String(incoming.length);
            for (const id of ['posPickupPendingCount','posPickupPreviewCount']) {
                const badge = document.getElementById(id);
                if (badge) badge.textContent = pendingCount;
            }
            const container = document.getElementById('posPickupNewList');
            if (!container) return;
            container.replaceChildren();
            if (!incoming.length) {container.textContent = 'Không có đơn đặt trước cần xác nhận.'; return;}
            for (const order of incoming.slice(0,5)) {
                const row = document.createElement('div'); row.className = 'pos-delivery-live-item';
                const details = document.createElement('div');
                const name = document.createElement('strong'); name.textContent = 'Đơn #' + order.orderId + ' · ' + (order.customerName || 'Khách hàng');
                const note = document.createElement('small'); note.textContent = (order.pickupShopName || 'Chưa có chi nhánh') + ' · ' + MilkTeaApi.formatVND(order.totalAmount);
                details.append(name,note);
                const link = document.createElement('a'); link.href='/orders?order='+encodeURIComponent(order.orderId);
                link.className='btn btn-sm btn-outline-success'; link.textContent='Xử lý đơn';
                row.append(details,link); container.append(row);
            }
        } catch (error) {
            const container = document.getElementById('posPickupNewList');
            if (container) container.textContent = 'Chưa tải được đơn đặt trước: '+(error?.message || 'Lỗi kết nối');
        } finally {pickupLoading=false;}
    }

    function setupRealtime() {
        MilkTeaRealtime.on('ORDER_CREATED', () => {
            MilkTeaApi.showToast('Có đơn hàng mới!', 'success');
            void loadPickupOrders();
        });

        MilkTeaRealtime.on('ORDER_STATUS_CHANGED', () => {
            void loadPickupOrders();
        });

        MilkTeaRealtime.on('PAYMENT_RECORDED', () => { void loadPickupOrders(); });
        MilkTeaRealtime.on('SETTINGS_CHANGED', async () => {
            void loadBankSettings();
            try {
                const info = await MilkTeaApi.get('/api/v1/public/shop-info');
                if (info && info.logoUrl) {
                    document.querySelectorAll('.navbar-milktea .navbar-brand').forEach(brand => {
                        let img = brand.querySelector('img.navbar-shop-logo');
                        const badge = brand.querySelector('.navbar-brand-badge');
                        if (!img) {
                            img = document.createElement('img');
                            img.alt = 'Logo';
                            img.className = 'rounded-circle shadow-sm navbar-shop-logo';
                            img.style = 'width: 38px; height: 38px; object-fit: cover; border: 1px solid rgba(0,0,0,0.08);';
                            brand.prepend(img);
                        }
                        img.src = info.logoUrl;
                        img.style.display = 'inline-block';
                        if (badge) badge.style.setProperty('display', 'none', 'important');
                    });
                }
                if (info && info.shopName) {
                    document.querySelectorAll('.navbar-shop-name').forEach(el => el.textContent = 'UTE TEA');
                }
            } catch (e) {
                console.debug('Error refreshing shop info on SETTINGS_CHANGED:', e);
            }
        });

        MilkTeaRealtime.onReconnect(() => {
            void loadPickupOrders();
            void loadBankSettings();
        });
    }

    let activeToppingLineId = null;

    function isToppingProduct(p) {
        if (!p) return false;
        const catName = (p.categoryName || '').toLowerCase();
        return catName.includes('topping');
    }

    // POS Quầy: Thêm món vào giỏ quầy
    function addCounterCart(p) {
        if (submitting || pendingCounterOrder) { MilkTeaApi.showToast('Hoàn tất thanh toán đơn đang tạo trước.', 'warning'); return; }

        // Nếu đang ở chế độ thêm topping cho một dòng món
        if (activeToppingLineId) {
            const targetLine = counterCart.find(it => it.lineId === activeToppingLineId);
            if (!targetLine) {
                exitToppingMode();
            } else {
                const isTopping = p.isTopping || isToppingProduct(p);
                if (isTopping) {
                    const targetProd = products.find(prod => prod.id === targetLine.productId);
                    if (targetProd && targetProd.allowTopping === false) {
                        MilkTeaApi.showToast(`Món ${targetLine.productName} không cho phép chọn topping đi kèm!`, 'warning');
                        return;
                    }

                    if (!targetLine.toppings) targetLine.toppings = [];
                    const existsIdx = targetLine.toppings.findIndex(t => t.id === p.id);
                    if (existsIdx >= 0) {
                        targetLine.toppings.splice(existsIdx, 1);
                        MilkTeaApi.showToast(`Đã bỏ topping ${p.name}`, 'info');
                    } else {
                        targetLine.toppings.push({ id: p.id, name: p.name, price: p.price });
                        MilkTeaApi.showToast(`Đã thêm ${p.name} vào ${targetLine.productName}`, 'success');
                    }
                    renderCounterCart();
                    return;
                } else {
                    // Nhấn vào món nước khác -> Tự động kết thúc chế độ topping
                    exitToppingMode();
                }
            }
        }

        // Tạo dòng mới có stable lineId
        const lineId = 'line_' + Date.now() + '_' + Math.random().toString(36).slice(2, 7);
        counterCart.push({
            lineId: lineId,
            productId: p.id,
            productName: p.name,
            size: p.size,
            price: p.price,
            quantity: 1,
            imageUrl: p.imageUrl,
            note: '',
            toppings: [],
            noteOpen: false
        });
        renderCounterCart();
    }

    function enterToppingMode(lineId) {
        if (submitting || pendingCounterOrder) return;
        const item = counterCart.find(it => it.lineId === lineId);
        if (!item) return;

        const prod = products.find(p => p.id === item.productId);
        if (prod && prod.allowTopping === false) {
            MilkTeaApi.showToast(`Món ${item.productName} thuộc danh mục không cho phép chọn topping!`, 'warning');
            return;
        }

        activeToppingLineId = lineId;

        // Chuyển sang tab Topping
        const toppingBtn = [...document.querySelectorAll('#posCategories button')].find(b => b.textContent.trim().toLowerCase().includes('topping'));
        if (toppingBtn) {
            selectedCategory = toppingBtn.dataset.category;
            document.querySelectorAll('#posCategories button').forEach(el => el.classList.toggle('active', el === toppingBtn));
        }

        const banner = document.getElementById('posToppingModeBanner');
        if (banner) {
            document.getElementById('toppingTargetLineName').textContent = `${item.productName} (Size ${item.size})`;
            banner.classList.remove('d-none');
        }
        renderProducts();
        MilkTeaApi.showToast(`Chọn topping để thêm/bỏ cho ${item.productName}`, 'info');
    }

    function exitToppingMode() {
        activeToppingLineId = null;
        const banner = document.getElementById('posToppingModeBanner');
        if (banner) banner.classList.add('d-none');
        selectedCategory = '';
        document.querySelectorAll('#posCategories button').forEach((el, idx) => el.classList.toggle('active', idx === 0));
        renderProducts();
    }

    function changeLineSize(lineId, newProductId) {
        if (submitting || pendingCounterOrder) return;
        const item = counterCart.find(it => it.lineId === lineId);
        const newProd = products.find(p => p.id === Number(newProductId));
        if (item && newProd) {
            item.productId = newProd.id;
            item.size = newProd.size;
            item.price = newProd.price;
            renderCounterCart();
        }
    }

    function toggleLineNote(lineId) {
        if (submitting || pendingCounterOrder) return;
        const item = counterCart.find(it => it.lineId === lineId);
        if (item) {
            item.noteOpen = !item.noteOpen;
            renderCounterCart();
        }
    }

    function updateLineNote(lineId, val) {
        if (submitting || pendingCounterOrder) return;
        const item = counterCart.find(it => it.lineId === lineId);
        if (item) {
            item.note = val.trim();
        }
    }

    function removeLineTopping(lineId, toppingId) {
        if (submitting || pendingCounterOrder) return;
        const item = counterCart.find(it => it.lineId === lineId);
        if (item && item.toppings) {
            item.toppings = item.toppings.filter(t => t.id !== toppingId);
            renderCounterCart();
        }
    }

    function removeLine(lineId) {
        if (submitting || pendingCounterOrder) return;
        if (activeToppingLineId === lineId) exitToppingMode();
        counterCart = counterCart.filter(it => it.lineId !== lineId);
        renderCounterCart();
    }

    function renderCounterCart() {
        if (!pendingCounterOrder) { latestQuote = null; quotedItems = null; }
        const container = document.getElementById('counterCartList');
        if (!container) return;

        const itemCount = counterCart.reduce((n, i) => n + i.quantity, 0);
        document.getElementById('counterItemCount').textContent = '(' + itemCount + ')';
        const mobileCount = document.getElementById('posMobileItemCount');
        if (mobileCount) mobileCount.textContent = itemCount + ' món';
        if (counterCart.length === 0) {
            container.innerHTML = '<div class="pos-empty-cart"><i class="bi bi-bag" aria-hidden="true"></i><strong>Giỏ hàng đang trống</strong><span>Chọn món ở phần thực đơn để bắt đầu tạo đơn.</span></div>';
            updateCheckoutSummary(0);
            scheduleQuote();
            return;
        }

        let total = 0;
        let html = '';
        counterCart.forEach((it) => {
            const toppingSum = (it.toppings || []).reduce((acc, t) => acc + (t.price || 0), 0);
            const unitPrice = (it.price || 0) + toppingSum;
            const lineTotal = unitPrice * it.quantity;
            total += lineTotal;

            // Lấy danh sách size từ catalog cho sản phẩm này
            const variants = products.filter(p => p.name === it.productName);

            let sizeSelector = '';
            if (variants.length > 1) {
                sizeSelector = `<select class="form-select form-select-sm py-0 px-2 my-1" style="font-size: 0.8rem; height: 26px;" onchange="CashierApp.changeLineSize('${it.lineId}', this.value)">
                    ${variants.map(v => `<option value="${v.id}" ${v.id === it.productId ? 'selected' : ''}>Size ${v.size} (${MilkTeaApi.formatVND(v.price)})</option>`).join('')}
                </select>`;
            } else {
                sizeSelector = `<small class="text-muted d-block my-1">Size ${it.size} · ${MilkTeaApi.formatVND(it.price)}</small>`;
            }

            const isCurrentToppingTarget = activeToppingLineId === it.lineId;
            const targetProd = products.find(p => p.id === it.productId);
            const isProdTopping = targetProd ? (targetProd.isTopping || isToppingProduct(targetProd)) : false;
            const canHaveTopping = !isProdTopping && (!targetProd || targetProd.allowTopping !== false);

            let toppingControlHtml = '';
            if (canHaveTopping) {
                toppingControlHtml = `
                    <div class="my-1">
                        <button type="button" class="btn btn-sm btn-link p-0 text-decoration-none fw-semibold" style="font-size: 0.8rem;" onclick="CashierApp.enterToppingMode('${it.lineId}')">
                            <i class="bi bi-plus-circle me-1"></i>${it.toppings && it.toppings.length ? 'Sửa topping' : 'Thêm topping'}
                        </button>
                        <div class="d-flex flex-wrap gap-1 mt-1">
                            ${(it.toppings || []).map(t => `
                                <span class="badge bg-light text-dark border d-inline-flex align-items-center py-1 px-2" style="font-size: 0.75rem;">
                                    ${MilkTeaReceipts.esc(t.name)} (+${MilkTeaApi.formatVND(t.price)})
                                    <button type="button" class="btn-close ms-1 p-0" style="font-size: 0.5rem;" aria-label="Xóa topping" onclick="CashierApp.removeLineTopping('${it.lineId}', ${t.id})"></button>
                                </span>
                            `).join('')}
                        </div>
                    </div>
                `;
            } else {
                toppingControlHtml = `
                    <div class="my-1">
                        <small class="text-muted fst-italic" style="font-size: 0.75rem;">Không áp dụng topping</small>
                    </div>
                `;
            }

            html += `
                <div class="pos-cart-item ${isCurrentToppingTarget ? 'border border-primary bg-primary-subtle' : ''}">
                    <img src="${MilkTeaReceipts.esc(it.imageUrl || '/images/ute-tea-product-fallback.svg')}" alt="${MilkTeaReceipts.esc(it.productName)}" onerror="this.onerror=null;this.src='/images/ute-tea-product-fallback.svg'">
                    <div class="pos-cart-info">
                        <div class="d-flex justify-content-between gap-2">
                            <strong>${MilkTeaReceipts.esc(it.productName)}</strong>
                            <button class="btn btn-sm p-0 text-muted" aria-label="Xóa món" onclick="CashierApp.removeLine('${it.lineId}')">×</button>
                        </div>
                        ${sizeSelector}

                        <!-- Topping gắn vào món -->
                        ${toppingControlHtml}

                        <!-- Ghi chú dòng món -->
                        <div class="d-flex align-items-center gap-2 mt-1">
                            <button type="button" class="btn btn-sm btn-link p-0 text-secondary" title="Ghi chú món" onclick="CashierApp.toggleLineNote('${it.lineId}')">
                                <i class="bi bi-chat-left-text${it.note ? '-fill text-primary' : ''}"></i>
                            </button>
                            ${it.note && !it.noteOpen ? `<small class="text-muted fst-italic text-truncate" style="max-width: 140px;">${MilkTeaReceipts.esc(it.note)}</small>` : ''}
                        </div>
                        ${it.noteOpen ? `
                            <input type="text" class="form-control form-control-sm mt-1" style="font-size: 0.8rem;" placeholder="Ghi chú món này..." value="${MilkTeaReceipts.esc(it.note || '')}" oninput="CashierApp.updateLineNote('${it.lineId}', this.value)">
                        ` : ''}

                        <div class="d-flex justify-content-between align-items-center mt-2">
                            <div class="pos-qty">
                                <button aria-label="Giảm số lượng" onclick="CashierApp.adjustCounterCart('${it.lineId}', -1)">−</button>
                                <span>${it.quantity}</span>
                                <button aria-label="Tăng số lượng" onclick="CashierApp.adjustCounterCart('${it.lineId}', 1)">+</button>
                            </div>
                            <span class="pos-cart-price">${MilkTeaApi.formatVND(lineTotal)}</span>
                        </div>
                    </div>
                </div>
            `;
        });
        container.innerHTML = html;
        latestQuote = null;
        quotedItems = null;
        updateCheckoutSummary(total);
        scheduleQuote();
        refreshSubmitState();
    }

    function adjustCounterCart(lineId, delta) {
        if (submitting || pendingCounterOrder) return;
        const it = counterCart.find(i => i.lineId === lineId);
        if (!it) return;
        it.quantity += delta;
        if (it.quantity <= 0) {
            removeLine(lineId);
        } else {
            renderCounterCart();
        }
    }

    function cartItems() {
        return counterCart.map(it => ({
            productId: it.productId, quantity: it.quantity,
            note: it.note || '', toppingIds: (it.toppings || []).map(t => t.id)
        }));
    }

    function setPendingOrderControls(locked) {
        const shopSelect = document.getElementById('posShopId');
        if (shopSelect) shopSelect.disabled = locked || !activeBranches.length;
        document.getElementById('counterPayMethod').disabled = locked;
        const clear = document.querySelector('.pos-clear-btn');
        if (clear) clear.disabled = locked;
    }

    function setupPaymentControls() {
        document.getElementById('counterPayMethod').addEventListener('change', updatePaymentFields);
        document.getElementById('counterCashReceived').addEventListener('input', updateChangeDue);
        document.getElementById('posSuccessReceiptBtn').addEventListener('click', () => {
            const id = document.getElementById('posSuccessReceiptBtn').dataset.orderId;
            bootstrap.Modal.getOrCreateInstance(document.getElementById('posPaymentSuccessModal')).hide();
            if (id) MilkTeaReceipts.open(id);
        });
        updatePaymentFields();
    }

    function updatePaymentFields() {
        const cash = document.getElementById('counterPayMethod').value === 'CASH';
        document.getElementById('posCashTender').hidden = !cash;
        document.getElementById('posTransferConfirmation').hidden = cash;
        if (cash) document.getElementById('counterTransferConfirmed').checked = false;
        updateChangeDue();
    }

    function updateChangeDue() {
        const paid = Number(document.getElementById('counterCashReceived')?.value);
        const total = Number(pendingCounterOrder?.totalAmount ?? latestQuote?.totalAmount ?? 0);
        const change = Number.isFinite(paid) && paid >= total && total > 0 ? paid - total : 0;
        document.getElementById('posChangeDue').textContent = MilkTeaApi.formatVND(change);
    }

    function updateCheckoutSummary(localSubtotal) {
        const money = MilkTeaApi.formatVND;
        document.getElementById('counterCartTotal').textContent = money(localSubtotal);
        const final = pendingCounterOrder?.totalAmount ?? latestQuote?.totalAmount ?? localSubtotal;
        const discount = pendingCounterOrder?.discountAmount ?? latestQuote?.discountAmount ?? 0;
        document.getElementById('posDiscountAmount').textContent = '−' + money(discount);
        document.getElementById('posFinalTotal').textContent = money(final);
        document.getElementById('posMobileCartTotal').textContent = money(final);
        updateChangeDue();
    }

    function showQuoteStatus(text, isError = false) {
        const el = document.getElementById('posPriceStatus');
        el.textContent = text;
        el.classList.toggle('text-danger', isError);
    }

    function scheduleQuote() {
        clearTimeout(quoteTimer);
        const requestId = ++quoteRequest;
        if (pendingCounterOrder) return;
        latestQuote = null;
        quotedItems = null;
        if (!counterCart.length) {
            showQuoteStatus('Thêm món để xem tổng tiền chính xác.');
            refreshSubmitState();
            return;
        }
        refreshSubmitState();
        showQuoteStatus('Đang đối chiếu giá món và ưu đãi của cửa hàng...');
        quoteTimer = setTimeout(async () => {
            const items = cartItems();
            const fingerprint = JSON.stringify(items);
            try {
                const quote = await MilkTeaApi.post('/api/v1/cashier/orders/quote', { items });
                if (requestId !== quoteRequest) return;
                latestQuote = quote;
                quotedItems = fingerprint;
                updateCheckoutSummary(Number(quote.subtotal));
                showQuoteStatus('Đã kiểm tra giá với hệ thống. Tổng tiền gồm giảm giá hiện hành.');
                refreshSubmitState();
            } catch (e) {
                if (requestId !== quoteRequest) return;
                showQuoteStatus('Chưa xác minh được giá: ' + (e.message || 'Lỗi kết nối') + '. Không thu tiền trước khi kiểm tra lại.', true);
                refreshSubmitState();
            }
        }, 200);
    }

    async function submitCounterOrder() {
        if (submitting) return;
        if (counterCart.length === 0 && !pendingCounterOrder) {
            MilkTeaApi.showToast('Vui lòng chọn món trước khi tạo đơn', 'warning');
            return;
        }
        const shopId = selectedShopId();
        if (!pendingCounterOrder && !shopId) {
            MilkTeaApi.showToast('Vui lòng chọn chi nhánh bán hàng trước khi thanh toán.', 'warning');
            document.getElementById('posShopId')?.focus();
            return;
        }
        if (!pendingCounterOrder && (!latestQuote || quotedItems !== JSON.stringify(cartItems()))) {
            MilkTeaApi.showToast('Vui lòng chờ hệ thống kiểm tra giá món.', 'warning');
            scheduleQuote();
            return;
        }
        submitting = true;
        const submitBtn = document.getElementById('counterSubmitBtn');
        submitBtn.disabled = true;
        let paid = false;
        try {
            const items = cartItems();
            let created = pendingCounterOrder;
            if (!created) {
                // Confirm the exact amount using the server's snapshot before creating an order.
                const quoted = await MilkTeaApi.post('/api/v1/cashier/orders/quote', { items });
                const newFingerprint = JSON.stringify(items);
                if (!latestQuote || Number(latestQuote.totalAmount) !== Number(quoted.totalAmount)
                        || Number(latestQuote.subtotal) !== Number(quoted.subtotal)) {
                    latestQuote = quoted;
                    quotedItems = newFingerprint;
                    updateCheckoutSummary(Number(quoted.subtotal));
                    showQuoteStatus('Giá vừa được cập nhật. Hãy xác nhận tổng tiền mới trước khi thu.', true);
                    MilkTeaApi.showToast('Giá đã cập nhật. Vui lòng kiểm tra lại tổng tiền và bấm xác nhận lần nữa.', 'warning');
                    return;
                }
            }
            const paymentMethod = document.getElementById('counterPayMethod').value;
            const amount = Number(created?.totalAmount ?? latestQuote?.totalAmount);
            if (!Number.isFinite(amount) || amount <= 0) throw new Error('Tổng tiền hóa đơn không hợp lệ.');
            if (paymentMethod === 'CASH') {
                const cashField = document.getElementById('counterCashReceived');
                const cash = Number(cashField.value);
                if (cashField.value.trim() === '' || !Number.isFinite(cash) || cash < amount) {
                    cashField.focus();
                    MilkTeaApi.showToast('Tiền khách đưa phải lớn hơn hoặc bằng số tiền cần thanh toán.', 'warning');
                    return;
                }
            } else if (!document.getElementById('counterTransferConfirmed').checked) {
                MilkTeaApi.showToast('Hãy xác nhận đã nhận đủ tiền chuyển khoản trước khi ghi thu.', 'warning');
                return;
            }
            if (!created) {
                const payload = { shopId: Number(shopId), items };
                const fingerprint = JSON.stringify(payload);
                if (fingerprint !== creationFingerprint) {
                    creationFingerprint = fingerprint;
                    creationKey = newRequestKey();
                }
                created = await MilkTeaApi.post('/api/v1/orders/counter', payload, { 'Idempotency-Key': creationKey });
                pendingCounterOrder = created;
                lastPaymentMethod = paymentMethod;
                setPendingOrderControls(true);
                if (Number(created.totalAmount) !== Number(latestQuote.totalAmount)) {
                    updateCheckoutSummary(Number(created.subtotal));
                    showQuoteStatus('Hóa đơn đã lập với số tiền mới. Kiểm tra lại trước khi ghi thu.', true);
                    MilkTeaApi.showToast('Giá thay đổi khi lập hóa đơn. Đơn đã được lưu, vui lòng kiểm tra lại số tiền trước khi xác nhận thu.', 'warning');
                    return;
                }
            }
            const method = lastPaymentMethod || paymentMethod;
            if (document.getElementById('counterPayMethod').value !== method) {
                document.getElementById('counterPayMethod').value = method;
                updatePaymentFields();
                MilkTeaApi.showToast('Phương thức đã cố định sau khi tạo đơn. Vui lòng xác nhận thu lại.', 'warning');
                return;
            }
            await MilkTeaApi.post(`/api/v1/cashier/orders/${created.orderId}/payments`,
                { method, reference: 'POS_' + created.orderId },
                { 'Idempotency-Key': 'POS_PAYMENT_' + created.orderId });
            paid = true;
            pendingCounterOrder = null;
            creationKey = null; creationFingerprint = null; lastPaymentMethod = null;
            setPendingOrderControls(false);
            const success = document.getElementById('posPaymentSuccessModal');
            document.getElementById('posSuccessOrderCode').textContent = MilkTeaReceipts.code(created.orderId);
            document.getElementById('posSuccessAmount').textContent = MilkTeaApi.formatVND(created.totalAmount);
            document.getElementById('posSuccessMethod').textContent = method === 'CASH' ? 'Tiền mặt' : 'Chuyển khoản';
            const shop = activeBranches.find(s => String(s.id) === shopId);
            document.getElementById('posSuccessBranch').textContent = created.pickupShopName || shop?.name || 'Quầy bán hàng';
            document.getElementById('posSuccessReceiptBtn').dataset.orderId = String(created.orderId);
            document.getElementById('posSuccessMessage').textContent = 'Đã ghi nhận thanh toán và gửi đơn mang đi cho bếp. Hướng dẫn khách nhận món tại chi nhánh đã chọn.';
            counterCart = [];
            document.getElementById('counterCashReceived').value = '';
            document.getElementById('counterTransferConfirmed').checked = false;
            document.getElementById('counterPayMethod').value = 'CASH';
            updatePaymentFields();
            clearTimeout(quoteTimer);
            quoteRequest++;
            latestQuote = null; quotedItems = null;
            exitToppingMode();
            renderCounterCart();
            bootstrap.Modal.getOrCreateInstance(success).show();
            MilkTeaApi.showToast(`Đã thanh toán thành công đơn ${MilkTeaReceipts.code(created.orderId)}!`, 'success');
            // Refresh is not part of the payment transaction; failure must not lead to retry.
            void loadPickupOrders();
        } catch (e) {
            MilkTeaApi.showToast(e.message || 'Không thể tạo đơn / ghi nhận thanh toán', 'error');
            if (pendingCounterOrder) {
                submitBtn.textContent = 'Thử lại thanh toán đơn #' + pendingCounterOrder.orderId;
            }
        } finally {
            submitting = false;
            submitBtn.disabled = false;
            refreshSubmitState();
        }
    }

    function setupProducts() {
        products = [...document.querySelectorAll('#posProductsData [data-id]')].map(el => {
            const catName = el.dataset.categoryName || '';
            const isTopping = catName.toLowerCase().includes('topping');
            return {
                id: Number(el.dataset.id),
                name: el.dataset.name,
                size: el.dataset.size,
                price: Number(el.dataset.price),
                imageUrl: el.dataset.image,
                category: el.dataset.category,
                categoryName: catName,
                allowTopping: el.dataset.categoryAllowTopping !== 'false',
                isTopping: isTopping
            };
        });
        if (!filtersReady) {
            document.getElementById('posProductSearch').addEventListener('input', renderProducts);
            document.getElementById('posCategories').addEventListener('click', event => {
                const b = event.target.closest('[data-category]'); if (!b) return;
                selectedCategory = b.dataset.category;
                document.querySelectorAll('#posCategories button').forEach(el => el.classList.toggle('active', el === b)); renderProducts();
            });
            document.getElementById('posProductGrid').addEventListener('click', event => {
                const b = event.target.closest('[data-product-id]'); if (b) addCounterCart(products.find(p => String(p.id) === b.dataset.productId));
            }); filtersReady = true;
        } renderProducts();
    }
    function renderProducts() {
        const query = document.getElementById('posProductSearch').value.trim().toLocaleLowerCase('vi');
        const groups = new Map();
        products.filter(p => (!selectedCategory || p.category === selectedCategory) && p.name.toLocaleLowerCase('vi').includes(query)).forEach(p => {
            const key = p.name + '|' + p.category; if (!groups.has(key)) groups.set(key, []); groups.get(key).push(p);
        });
        document.getElementById('posProductGrid').innerHTML = [...groups.values()].map(variants => {
            variants.sort((a, b) => a.price - b.price); const p = variants[0], esc = MilkTeaReceipts.esc;
            return `<article class="pos-product"><img src="${esc(p.imageUrl || '/images/ute-tea-product-fallback.svg')}" alt="${esc(p.name)}" loading="lazy" onerror="this.onerror=null;this.src='/images/ute-tea-product-fallback.svg'"><div class="pos-product-body"><h6>${esc(p.name)}</h6><div class="pos-product-price">${variants.length > 1 ? 'Từ ' : ''}${MilkTeaApi.formatVND(p.price)}</div><div class="pos-sizes">${variants.map(v => `<button data-product-id="${v.id}" title="Size ${esc(v.size)} · ${MilkTeaApi.formatVND(v.price)}">${esc(v.size)}</button>`).join('')}</div></div></article>`;
        }).join('') || '<p class="text-muted p-3">Không có món phù hợp.</p>';
    }
    function clearCart() { if (submitting || pendingCounterOrder) return; counterCart = []; exitToppingMode(); renderCounterCart(); }
    function removeItem(idx) { if (submitting || pendingCounterOrder) return; counterCart.splice(idx, 1); renderCounterCart(); }
    return {
        init,
        addCounterCart,
        adjustCounterCart,
        submitCounterOrder,
        clearCart,
        removeItem,
        removeLine,
        enterToppingMode,
        exitToppingMode,
        changeLineSize,
        toggleLineNote,
        updateLineNote,
        removeLineTopping
    };
})();
