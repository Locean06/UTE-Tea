/**
 * MilkTea Customer Client Logic (Menu, Cart, Orders, Realtime)
 * Tuân thủ frontend_rule.md và author_rule.md
 */

window.MilkTeaContext = {
    tableId: null,
    tableName: null,
    qrCode: null,
    sessionId: null,
    sessionStatus: null,
    tableToken: null,
    cartVersion: "0"
};

const CustomerApp = (() => {
    let localCart = []; // Giỏ RAM khi chưa có phiên bàn mở
    let serverCart = []; // Giỏ server khi đã có phiên bàn mở
    const accountId = document.body.dataset.accountId;
    let accountCartVersion = '0';
    let cartWrite = Promise.resolve();
    let contextReady = null;

    function getLocalCartStorageKey() {
        const ctx = window.MilkTeaContext || {};
        if (ctx.qrCode) return `milktea.localCart.qr.${ctx.qrCode}`;
        if (ctx.tableId) return `milktea.localCart.table.${ctx.tableId}`;
        return 'milktea.localCart';
    }

    function loadLocalCart() {
        try {
            const raw = localStorage.getItem(getLocalCartStorageKey());
            localCart = raw ? JSON.parse(raw) : [];
            if (!Array.isArray(localCart)) localCart = [];
        } catch (e) {
            localCart = [];
        }
    }

    async function saveLocalCart() {
        if (accountId) {
            const items = localCart.map(it => ({productId: it.productId, quantity: it.quantity, note: it.note, toppingIds: extractToppingIds(it)}));
            cartWrite = cartWrite.catch(() => {}).then(async () => {
                try {
                    const cart = await MilkTeaApi.put('/api/v1/account/cart', {items}, {'If-Match': accountCartVersion});
                    accountCartVersion = cart.version;
                    localCart = cart.items.map(it => ({...it, price: it.unitPrice}));
                } catch (e) {
                    const cart = await MilkTeaApi.get('/api/v1/account/cart');
                    accountCartVersion = cart.version;
                    localCart = cart.items.map(it => ({...it, price: it.unitPrice}));
                    MilkTeaApi.showToast(e.message, 'error');
                    throw e;
                } finally { updateCartBadge(); }
            });
            return cartWrite;
        }
        localStorage.setItem(getLocalCartStorageKey(), JSON.stringify(localCart));
    }

    async function clearLocalCart() {
        localCart = [];
        if (accountId) return saveLocalCart();
        localStorage.removeItem(getLocalCartStorageKey());
    }

    // Khách đặt hàng trực tuyến: nhận tại quầy / mang về. Bàn do thu ngân gán trong POS.
    function initContext() {
        if(!contextReady)contextReady=initializeContext().catch(error=>{contextReady=null;throw error;});
        return contextReady;
    }
    async function initializeContext() {
        const params = new URLSearchParams(location.search);
        const explicitQr = params.get('qrCode') || params.get('qr') || (params.get('table')?.startsWith('TABLE_QR_') ? params.get('table') : null);
        const qr = explicitQr || sessionStorage.getItem('uteTea.scannedQr');
        if (qr) {
            try {
                const context = await MilkTeaApi.get('/api/v1/public/table-context?qrCode='+encodeURIComponent(qr));
                Object.assign(window.MilkTeaContext,context,{qrCode:qr});
                sessionStorage.setItem('uteTea.scannedQr',qr);
            } catch (e) {
                sessionStorage.removeItem('uteTea.scannedQr');
                if (explicitQr) throw e;
            }
        }
        window.MilkTeaContext.counterToken = sessionStorage.getItem('milktea.counterToken');
        if (accountId) {
            const cart = await MilkTeaApi.get('/api/v1/account/cart');
            accountCartVersion = cart.version;
            localCart = cart.items.map(it => ({...it, price: it.unitPrice}));
            try {
                const guest=JSON.parse(localStorage.getItem(getLocalCartStorageKey())||'[]');
                if(Array.isArray(guest)&&guest.length){localCart.push(...guest);await saveLocalCart();localStorage.removeItem(getLocalCartStorageKey());}
            } catch(e) {MilkTeaApi.showToast('Giỏ tạm vẫn được giữ trên máy. '+e.message,'warning');}
        } else { loadLocalCart(); }
        updateCartBadge();
        setupRealtimeListeners();
    }

    function setupRealtimeListeners() {
        MilkTeaRealtime.on('CART_CHANGED', (event) => {
            console.log('Giỏ hàng thay đổi từ thiết bị khác. Đang tải lại giỏ hàng...');
            // Personal account cart is refreshed by its versioned write path.
        });

        MilkTeaRealtime.on('ORDER_STATUS_CHANGED', (event) => {
            console.log('Trạng thái đơn hàng cập nhật:', event);
            if (typeof loadOrdersList === 'function') {
                loadOrdersList();
            }
        });

        MilkTeaRealtime.on('SETTINGS_CHANGED', async () => {
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
            console.log('WebSocket reconnect: Làm mới dữ liệu từ server...');

            if (typeof loadOrdersList === 'function') {
                loadOrdersList();
            }
        });
    }

    // Thêm món vào giỏ
    async function addToCart(product, quantity = 1, note = '', selectedToppings = []) {
        await initContext();
        const toppingIds = (selectedToppings || []).map(t => Number(t.id || t.productId)).filter(Boolean).sort((a, b) => a - b);
        const toppingSum = (selectedToppings || []).reduce((acc, t) => acc + (Number(t.price) || 0), 0);
        const unitPriceWithTopping = (Number(product.price) || 0) + toppingSum;
        const lineTotal = unitPriceWithTopping * parseInt(quantity);

        const item = {
            productId: product.id,
            productName: product.name,
            size: product.size,
            price: product.price,
            quantity: parseInt(quantity),
            note: note || '',
            toppingIds: toppingIds,
            toppings: selectedToppings || [],
            lineTotal: lineTotal
        };

            const tKey = toppingIds.join(',');
            const existing = localCart.find(it =>
                it.productId === item.productId &&
                (it.note || '') === (item.note || '') &&
                ((it.toppingIds || []).slice().sort((a, b) => a - b).join(',')) === tKey
            );
            if (existing) {
                existing.quantity += item.quantity;
                const existingToppingSum = (existing.toppings || []).reduce((acc, t) => acc + (Number(t.price) || 0), 0);
                existing.lineTotal = (existing.price + existingToppingSum) * existing.quantity;
            } else {
                localCart.push(item);
            }
            updateCartBadge(true);
            await saveLocalCart();
            updateCartBadge();
            MilkTeaApi.showToast(`Đã thêm ${item.productName} vào giỏ`, 'success');
    }

    function extractToppingIds(item) {
        if (!item) return [];
        if (Array.isArray(item.toppingIds) && item.toppingIds.length > 0) {
            return item.toppingIds.map(Number).filter(Boolean);
        }
        if (Array.isArray(item.toppings) && item.toppings.length > 0) {
            return item.toppings.map(t => Number(t.id || t.productId)).filter(Boolean);
        }
        return [];
    }

    async function loadServerCart() {
        if (!window.MilkTeaContext.sessionId) return;
        try {
            const cartData = await MilkTeaApi.get(`/api/v1/table-sessions/${window.MilkTeaContext.sessionId}/cart`);
            if (cartData) {
                window.MilkTeaContext.cartVersion = cartData.version;
                serverCart = (cartData.items || []).map(it => ({
                    ...it,
                    toppingIds: extractToppingIds(it)
                }));
                updateCartBadge();
                if (typeof renderCartItems === 'function') {
                    renderCartItems(serverCart, cartData.totalAmount);
                }
            }
        } catch (e) {
            console.warn('Lỗi khi tải giỏ hàng server:', e);
        }
    }

    async function addServerCartItem(newItem) {
        const itemsToSend = [...serverCart.map(it => ({
            productId: it.productId,
            quantity: it.quantity,
            note: it.note,
            toppingIds: extractToppingIds(it)
        }))];

        const newTKey = extractToppingIds(newItem).slice().sort((a, b) => a - b).join(',');
        const existing = itemsToSend.find(it =>
            it.productId === newItem.productId &&
            (it.note || '') === (newItem.note || '') &&
            (it.toppingIds || []).slice().sort((a, b) => a - b).join(',') === newTKey
        );
        if (existing) {
            existing.quantity += newItem.quantity;
        } else {
            itemsToSend.push({
                productId: newItem.productId,
                quantity: newItem.quantity,
                note: newItem.note,
                toppingIds: extractToppingIds(newItem)
            });
        }

        try {
            const ifMatch = `"${window.MilkTeaContext.cartVersion}"`;
            const updatedCart = await MilkTeaApi.put(
                `/api/v1/table-sessions/${window.MilkTeaContext.sessionId}/cart`,
                { items: itemsToSend },
                { 'If-Match': ifMatch }
            );
            window.MilkTeaContext.cartVersion = updatedCart.version;
            serverCart = (updatedCart.items || []).map(it => ({
                ...it,
                toppingIds: extractToppingIds(it)
            }));
            updateCartBadge();
            MilkTeaApi.showToast(`Đã thêm ${newItem.productName} vào giỏ hàng`, 'success');
        } catch (e) {
            MilkTeaApi.showToast(e.message || 'Lỗi khi cập nhật giỏ hàng', 'error');
            await loadServerCart(); // Tải lại bản mới nếu bị xung đột version
            throw e;
        }
    }

    async function updateQuantity(index, delta) {
        const currentItems = localCart;
        if (!currentItems[index]) return;

        const newQty = currentItems[index].quantity + delta;
        if (newQty <= 0) {
            currentItems.splice(index, 1);
        } else {
            currentItems[index].quantity = newQty;
            const toppingSum = (currentItems[index].toppings || []).reduce((acc, t) => acc + (Number(t.price) || 0), 0);
            currentItems[index].lineTotal = ((currentItems[index].price || currentItems[index].unitPrice || 0) + toppingSum) * newQty;
        }

        updateCartBadge(true);
        try { await saveLocalCart(); } catch (_) { return false; }

        updateCartBadge();
        if (typeof renderCartItems === 'function') {
            const updatedItems=localCart;
            const total = updatedItems.reduce((acc, it) => acc + (it.lineTotal || (it.price || it.unitPrice || 0) * it.quantity), 0);
            renderCartItems(updatedItems, total);
        }
        return true;
    }

    async function removeItem(index) {
        await updateQuantity(index, -999999);
    }
    async function replaceItem(index,product,quantity,note,toppings) {
        const items=localCart;
        if(!items[index])throw Error('Món trong giỏ đã thay đổi. Vui lòng tải lại.');
        items[index]={productId:product.id,productName:product.name,size:product.size,price:Number(product.price),quantity,note,
            toppings,toppingIds:toppings.map(t=>t.id),lineTotal:(Number(product.price)+toppings.reduce((sum,t)=>sum+Number(t.price),0))*quantity};
        if(await updateQuantity(index,0)===false)throw Error('Chưa lưu được thay đổi. Vui lòng kiểm tra giỏ mới nhất.');
    }

    function updateCartBadge(pending=false) {
        const badges = document.querySelectorAll('#cartCountBadge, [data-cart-count]');
        const currentItems = localCart;
        const count = currentItems.reduce((acc, it) => acc + it.quantity, 0);
        badges.forEach(badge => {
            badge.textContent = count;
            badge.style.display = count > 0 ? 'inline-block' : 'none';
        });
        document.dispatchEvent(new CustomEvent('ute:cart-changed',{detail:{items:currentItems,version:accountCartVersion,pending}}));
    }

    // Đặt đơn hàng
    async function submitOrder(checkout = {}) {
        await cartWrite;
        const currentItems = localCart;
        if (currentItems.length === 0) {
            MilkTeaApi.showToast('Giỏ hàng đang trống!', 'warning');
            return;
        }

        const btn = document.getElementById('checkoutBtn');
        if (btn) btn.disabled = true;

        try {
            const items = currentItems.map(it => ({
                productId: it.productId,
                quantity: it.quantity,
                note: it.note,
                toppingIds: extractToppingIds(it)
            }));

            const dine = checkout.fulfillmentMode === 'DINE_IN';
            const ctx = window.MilkTeaContext;
            if (dine && !ctx.tableId) throw new Error('Vui lòng quét QR bàn hợp lệ');
            const body = {items,shopId:checkout.shopId,paymentMethod:checkout.paymentMethod || 'CASH',voucherCode:checkout.voucherCode,expectedTotal:checkout.expectedTotal};
            if (dine) Object.assign(body,{tableId:ctx.tableId,expectedSessionId:ctx.sessionId,qrCode:ctx.qrCode,tableToken:ctx.tableToken});
            const {tableToken,expectedSessionId,...logicalOrder} = body;
            const signature = JSON.stringify(logicalOrder);
            let attempt;
            try { attempt = JSON.parse(sessionStorage.getItem('uteTea.checkoutAttempt') || 'null'); } catch (_) {}
            if (!attempt || attempt.signature !== signature) {
                attempt = {signature,key:crypto.randomUUID()};
                sessionStorage.setItem('uteTea.checkoutAttempt',JSON.stringify(attempt));
            }
            const order = await MilkTeaApi.post(dine ? '/api/v1/orders/table' : '/api/v1/orders/counter',body,{'Idempotency-Key':attempt.key});
            sessionStorage.removeItem('uteTea.checkoutAttempt');
            MilkTeaApi.showToast('Đặt đơn thành công! Đang chuyển hướng...', 'success');

            // Cập nhật context phiên nếu là đơn đầu tiên mở phiên
            if (order.sessionId) {
                window.MilkTeaContext.sessionId = order.sessionId;
            }
            if (order.guestTableToken) {
                window.MilkTeaContext.tableToken = order.guestTableToken;
            }
            if (order.guestCounterToken) {
                window.MilkTeaContext.counterToken = order.guestCounterToken;
                sessionStorage.setItem('milktea.counterToken', order.guestCounterToken);
            }

            // Xóa giỏ hàng chỉ khi tạo đơn thành công
            try {
                await clearLocalCart();
            } catch (cartError) {
                MilkTeaApi.showToast('Đơn #' + order.orderId + ' đã được tạo; giỏ hàng đã thay đổi ở nơi khác. Vui lòng kiểm tra trước khi đặt thêm.', 'warning');
            }
            serverCart = [];
            updateCartBadge();

            // Mỗi lần đặt chỉ được xem đúng đơn vừa tạo bằng per-order token.
            // Không gửi QR/token ở URL và không chuyển khách ẩn danh tới lịch sử bàn.
            sessionStorage.setItem('uteTea.recentOrderId', String(order.orderId));
            if (order.guestCounterToken) {
                sessionStorage.setItem('uteTea.recentOrderToken', order.guestCounterToken);
            } else {
                sessionStorage.removeItem('uteTea.recentOrderToken');
            }
            setTimeout(() => {
                window.location.href = '/order-confirmation?order=' + encodeURIComponent(order.orderId);
            }, 800);
            return order;

        } catch (e) {
            // Giữ giỏ hàng và hiển thị lỗi, không xóa giỏ hay chuyển trang
            MilkTeaApi.showToast(e.message || 'Không thể tạo đơn hàng', 'error');
            if (btn) btn.disabled = false;
            throw e;
        }
    }

    return {
        initContext,
        addToCart,
        updateQuantity,
        removeItem,
        replaceItem,
        clearCart: async()=>{await clearLocalCart();updateCartBadge();},
        getCartVersion: ()=>accountCartVersion,
        submitOrder,
        loadServerCart,
        getLocalCart: () => localCart,
        getServerCart: () => serverCart
    };
})();
