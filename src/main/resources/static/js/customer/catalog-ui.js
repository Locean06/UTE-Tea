/** Accessible product-card renderer for API pagination and filters. */
const UTETeaCatalog = (() => {
    const fallback = '/images/ute-tea-product-fallback.svg';
    const byId = id => document.getElementById(id);

    function el(tag, className, text) {
        const item = document.createElement(tag);
        if (className) item.className = className;
        if (text !== undefined) item.textContent = String(text ?? '');
        return item;
    }

    /** One visual card per drink, never one card per size variant. */
    function normalize(value) {
        return String(value ?? '')
            .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim();
    }

    function displayImage(product) {
        const url = String(product.imageUrl || '').trim();
        if (url && !url.endsWith('/ute-tea-product-fallback.svg') && !url.endsWith('/MilkTea.png')) return url;
        const name = normalize(product.name) + ' ' + normalize(product.categoryName);
        if (/topping|tran chau|pudding|thach/.test(name)) return '/images/products/ute-topping.webp';
        if (/matcha|tra xanh/.test(name)) return '/images/products/ute-matcha.webp';
        if (/ca phe|coffee|bac xiu|espresso|latte|muoi/.test(name)) return '/images/products/ute-coffee.webp';
        if (/xoai|chanh day|dao|cam|chanh|vai|dau|trai cay|nhiet doi|fruit/.test(name)) return '/images/products/ute-fruit-tea.webp';
        if (/tra sua|milk tea|sua|oolong|hong tra|sen|lai/.test(name)) return '/images/products/ute-milk-tea.webp';
        return fallback;
    }

    function groupProducts(products) {
        const grouped = new Map();
        for (const product of products || []) {
            if (!product || product.id == null) continue;
            const key = [String(product.shopId ?? 'public'), String(product.categoryId ?? '0'), normalize(product.name)].join('|');
            if (!grouped.has(key)) grouped.set(key, []);
            grouped.get(key).push(product);
        }
        return Array.from(grouped.values(), variants => {
            // Keep an ID from an actual size variant so opening options still works.
            const preferred = variants.find(p => normalize(p.size) === 'm') || variants[0];
            const prices = variants.map(p => Number(p.price)).filter(Number.isFinite);
            const minPrice = prices.length ? Math.min(...prices) : preferred.price;
            const distinctSizes = new Set(variants.map(p => normalize(p.size)).filter(Boolean));
            const image = variants.find(p => typeof p.imageUrl === 'string' && p.imageUrl.trim() && !p.imageUrl.endsWith('/ute-tea-product-fallback.svg'))?.imageUrl;
            return {
                ...preferred,
                price: minPrice,
                size: distinctSizes.size > 1 ? '' : preferred.size,
                imageUrl: image || displayImage(preferred),
                _hasSizeVariants: distinctSizes.size > 1 || variants.some(p => p._hasSizeVariants)
            };
        });
    }

    /** Also consolidate the Thymeleaf fallback before the API request completes. */
    function consolidateInitialCards() {
        const grid = byId('productsGrid');
        if (!grid) return;
        const seen = new Map();
        grid.querySelectorAll('.product-item').forEach(item => {
            const key = [item.dataset.shopId || 'public', item.dataset.category || '0', normalize(item.dataset.name || item.querySelector('h5')?.textContent)].join('|');
            const price = Number(item.dataset.price);
            if (!seen.has(key)) {
                seen.set(key, { item, prices: Number.isFinite(price) ? [price] : [], sizes: new Set([normalize(item.dataset.size)].filter(Boolean)) });
                // Size selection belongs inside the popup, not on catalog cards.
                item.querySelector('.product-img-wrapper .badge')?.remove();
                return;
            }
            const primary = seen.get(key);
            if (Number.isFinite(price)) primary.prices.push(price);
            if (item.dataset.size) primary.sizes.add(normalize(item.dataset.size));
            item.remove();
        });
        seen.forEach(({ item, prices, sizes }) => {
            const priceEl = item.querySelector('.product-price');
            if (priceEl && sizes.size > 1 && prices.length) {
                priceEl.textContent = 'Từ ' + MilkTeaApi.formatVND(Math.min(...prices));
            }
        });
    }

    function hasText(value) {
        return typeof value === 'string' && value.trim().length > 0;
    }

    function productCard(product) {
        const col = el('div', 'col product-item');
        col.dataset.id = String(product.id);
        col.dataset.category = String(product.categoryId ?? '');
        col.dataset.categoryName = product.categoryName || '';
        col.dataset.shopId = String(product.shopId ?? '');

        const card = el('article', 'product-card');

        const imageWrap = el('div', 'product-img-wrapper');
        const image = el('img', 'product-img');
        image.src = displayImage(product);
        image.alt = 'Ảnh ' + (product.name || 'món uống');
        image.loading = 'lazy';
        image.decoding = 'async';
        image.addEventListener('error', () => {
            if (image.src !== new URL(fallback, location.origin).href) image.src = fallback;
        });
        imageWrap.append(image);

        const body = el('div', 'p-3 d-flex flex-column flex-grow-1');
        body.append(el('h5', 'fw-bold mb-1 text-truncate', product.name || 'Món uống'));

        const fav = el('button', 'btn btn-sm product-favorite-btn mb-2 align-self-start');
        fav.innerHTML = '<i class="bi bi-heart"></i> Yêu thích';
        fav.type = 'button';
        fav.setAttribute('aria-label', 'Yêu thích ' + (product.name || 'sản phẩm'));
        fav.dataset.favorite = 'false';
        fav.addEventListener('click', () => favoriteProduct(product.id, fav));
        body.append(fav);

        const action = el('div', 'd-flex justify-content-between align-items-center mt-auto pt-2 border-top');
        const pricePrefix = product._hasSizeVariants || !hasText(product.size) ? 'Từ ' : '';
        action.append(el('span', 'product-price', pricePrefix + MilkTeaApi.formatVND(product.price)));

        const add = el('button', 'btn btn-sm btn-primary-custom rounded-pill px-3', '＋ Thêm');
        add.type = 'button';
        add.setAttribute('aria-label', 'Tùy chọn và thêm ' + (product.name || 'sản phẩm') + ' vào giỏ');
        add.addEventListener('click', () => openCustomizeModal(product.id));
        action.append(add);

        body.append(action);
        card.append(imageWrap, body);
        col.append(card);
        return col;
    }

    function render(page, requestedPage) {
        const grid = byId('productsGrid');
        if (!grid || !page || !Array.isArray(page.items)) throw new Error('Dữ liệu thực đơn trả về không hợp lệ');
        const uniqueProducts = groupProducts(page.items);
        const nodes = uniqueProducts.map(productCard);
        grid.replaceChildren(...nodes);
        const totalFromApi = Number(page.total) || 0;
        // New backend counts unique drinks. Older backend counts size variants,
        // so its result can report 2 items when we visibly have only 1.
        const total = totalFromApi === page.items.length && uniqueProducts.length < page.items.length
            ? uniqueProducts.length : totalFromApi;
        const size = Number(page.size) || 20;
        const pageIndex = Math.max(0, Number(page.page ?? requestedPage) || 0);
        byId('uteCatalogEmpty')?.classList.toggle('d-none', nodes.length !== 0);
        const label = byId('catalogPageLabel');
        if (label) label.textContent = 'Trang ' + (pageIndex + 1) + ' / ' + Math.max(1, Math.ceil(total / size)) + ' · ' + total + ' món';
        const prev = byId('catalogPrevious');
        const next = byId('catalogNext');
        if (prev) prev.disabled = pageIndex <= 0;
        if (next) next.disabled = (pageIndex + 1) * size >= total;
        byId('uteCatalogError')?.remove();
    }

    function showError(message) {
        const grid = byId('productsGrid');
        if (!grid) return;
        let banner = byId('uteCatalogError');
        if (!banner) {
            banner = el('div', 'alert alert-warning my-3');
            banner.id = 'uteCatalogError';
            banner.setAttribute('role', 'alert');
            grid.before(banner);
        }
        banner.textContent = 'Không thể cập nhật thực đơn: ' + message + '. Vui lòng thử lại sau.';
    }

    function setLoading(loading) {
        const grid = byId('productsGrid');
        if (grid) grid.setAttribute('aria-busy', String(Boolean(loading)));
    }

    return { render, productCard, showError, setLoading, groupProducts, consolidateInitialCards };
})();
