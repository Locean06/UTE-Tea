// Static UI regression checks for the shared Admin branches page.
// Requires only Node built-ins; no credentials, external services or browser.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');

const base = 'src/main/resources/';
const html = fs.readFileSync(base + 'templates/admin/branches.html', 'utf8');
const catalog = fs.readFileSync(base + 'templates/admin/catalog.html', 'utf8');
const promotions = fs.readFileSync(base + 'templates/admin/promotions.html', 'utf8');
const css = fs.readFileSync(base + 'static/css/admin-branches.css', 'utf8');
const js = fs.readFileSync(base + 'static/js/admin/takeaway-management.js', 'utf8');

assert.match(html, /class="ute-page-banner"/, 'Must use the shared Admin header');
assert.match(html, /class="[^"]*admin-sidebar"/, 'Must use shared Admin sidebar');
assert.match(html, /class="admin-nav-link active" aria-current="page"/);
assert.match(html, /css\/admin-branches\.css/, 'Missing branch-scoped stylesheet');
assert.match(html, /css\/takeaway-admin\.css/, 'Must retain controls for CRUD');
assert.match(html, /Thực đơn và tồn bán dùng chung/);
assert.ok(!html.includes('ta-layout'), 'Old separate branch layout is still present');

const links = str => [...str.matchAll(/(?:th:href="@\{)(\/admin\/[^}]+)\}/g)].map(x => x[1]);
const menuLinks = new Set(links(catalog));
const branchLinks = new Set(links(html));
for (const href of menuLinks) {
  assert.ok(branchLinks.has(href), 'Missing standard Admin navigation: ' + href);
}

for (const id of [
  'taOverlay', 'taAlert', 'branchRows', 'branchTotalCount',
  'branchActiveCount', 'branchInactiveCount', 'branchResultCount',
  'branchFilter', 'branchStatusFilter', 'branchReload', 'branchNew',
  'branchEditor', 'branchForm', 'branchFormFeedback',
  'branchClose', 'branchCancel', 'branchSave',
  'branchName', 'branchAddress', 'branchArea', 'branchLat', 'branchLong', 'branchId'
]) {
  assert.equal((html.match(new RegExp('id="' + id + '"', 'g')) || []).length, 1,
    'Missing/duplicate branch control: ' + id);
}
for (const path of ['/api/v1/market/shops', '/api/v1/admin/takeaway/branches/']) {
  assert.ok(js.includes(path), 'Branch CRUD endpoint disappeared: ' + path);
}
for (const name of ['branchEdit(', 'renderBranches(', 'reloadBranches()', 'initBranches()']) {
  assert.ok(js.includes(name), 'Branch JS handler missing: ' + name);
}
assert.match(js, /formFeedback\.classList\.remove\('d-none'\)/);
assert.match(html, /id="branchEditor"[^>]*role="dialog"/);
assert.match(html, /id="branchForm"[^>]*class="ta-drawer-form"/);
assert.match(css, /\.admin-branches-page \.branches-editor-modal\.is-open/);
assert.ok(css.includes('.admin-branches-page'), 'CSS not scoped to branch page');
assert.ok(!promotions.includes('admin-branches.css'), 'Shared promo styles unexpectedly changed');

const markup = html.split('<th:block th:fragment="scripts">')[0];
assert.equal((markup.match(/<div\b/g)||[]).length, (markup.match(/<\/div>/g)||[]).length, 'Unbalanced div elements');
assert.equal((markup.match(/<aside\b/g)||[]).length, (markup.match(/<\/aside>/g)||[]).length, 'Unbalanced aside elements');
assert.equal((markup.match(/<form\b/g)||[]).length, (markup.match(/<\/form>/g)||[]).length, 'Unbalanced forms');
console.log('Branch Admin UI consistency and CRUD hooks: OK');
