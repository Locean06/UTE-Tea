/* Dedicated UTE Tea Take Away admin pages. No mock data: backed by existing market REST APIs. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const isBranchPage = !!$('branchRows');
  const isPromoPage = !!$('promoRows');
  if (!isBranchPage && !isPromoPage) return;

  const shops = [];
  const vouchers = [];
  let lastFocus = null;
  let previousOverflow = '';
  const fmt = amount => MilkTeaApi.formatVND(Number(amount) || 0);
  const human = amount => new Intl.NumberFormat('vi-VN').format(Number(amount) || 0);
  const node = (tag, text, className) => {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = String(text ?? '');
    if (className) element.className = className;
    return element;
  };
  function setText(id, value) { if ($(id)) $(id).textContent = String(value); }
  function cell(row, value, cls) { const c = node('td', value, cls); row.append(c); return c; }
  function clear(parent) { parent.replaceChildren(); }
  function alertMessage(message, error = false) {
    const box = $('taAlert');
    box.className = 'ta-alert' + (error ? ' error' : '');
    clear(box);
    box.append(node('i', undefined, 'bi ' + (error ? 'bi-exclamation-circle' : 'bi-check-circle')),
      node('span', message || (error ? 'Không tải được dữ liệu.' : 'Đã cập nhật.')));
    box.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }
  function handleError(error) {
    const suffix = error && error.traceId ? ' (Mã lỗi: ' + error.traceId + ')' : '';
    alertMessage((error && error.message ? error.message : 'Không thể kết nối máy chủ.') + suffix, true);
  }
  function busy(button, fn) {
    return async () => {
      if (button.disabled) return;
      button.disabled = true;
      button.setAttribute('aria-busy', 'true');
      try { await fn(); } catch (e) { handleError(e); }
      finally { button.disabled = false; button.removeAttribute('aria-busy'); }
    };
  }
  function tableEmpty(id, columns, text) {
    const tr = node('tr');
    const td = cell(tr, undefined, 'ta-empty'); td.colSpan = columns;
    td.append(node('i', undefined, 'bi bi-inbox'), node('p', text));
    $(id).append(tr);
  }
  function action(text, icon, variant, fn) {
    const b = node('button', undefined, 'btn ' + (variant || ''));
    b.type = 'button'; b.append(node('i', undefined, 'bi ' + icon), node('span', text));
    b.addEventListener('click', busy(b, fn));
    return b;
  }
  function iconStatus(label, variant) {
    return node('span', label, 'ta-status ' + variant);
  }

  function openDrawer(editorId, focusId) {
    const drawer = $(editorId);
    lastFocus = document.activeElement;
    previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    drawer.classList.add('is-open');
    drawer.setAttribute('aria-hidden', 'false');
    $('taOverlay').classList.add('is-open');
    $('taOverlay').setAttribute('aria-hidden', 'false');
    setTimeout(() => $(focusId)?.focus(), 40);
  }
  function closeDrawer() {
    const drawer = document.querySelector('.ta-drawer.is-open');
    if (!drawer) return;
    drawer.classList.remove('is-open');
    drawer.setAttribute('aria-hidden', 'true');
    $('taOverlay').classList.remove('is-open');
    $('taOverlay').setAttribute('aria-hidden', 'true');
    document.body.style.overflow = previousOverflow;
    if (lastFocus && lastFocus.isConnected) lastFocus.focus();
  }
  $('taOverlay').addEventListener('click', closeDrawer);
  document.addEventListener('keydown', event => {
    const drawer = document.querySelector('.ta-drawer.is-open');
    if (!drawer) return;
    if (event.key === 'Escape') { event.preventDefault(); closeDrawer(); }
    if (event.key !== 'Tab') return;
    const focusable = [...drawer.querySelectorAll('button:not(:disabled), input:not(:disabled):not([type=hidden]), textarea:not(:disabled), select:not(:disabled), a[href]')]
      .filter(el => el.offsetWidth > 0 || el.offsetHeight > 0);
    if (!focusable.length) return;
    if (event.shiftKey && document.activeElement === focusable[0]) { event.preventDefault(); focusable[focusable.length - 1].focus(); }
    if (!event.shiftKey && document.activeElement === focusable[focusable.length - 1]) { event.preventDefault(); focusable[0].focus(); }
  });
  async function fetchShops() {
    const result = await MilkTeaApi.get('/api/v1/market/shops');
    if (!Array.isArray(result)) throw Error('Dữ liệu chi nhánh không hợp lệ.');
    shops.splice(0, shops.length, ...result);
    return shops;
  }

  // BRANCHES
  function branchReset() {
    $('branchForm').reset();
    $('branchId').value = '';
    setText('branchFormTitle', 'Thêm chi nhánh');
  }
  function branchEdit(s) {
    branchReset();
    $('branchId').value = s.id;
    $('branchName').value = s.name || '';
    $('branchAddress').value = s.address || '';
    $('branchArea').value = s.area || '';
    $('branchLat').value = s.latitude ?? '';
    $('branchLong').value = s.longitude ?? '';
    setText('branchFormTitle', 'Sửa chi nhánh');
    openDrawer('branchEditor', 'branchName');
  }
  function renderBranches() {
    const rows = $('branchRows'); clear(rows);
    const term = $('branchFilter').value.trim().toLocaleLowerCase('vi');
    const view = $('branchStatusFilter').value;
    const filtered = shops.filter(s => view === 'all' || (view === 'active' ? s.active === true : s.active !== true))
      .filter(s => [s.name, s.address, s.area].join(' ').toLocaleLowerCase('vi').includes(term));
    setText('branchTotalCount', human(shops.length));
    setText('branchActiveCount', human(shops.filter(s => s.active).length));
    setText('branchInactiveCount', human(shops.filter(s => !s.active).length));
    setText('branchResultCount', 'Hiển thị ' + filtered.length + ' / ' + shops.length + ' chi nhánh');
    if (!filtered.length) { tableEmpty('branchRows', 4, 'Chưa có chi nhánh phù hợp bộ lọc.'); return; }
    for (const s of filtered) {
      const row = node('tr');
      const nameCell = cell(row);
      const entity = node('div', undefined, 'ta-entity');
      entity.append(node('span', undefined, 'ta-entity-icon bi bi-shop-window'));
      const details = node('div', undefined, 'ta-entity-details');
      details.append(node('div', s.name || 'Chi nhánh chưa đặt tên', 'ta-primary-cell'), node('div', 'ID #' + s.id, 'ta-sub-cell'));
      entity.append(details); nameCell.append(entity);
      const address = cell(row);
      address.append(node('div', s.address || 'Chưa có địa chỉ'));
      address.append(node('div', s.area || 'Chưa khai báo khu vực', 'ta-sub-cell'));
      cell(row).append(iconStatus(s.active ? 'Đang hoạt động' : 'Đã ngừng', s.active ? 'live' : 'disabled'));
      const commands = cell(row); commands.classList.add('text-end');
      const group = node('div', undefined, 'ta-action-group');
      group.append(action('Sửa', 'bi-pencil-square', '', async () => branchEdit(s)));
      if (s.active) {
        group.append(action('Ngừng', 'bi-pause-circle', 'ta-danger', async () => {
          if (!window.confirm('Ngừng hoạt động chi nhánh "' + s.name + '"? Đơn hàng đã tạo vẫn được giữ lại.')) return;
          await MilkTeaApi.delete('/api/v1/admin/takeaway/branches/' + s.id);
          await reloadBranches(); alertMessage('Đã ngừng hoạt động chi nhánh ' + s.name + '.');
        }));
      } else {
        group.append(action('Khôi phục', 'bi-arrow-counterclockwise', 'ta-restore', async () => {
          await MilkTeaApi.put('/api/v1/market/shops/' + s.id + '/config', {
            active: true, commissionPercent: s.commissionPercent ?? 0,
            managerCommissionEnabled: !!s.managerCommissionEnabled
          });
          await reloadBranches(); alertMessage('Đã khôi phục chi nhánh ' + s.name + '.');
        }));
      }
      commands.append(group); rows.append(row);
    }
  }
  async function reloadBranches() { await fetchShops(); renderBranches(); }
  function initBranches() {
    $('branchNew').addEventListener('click', () => { branchReset(); openDrawer('branchEditor', 'branchName'); });
    $('branchClose').addEventListener('click', closeDrawer);
    $('branchCancel').addEventListener('click', closeDrawer);
    $('branchFilter').addEventListener('input', renderBranches);
    $('branchStatusFilter').addEventListener('change', renderBranches);
    $('branchReload').addEventListener('click', busy($('branchReload'), reloadBranches));
    $('branchForm').addEventListener('submit', event => {
      event.preventDefault();
      busy($('branchSave'), async () => {
        const id = $('branchId').value;
        const lat = $('branchLat').value.trim(), lng = $('branchLong').value.trim();
        if (Boolean(lat) !== Boolean(lng)) throw Error('Cần nhập đủ vĩ độ và kinh độ, hoặc để trống cả hai.');
        const payload = {
          name: $('branchName').value.trim(), address: $('branchAddress').value.trim(),
          area: $('branchArea').value.trim() || null,
          latitude: lat ? Number(lat) : null, longitude: lng ? Number(lng) : null
        };
        if (!payload.name || !payload.address) throw Error('Vui lòng điền tên và địa chỉ chi nhánh.');
        await MilkTeaApi[id ? 'put' : 'post']('/api/v1/market/shops' + (id ? '/' + id : ''), payload);
        await reloadBranches(); closeDrawer(); branchReset();
        alertMessage(id ? 'Đã cập nhật thông tin chi nhánh.' : 'Đã thêm chi nhánh. Hãy cấu hình menu và tồn kho để nhận đơn giao hàng.');
      })();
    });
    reloadBranches().catch(handleError);
  }

  // PROMOTIONS
  function voucherStatus(v) {
    const now = Date.now();
    if (!v.active) return { key: 'inactive', label: 'Đã tắt', cls: 'disabled' };
    if (v.endsAt && new Date(v.endsAt).getTime() <= now) return { key: 'expired', label: 'Đã hết hạn', cls: 'expired' };
    if (v.usageLimit != null && Number(v.usedCount) >= Number(v.usageLimit)) return { key: 'expired', label: 'Hết lượt', cls: 'expired' };
    if (v.startsAt && new Date(v.startsAt).getTime() > now) return { key: 'upcoming', label: 'Sắp diễn ra', cls: 'upcoming' };
    return { key: 'active', label: 'Đang hiệu lực', cls: 'live' };
  }
  const pdate = value => {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  };
  const localInputValue = value => {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return shifted.toISOString().slice(0, 16);
  };
  function promoReset() {
    $('promoForm').reset();
    $('promoId').value = '';
    $('promoCode').readOnly = false;
    $('promoShop').disabled = false;
    $('promoActive').checked = true;
    $('promoStart').value = localInputValue(new Date());
    $('promoEnd').value = localInputValue(new Date(Date.now() + 30 * 86400000));
    setText('promoFormTitle', 'Tạo mã khuyến mãi');
    typeChanged();
  }
  function typeChanged() {
    const percentage = $('promoType').value === 'PERCENT';
    $('promoValue').step = percentage ? '0.01' : '1';
    if (percentage) $('promoValue').max = '100'; else $('promoValue').removeAttribute('max');
    setText('promoUnit', percentage ? '%' : '₫');
  }
  function promoEdit(v) {
    promoReset();
    $('promoId').value = v.id;
    $('promoCode').value = v.code;
    $('promoCode').readOnly = Number(v.usedCount) > 0;
    $('promoShop').value = v.shopId == null ? '' : String(v.shopId);
    $('promoShop').disabled = true; // Shop association is fixed by the existing update API.
    $('promoType').value = v.discountType || 'FIXED_AMOUNT';
    $('promoValue').value = v.discountType === 'PERCENT' ? (v.discountPercent ?? 0) : (v.discountAmount ?? 0);
    $('promoMin').value = v.minimumAmount ?? 0;
    $('promoLimit').value = v.usageLimit ?? 1;
    $('promoPerAccount').value = v.perAccountLimit ?? 1;
    $('promoStart').value = localInputValue(v.startsAt);
    $('promoEnd').value = localInputValue(v.endsAt);
    $('promoActive').checked = !!v.active;
    setText('promoFormTitle', 'Chỉnh sửa mã ' + v.code);
    typeChanged(); openDrawer('promoEditor', 'promoCode');
  }
  function shopName(id) { return id == null ? 'Toàn hệ thống' : (shops.find(s => String(s.id) === String(id))?.name || ('Chi nhánh #' + id)); }
  function fillShopOptions() {
    for (const id of ['promoShop', 'promoFilterShop']) {
      const select = $(id), previous = select.value;
      while (select.options.length > 1) select.remove(1);
      for (const shop of shops) {
        const opt = node('option', shop.name + (shop.active ? '' : ' (đã ngừng)'));
        opt.value = String(shop.id);
        if (id === 'promoShop' && !shop.active) opt.disabled = true;
        select.append(opt);
      }
      select.value = previous;
    }
  }
  function buildVoucher(v, active) {
    return {
      code: v.code, discountType: v.discountType,
      discountPercent: Number(v.discountPercent || 0), discountAmount: Number(v.discountAmount || 0),
      minimumAmount: Number(v.minimumAmount || 0), startsAt: v.startsAt, endsAt: v.endsAt,
      usageLimit: Number(v.usageLimit), perAccountLimit: Number(v.perAccountLimit), active
    };
  }
  function renderPromos() {
    const rows = $('promoRows'); clear(rows);
    const shopId = $('promoFilterShop').value;
    const filter = $('promoFilterStatus').value;
    const term = $('promoSearch').value.trim().toUpperCase();
    const filtered = vouchers.filter(v => (!shopId || String(v.shopId) === shopId) &&
      (filter === 'all' || voucherStatus(v).key === filter) && String(v.code || '').toUpperCase().includes(term));
    setText('promoTotal', human(vouchers.length));
    setText('promoActiveCount', human(vouchers.filter(v => voucherStatus(v).key === 'active').length));
    setText('promoScheduled', human(vouchers.filter(v => voucherStatus(v).key === 'upcoming').length));
    setText('promoUsedCount', human(vouchers.reduce((total, v) => total + Number(v.usedCount || 0), 0)));
    setText('promoResultCount', 'Hiển thị ' + filtered.length + ' / ' + vouchers.length + ' mã khuyến mãi');
    if (!filtered.length) { tableEmpty('promoRows', 7, 'Chưa có mã khuyến mãi phù hợp bộ lọc.'); return; }
    for (const v of filtered) {
      const row = node('tr');
      cell(row).append(node('span', v.code, 'ta-code'));
      const scopeCell = cell(row); scopeCell.append(node('div', shopName(v.shopId), 'ta-primary-cell'));
      const percent = v.discountType === 'PERCENT';
      cell(row).append(node('div', percent ? human(v.discountPercent) + '%' : fmt(v.discountAmount), 'ta-primary-cell'));
      const timeCell = cell(row); timeCell.append(node('div', pdate(v.startsAt) + ' → ' + pdate(v.endsAt)));
      const countCell = cell(row); countCell.append(node('div', human(v.usedCount) + ' / ' + human(v.usageLimit), 'ta-primary-cell'));
      const stat = voucherStatus(v); cell(row).append(iconStatus(stat.label, stat.cls));
      const commands = cell(row); commands.classList.add('text-end');
      const group = node('div', undefined, 'ta-action-group');
      group.append(action('Sửa', 'bi-pencil-square', '', async () => promoEdit(v)));
      if (v.active) {
        group.append(action('Tắt mã', 'bi-slash-circle', 'ta-danger', async () => {
          if (!window.confirm('Tắt mã khuyến mãi "' + v.code + '"?')) return;
          await MilkTeaApi.delete('/api/v1/market/vouchers/' + v.id);
          await reloadPromos(); alertMessage('Đã tắt mã ' + v.code + '.');
        }));
      } else {
        group.append(action('Bật mã', 'bi-check-circle', 'ta-restore', async () => {
          await MilkTeaApi.put('/api/v1/market/vouchers/' + v.id, buildVoucher(v, true));
          await reloadPromos(); alertMessage('Đã bật mã ' + v.code + '.');
        }));
      }
      commands.append(group); rows.append(row);
    }
  }
  async function reloadPromos() {
    const data = await MilkTeaApi.get('/api/v1/admin/takeaway/vouchers');
    if (!Array.isArray(data)) throw Error('Dữ liệu khuyến mãi không hợp lệ.');
    vouchers.splice(0, vouchers.length, ...data);
    renderPromos();
  }
  function initPromos() {
    $('promoNew').addEventListener('click', () => { promoReset(); openDrawer('promoEditor', 'promoCode'); });
    $('promoClose').addEventListener('click', closeDrawer);
    $('promoCancel').addEventListener('click', closeDrawer);
    $('promoType').addEventListener('change', typeChanged);
    for (const id of ['promoSearch', 'promoFilterShop', 'promoFilterStatus'])
      $(id).addEventListener(id === 'promoSearch' ? 'input' : 'change', renderPromos);
    $('promoReload').addEventListener('click', busy($('promoReload'), reloadPromos));
    $('promoForm').addEventListener('submit', event => {
      event.preventDefault();
      busy($('promoSave'), async () => {
        const id = $('promoId').value;
        const isPercent = $('promoType').value === 'PERCENT';
        const amount = Number($('promoValue').value);
        const shopId = $('promoShop').value;
        const startsAt = new Date($('promoStart').value), endsAt = new Date($('promoEnd').value);
        if (!Number.isFinite(amount) || amount <= 0 || (isPercent && amount > 100)) throw Error('Giá trị giảm không hợp lệ.');
        if (!Number.isFinite(startsAt.getTime()) || !Number.isFinite(endsAt.getTime()) || endsAt <= startsAt)
          throw Error('Ngày kết thúc phải sau ngày bắt đầu.');
        if (Number($('promoPerAccount').value) > Number($('promoLimit').value))
          throw Error('Lượt dùng mỗi khách không thể lớn hơn tổng lượt sử dụng.');
        const payload = {
          code: $('promoCode').value.trim().toUpperCase(), discountType: $('promoType').value,
          discountAmount: isPercent ? 0 : amount, discountPercent: isPercent ? amount : 0,
          minimumAmount: Number($('promoMin').value),
          startsAt: startsAt.toISOString(), endsAt: endsAt.toISOString(),
          usageLimit: Number($('promoLimit').value), perAccountLimit: Number($('promoPerAccount').value),
          active: $('promoActive').checked
        };
        const endpoint = '/api/v1/market/vouchers' + (id ? '/' + id : (shopId ? '?shopId=' + encodeURIComponent(shopId) : ''));
        await MilkTeaApi[id ? 'put' : 'post'](endpoint, payload);
        await reloadPromos(); closeDrawer(); promoReset();
        alertMessage(id ? 'Đã cập nhật mã khuyến mãi.' : 'Đã tạo mã khuyến mãi mới.');
      })();
    });
    (async () => { await fetchShops(); fillShopOptions(); promoReset(); await reloadPromos(); })().catch(handleError);
  }
  if (isBranchPage) initBranches(); else initPromos();
})();
