/** UTE Tea - Admin table operations (no QR screens). */
(function () {
  'use strict';
  const normalize = (value) => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim();
  let status = 'ALL';
  const cards = () => Array.from(document.querySelectorAll('#adminTablesCards .ute-table-item'));

  function apply() {
    const term = normalize(document.getElementById('adminTableSearch')?.value);
    let shown = 0;
    cards().forEach(card => {
      const match = (status === 'ALL' || card.dataset.status === status) && normalize(card.dataset.name).includes(term);
      card.classList.toggle('d-none', !match);
      if (match) shown++;
    });
    document.getElementById('uteTablesEmpty')?.classList.toggle('d-none', shown !== 0);
  }
  function count() {
    const all = cards();
    [['uteTableAll', 'ALL'], ['uteTableFree', 'TRONG'], ['uteTableBusy', 'CO_KHACH'], ['uteTableMaintenance', 'BAO_TRI']]
      .forEach(([id, state]) => {
        const el = document.getElementById(id);
        if (el) el.textContent = state === 'ALL' ? all.length : all.filter(x => x.dataset.status === state).length;
      });
  }
  function csv() {
    const displayed = cards().filter(card => !card.classList.contains('d-none'));
    const escape = s => { const raw = String(s ?? ''); const safe = /^[\s]*[=+@\-]/.test(raw) ? '\'' + raw : raw; return '"' + safe.replace(/"/g, '""') + '"'; };
    const labels = { TRONG: 'Trống', CO_KHACH: 'Có khách', BAO_TRI: 'Bảo trì' };
    const rows = [['Tên bàn', 'Trạng thái', 'Đang hoạt động']].concat(displayed.map(x => [
      x.dataset.name, labels[x.dataset.status] || x.dataset.status, x.dataset.active === 'true' ? 'Có' : 'Không'
    ]));
    const file = new Blob(['\ufeff' + rows.map(row => row.map(escape).join(',')).join('\r\n')], {type:'text/csv;charset=utf-8'});
    const url = URL.createObjectURL(file);
    const a = document.createElement('a');
    a.href = url; a.download = 'UTE_Tea_danh_sach_ban.csv'; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  function edit(button) {
    const form = document.getElementById('uteTableEditForm');
    if (!form || !button) return;
    form.dataset.id = button.dataset.id;
    form.dataset.status = button.dataset.status;
    document.getElementById('uteTableEditName').value = button.dataset.name || '';
    const active = document.getElementById('uteTableEditActive');
    active.checked = button.dataset.active === 'true';
    active.disabled = button.dataset.status === 'CO_KHACH';
    document.getElementById('uteTableEditWarning').classList.toggle('d-none', button.dataset.status !== 'CO_KHACH');
    bootstrap.Modal.getOrCreateInstance(document.getElementById('editTableModal')).show();
  }
  async function save(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector('[type=submit]');
    const name = document.getElementById('uteTableEditName').value.trim();
    if (!name) return;
    const activeInput = document.getElementById('uteTableEditActive');
    try {
      button.disabled = true;
      await MilkTeaApi.put(`/api/v1/admin/tables/${encodeURIComponent(form.dataset.id)}`, {
        name, active: activeInput.disabled ? true : activeInput.checked
      });
      MilkTeaApi.showToast('Đã cập nhật thông tin bàn.', 'success');
      window.location.reload();
    } catch (error) {
      button.disabled = false;
      MilkTeaApi.showToast(error.message || 'Không thể cập nhật bàn.', 'error');
    }
  }
  document.addEventListener('DOMContentLoaded', () => {
    if (!document.getElementById('adminTablesCards')) return;
    count(); apply();
    document.getElementById('adminTableSearch')?.addEventListener('input', apply);
    document.querySelectorAll('[data-table-filter]').forEach(btn => btn.addEventListener('click', () => {
      status = btn.dataset.tableFilter;
      document.querySelectorAll('[data-table-filter]').forEach(el => el.classList.toggle('active', el === btn));
      apply();
    }));
    document.getElementById('exportTablesCsv')?.addEventListener('click', csv);
    document.getElementById('uteTableEditForm')?.addEventListener('submit', save);
  });
  window.UteTableTools = { edit };
})();
