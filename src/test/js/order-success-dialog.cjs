// Regression test for the success modal after pickup and COD delivery.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync('src/main/resources/templates/customer/cart.html','utf8');
const js = fs.readFileSync('src/main/resources/static/js/customer/order-success.js','utf8');
const checkoutFragment = fs.readFileSync('src/main/resources/templates/fragments/delivery-checkout.html','utf8');
assert.equal((checkoutFragment.match(/id="uteOrderPlacedDialog"/g)||[]).length,0,
  'Success dialog inside hidden checkout panel would freeze the whole page');
assert.equal((html.match(/id="uteOrderPlacedDialog"/g)||[]).length,1,
  'Cart must render precisely one success dialog');

const ids = [
  'uteOrderPlacedDialog', 'uteOrderPlacedHeading', 'uteOrderPlacedId',
  'uteOrderPlacedType', 'uteOrderPlacedBranch', 'uteOrderPlacedTotal',
  'uteOrderPlacedNote', 'uteOrderPlacedDetails', 'uteOrderPlacedClose'
];
for (const id of ids) {
  assert.ok(html.includes('id="' + id + '"'), 'Missing success element: ' + id);
}
assert.ok(html.includes('/js/customer/order-success.js'), 'Missing popup JS inclusion');

const elements = new Map();
for (const id of ids) {
  elements.set(id,{ id, textContent:'', href:'', open:false,
    showModal(){ this.open=true; this.openCalls=(this.openCalls||0)+1; },
    close(){ this.open=false; }, setAttribute(k){ if(k==='open')this.open=true; },
    removeAttribute(k){ if(k==='open')this.open=false; }
  });
}
let clickHandler;
const document = {
  getElementById: id => elements.get(id)||null,
  addEventListener(name,fn){ if(name==='click')clickHandler=fn; }
};
const context = {window:{},document,MilkTeaApi:{formatVND:n=>String(n)+' ₫'}};
vm.createContext(context);
vm.runInContext(js,context);
const popup=context.window.UTEOrderSuccess;
assert.equal(typeof popup.show,'function');
assert.equal(popup.show({orderId:123,type:'COUNTER',branch:'Campus',total:30000,detailsUrl:'/order-confirmation?order=123'}),true);
assert.equal(elements.get('uteOrderPlacedDialog').open,true);
assert.equal(elements.get('uteOrderPlacedId').textContent,'#123');
assert.equal(elements.get('uteOrderPlacedType').textContent,'Nhận tại quán');
assert.equal(elements.get('uteOrderPlacedBranch').textContent,'Campus');
assert.equal(elements.get('uteOrderPlacedTotal').textContent,'30000 ₫');
assert.equal(elements.get('uteOrderPlacedDetails').href,'/order-confirmation?order=123');
clickHandler({target:{closest: selector => selector==='#uteOrderPlacedClose'?{}:null}});
assert.equal(elements.get('uteOrderPlacedDialog').open,false,'Close button must work');
assert.equal(popup.show({orderId:456,type:'DELIVERY',branch:'Thủ Đức',total:55000,detailsUrl:'/my-orders?tab=delivery&order=456'}),true);
assert.equal(elements.get('uteOrderPlacedType').textContent,'Giao tận nơi');
assert.equal(elements.get('uteOrderPlacedDetails').href,'/my-orders?tab=delivery&order=456');
assert.equal(elements.get('uteOrderPlacedDialog').openCalls,2);
// Regression: if stale HTML ever contains an extra dialog inside the checkout
// panel, the popup handler must choose the independent outer dialog.
const outerDialog = elements.get('uteOrderPlacedDialog');
outerDialog.closest = () => null;
document.querySelectorAll = () => [{closest: () => ({}),showModal(){throw Error('Hidden dialog used!');}},outerDialog];
popup.close();
assert.equal(popup.show({orderId:789,type:'COUNTER',total:20000}),true);
assert.equal(outerDialog.open,true);
assert.equal(outerDialog.openCalls,3);

popup.close();
assert.equal(popup.show({orderId:0}),false);
console.log('Cart success popup regression passed: pickup and COD delivery');
