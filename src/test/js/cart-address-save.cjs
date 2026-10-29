// Regression test for the delivery address form on /cart.
// Uses only Node built-ins; no API calls, database or browser installation.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('src/main/resources/static/js/customer/delivery-checkout.js', 'utf8');
const template = fs.readFileSync('src/main/resources/templates/fragments/delivery-checkout.html', 'utf8');
assert.match(template, /id="checkoutAddressForm"/);
assert.match(template, /id="checkoutAddressFeedback"/);

async function scenario({ loggedIn = true, failSave = false, failCarriers = false } = {}) {
    const elements = new Map();
    const pageListeners = {};
    const calls = [];
    const addresses = [];
    function el(id) {
        if (elements.has(id)) return elements.get(id);
        const element = {
            id,
            value: '',
            disabled: false,
            textContent: '',
            className: '',
            open: false,
            hidden: false,
            checked: false,
            options: [],
            handlers: {},
            classList: { add() {}, remove() {}, toggle() {} },
            addEventListener(type, handler) { this.handlers[type] = handler; },
            append(...children) { this.options.push(...children); },
            replaceChildren(...children) { this.options = [...children]; this.value = ''; },
            reportValidity() { return true; },
            scrollIntoView() {},
        };
        elements.set(id, element);
        return element;
    }
    const form = el('checkoutAddressForm');
    form.values = {
        recipient: ' Nguyễn Văn A ',
        phone: '0901234567',
        addressLine: ' 12 Đường 3 ',
        area: ' Thủ Đức ',
        latitude: '',
        longitude: '',
    };
    form.elements = { namedItem: () => ({ checked: true }) };
    form.reset = function () { this.wasReset = true; };
    const saveButton = { disabled: false, textContent: 'Lưu địa chỉ' };
    const document = {
        body: { dataset: { accountId: loggedIn ? '123' : '' } },
        getElementById: el,
        createElement: tag => ({ tag, value: '', textContent: '', className: '' }),
        addEventListener(name, handler) { pageListeners[name] = handler; },
        dispatchEvent() {},
    };
    class FakeFormData {
        constructor(target) { this.target = target; }
        entries() { return Object.entries(this.target.values); }
        get() { return null; }
    }
    const api = {
        async get(url) {
            if (url === '/api/v1/public/market/shops') return [{ id: 2, name: 'Campus', active: true }];
            if (url === '/api/v1/public/market/carriers') {
                if (failCarriers) throw new Error('Carrier service unavailable');
                return [];
            }
            if (url === '/api/v1/account/addresses') return addresses.slice();
            throw new Error('Unexpected GET: ' + url);
        },
        async post(url, payload) {
            calls.push({ url, payload });
            if (failSave) throw new Error('Address API rejected');
            const saved = { ...payload, id: 42 };
            addresses.push(saved);
            return saved;
        },
        formatVND: x => String(x) + ' VND'
    };
    const context = {
        document, FormData: FakeFormData, MilkTeaApi: api,
        CustomerApp: { initContext: async () => {}, getLocalCart: () => [] },
        window: {}, location: { search: '' }, URLSearchParams, console,
        sessionStorage: { getItem: () => null, setItem() {}, removeItem() {} },
        CustomEvent: class {}, setTimeout,
    };
    vm.createContext(context);
    vm.runInContext(source, context, { filename: 'delivery-checkout.js' });
    assert.equal(typeof pageListeners.DOMContentLoaded, 'function');

    // The save listener must be installed before any async initialization.
    pageListeners.DOMContentLoaded();
    assert.equal(typeof form.handlers.submit, 'function', 'Save-address handler not bound');

    const event = {
        preventDefault() {},
        currentTarget: form,
        submitter: saveButton
    };
    await Promise.all([form.handlers.submit(event), form.handlers.submit(event)]);
    assert.equal(calls.length, loggedIn ? 1 : 0, 'Duplicate submission or unauthenticated write');
    if (loggedIn) {
        assert.equal(calls[0].url, '/api/v1/account/addresses');
        assert.equal(calls[0].payload.recipient, 'Nguyễn Văn A');
        assert.equal(calls[0].payload.addressLine, '12 Đường 3');
        assert.equal(calls[0].payload.phone, '0901234567');
        assert.equal(calls[0].payload.area, 'Thủ Đức');
        assert.equal(calls[0].payload.defaultAddress, true);
        assert.equal(calls[0].payload.latitude, null);
        assert.equal(calls[0].payload.longitude, null);
        assert.equal(saveButton.disabled, false);
        if (failSave) {
            assert.equal(form.wasReset, undefined, 'Failed save erased address form');
            assert.match(el('checkoutAddressFeedback').textContent, /Không lưu được/);
        } else {
            assert.equal(form.wasReset, true, 'Saved form not reset');
            assert.equal(el('checkoutAddress').value, '42', 'Saved address not selected');
        }
    }
}
(async () => {
    await scenario();
    await scenario({ loggedIn: true, failSave: true });
    await scenario({ loggedIn: true, failCarriers: true });
    await scenario({ loggedIn: false });
    console.log('Cart address save regression tests passed');
})().catch(err => { console.error(err); process.exitCode = 1; });
