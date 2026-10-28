/* UTE Tea — redesigned SHIPPER workspace. API and server-side authorization unchanged. */
(() => {
  'use strict';
  const byId = id => document.getElementById(id);
  const statuses = {
    CONFIRMED: {text: 'Chờ nhận giao', icon: 'bi-clock-history'},
    SHIPPING: {text: 'Đang giao', icon: 'bi-bicycle'},
    DELIVERED: {text: 'Giao thành công', icon: 'bi-check-circle'},
    DELIVERY_FAILED: {text: 'Giao thất bại', icon: 'bi-exclamation-circle'}
  };
  let assigned = [];
  let loading = false;
  let reloadQueued = false;
  let activeFilter = 'active';
  let hasLoaded = false;

  function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined && text !== null) node.textContent = String(text);
    if (className) node.className = className;
    return node;
  }
  const currency = amount => MilkTeaApi.formatVND(amount || 0);
  const set = (id, value) => { byId(id).textContent = value; };
  const safeNumber = phone => String(phone || '').replace(/[^+0-9]/g, '');
  function formatDate(date) {
    const value = date && new Date(date);
    if (!value || Number.isNaN(value.getTime())) return 'Chưa có thời gian';
    return new Intl.DateTimeFormat('vi-VN', {day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'}).format(value);
  }
  function inform(message, error = false) {
    const notice = byId('shipperNotice');
    notice.className = 'shipper-notice shipper-notice--' + (error ? 'error' : 'success');
    notice.textContent = message;
  }
  function renderStats(statistics) {
    const s = statistics || {};
    set('statWaiting', s.waiting ?? 0);
    set('statShipping', s.shipping ?? 0);
    set('statDelivered', s.delivered ?? 0);
    set('statAssigned', s.assigned ?? 0);
    set('statFailed', s.failed ?? 0);
    set('resultDelivered', s.delivered ?? 0);
    set('resultCOD', currency(s.codCollected));
    const rate = Math.min(100, Math.max(0, Number(s.successRate) || 0));
    set('shipperRate', rate + '%');
    byId('shipperProgressFill').style.width = rate + '%';
    byId('shipperScore').setAttribute('aria-valuenow', String(rate));
  }
  function matchesTab(order) {
    if (activeFilter === 'active') return order.status === 'CONFIRMED' || order.status === 'SHIPPING';
    if (activeFilter === 'done') return order.status === 'DELIVERED' || order.status === 'DELIVERY_FAILED';
    return true;
  }
  function updateTabs() {
    set('tabActiveCount', assigned.filter(o => o.status === 'CONFIRMED' || o.status === 'SHIPPING').length);
    set('tabDoneCount', assigned.filter(o => o.status === 'DELIVERED' || o.status === 'DELIVERY_FAILED').length);
    set('tabAllCount', assigned.length);
    document.querySelectorAll('[data-shipper-filter]').forEach(tab => {
      const selected = tab.dataset.shipperFilter === activeFilter;
      tab.classList.toggle('is-active', selected);
      tab.setAttribute('aria-pressed', String(selected));
    });
  }
  async function transition(order, status) {
    const action = status === 'SHIPPING' ? 'bắt đầu giao' : status === 'DELIVERED' ? 'xác nhận giao thành công' : 'báo giao thất bại';
    if (!window.confirm('Bạn có chắc muốn ' + action + ' đơn #' + order.id + '?')) return;
    // API checks assignment, permission, version and valid workflow transitions.
    try {
      await MilkTeaApi.put('/api/v1/shipper/orders/' + encodeURIComponent(order.id) + '/status', {
        status, version: order.version, reference: null
      });
      await load();
      inform('Đã cập nhật đơn #' + order.id + ' thành công.');
    } catch (err) {
      inform((err.message || 'Không cập nhật được đơn hàng') + '. Hãy làm mới rồi thử lại.', true);
    }
  }
  function button(text, icon, status, order, modifier = '') {
    const btn = el('button', null, 'shipper-btn' + (modifier ? ' shipper-btn--' + modifier : ''));
    btn.type = 'button';
    btn.append(el('i', null, 'bi ' + icon), document.createTextNode(text));
    btn.addEventListener('click', async () => {
      btn.disabled = true;
      try { await transition(order, status); } finally { btn.disabled = false; }
    });
    return btn;
  }
  function detailFact(label, value, amount = false) {
    const fact = el('div', null, 'shipper-fact' + (amount ? ' shipper-fact--amount' : ''));
    fact.append(el('span', label), el('strong', value));
    return fact;
  }
  function card(order) {
    const known = Object.prototype.hasOwnProperty.call(statuses, order.status);
    const meta = known ? statuses[order.status] : {text: order.status || 'Khác', icon: 'bi-info-circle'};
    const article = el('article', null, 'shipper-order shipper-order--' + (known ? order.status : 'OTHER'));

    const head = el('div', null, 'shipper-order-top');
    const identity = el('div');
    identity.append(el('div', 'Đơn #' + order.id, 'shipper-order-id'), el('div', formatDate(order.createdAt), 'shipper-order-meta'));
    const badge = el('span', null, 'shipper-status shipper-status--' + (known ? order.status : 'OTHER'));
    badge.append(el('i', null, 'bi ' + meta.icon), document.createTextNode(meta.text));
    head.append(identity, badge);

    const main = el('div', null, 'shipper-order-main');
    const receiver = el('div');
    receiver.append(el('h3', order.recipient || 'Khách hàng', 'shipper-order-recipient'));
    const address = el('div', null, 'shipper-address-line');
    address.append(el('i', null, 'bi bi-geo-alt'), el('span', order.addressLine || 'Chưa có địa chỉ'));
    receiver.append(address);
    if (order.phone) {
      const phone = el('a', null, 'shipper-order-phone');
      phone.append(el('i', null, 'bi bi-telephone'), document.createTextNode(order.phone));
      const tel = safeNumber(order.phone);
      if (tel) phone.href = 'tel:' + tel;
      receiver.append(phone);
    }
    const facts = el('div', null, 'shipper-order-facts');
    facts.append(detailFact('Lấy hàng tại', order.shopName || 'UTE Tea'));
    const payMethod = order.paymentMethod === 'COD' ? 'COD · Thu khi giao'
      : order.paid ? 'Trực tuyến · Đã trả' : 'Trực tuyến · Chờ thanh toán';
    facts.append(detailFact('Thanh toán', payMethod), detailFact('Giá trị đơn', currency(order.totalAmount), true));
    main.append(receiver, facts);

    const items = order.items || [];
    const quantity = items.reduce((total, item) => total + (Number(item.quantity) || 0), 0);
    const detail = el('details', null, 'shipper-order-detail');
    const summary = el('summary');
    summary.append(el('i', null, 'bi bi-chevron-down'), document.createTextNode('Xem món trong đơn · ' + quantity + ' món'));
    const itemList = el('ul', null, 'shipper-items');
    for (const item of items) {
      itemList.append(el('li', (item.quantity || 0) + ' × ' + (item.name || 'Sản phẩm')
        + (item.size ? ' (' + item.size + ')' : '') + (item.note ? ' · ' + item.note : '')));
    }
    if (!items.length) itemList.append(el('li', 'Chưa có chi tiết món'));
    detail.append(summary, itemList);

    const footer = el('div', null, 'shipper-order-footer');
    const hint = el('div', null, 'shipper-order-footnote');
    const actions = el('div', null, 'shipper-order-actions');
    if (order.status === 'CONFIRMED') {
      if (order.preparationStatus === 'READY' && (order.paymentMethod === 'COD' || order.paid)) {
        hint.append(el('i', null, 'bi bi-check-circle'), document.createTextNode('Bếp đã hoàn tất · Xác nhận đã nhận hàng từ quán để bắt đầu giao'));
        actions.append(button('Đã lấy hàng · Bắt đầu giao', 'bi-arrow-right', 'SHIPPING', order));
      } else {
        hint.append(el('i', null, 'bi bi-exclamation-circle'), document.createTextNode(order.preparationStatus !== 'READY' ? 'Đang chờ bếp pha chế xong' : 'Chờ khách thanh toán trực tuyến'));
      }
    } else if (order.status === 'SHIPPING') {
      hint.append(el('i', null, 'bi bi-truck'), document.createTextNode('Đang trên đường giao'));
      actions.append(button('Giao thất bại', 'bi-x-circle', 'DELIVERY_FAILED', order, 'soft'));
      actions.append(button('Giao thành công', 'bi-check2', 'DELIVERED', order, 'success'));
    } else if (order.status === 'DELIVERED') {
      hint.append(el('i', null, 'bi bi-check-circle'), document.createTextNode('Đã hoàn thành giao hàng'));
    } else if (order.status === 'DELIVERY_FAILED') {
      hint.append(el('i', null, 'bi bi-info-circle'), document.createTextNode('Liên hệ cửa hàng nếu cần hỗ trợ'));
    } else {
      hint.append(el('i', null, 'bi bi-info-circle'), document.createTextNode('Đang xử lý tại cửa hàng'));
    }
    footer.append(hint);
    if (actions.childNodes.length) footer.append(actions);
    article.append(head, main, detail, footer);
    return article;
  }
  function render() {
    updateTabs();
    const query = byId('shipperSearch').value.trim().toLocaleLowerCase('vi-VN');
    const result = assigned.filter(order => matchesTab(order) && (!query || [
      order.id, order.recipient, order.phone, order.shopName, order.addressLine
    ].some(value => String(value ?? '').toLocaleLowerCase('vi-VN').includes(query))));
    if (activeFilter === 'active') {
      result.sort((a, b) => (a.status === 'SHIPPING' ? 0 : 1) - (b.status === 'SHIPPING' ? 0 : 1)
        || (Number(b.id) || 0) - (Number(a.id) || 0));
    }
    set('shipperFilteredCount', result.length);
    const list = byId('shipperOrdersList');
    list.replaceChildren(...result.map(card));
    const empty = byId('shipperEmpty');
    empty.classList.toggle('d-none', result.length > 0 || !hasLoaded);
    if (!result.length && hasLoaded) {
      const searched = !!query;
      set('shipperEmptyTitle', searched ? 'Không tìm thấy đơn' : activeFilter === 'active' ? 'Không có đơn cần xử lý' : 'Chưa có đơn phù hợp');
      set('shipperEmptyText', searched ? 'Thử tìm bằng mã đơn, tên khách hàng hoặc số điện thoại khác.'
        : activeFilter === 'active' ? 'Bạn đã xử lý hết các đơn hiện tại. Đơn mới sẽ xuất hiện khi được phân công.'
          : activeFilter === 'done' ? 'Các đơn đã giao xong hoặc thất bại sẽ xuất hiện tại đây.'
            : 'Chưa có đơn nào được phân công cho bạn.');
    }
  }
  async function load() {
    if (loading) { reloadQueued = true; return; }
    loading = true;
    byId('shipperRefresh').disabled = true;
    try {
      const data = await MilkTeaApi.get('/api/v1/shipper/workspace');
      assigned = Array.isArray(data.orders) ? data.orders : [];
      hasLoaded = true;
      renderStats(data.statistics);
      render();
      set('shipperLastUpdated', 'Cập nhật lúc ' + new Intl.DateTimeFormat('vi-VN', {hour: '2-digit', minute: '2-digit'}).format(new Date()));
    } catch (err) {
      if (!hasLoaded) { assigned = []; hasLoaded = true; render(); }
      inform('Không tải được đơn được phân công: ' + (err.message || 'Lỗi kết nối'), true);
      set('shipperLastUpdated', 'Chưa đồng bộ');
    } finally {
      loading = false;
      byId('shipperRefresh').disabled = false;
      if (reloadQueued) { reloadQueued = false; void load(); }
    }
  }
  byId('shipperSearch').addEventListener('input', render);
  document.querySelectorAll('[data-shipper-filter]').forEach(tab => tab.addEventListener('click', () => {
    activeFilter = tab.dataset.shipperFilter;
    render();
  }));
  byId('shipperRefresh').addEventListener('click', load);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) void load(); });
  void load();
  window.setInterval(() => {
    if (document.visibilityState === 'visible' && !loading) void load();
  }, 45000);
})();
