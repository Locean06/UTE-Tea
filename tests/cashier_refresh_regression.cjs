// Node-only behavioral regression: new delivery orders must survive overlapping polling and push refreshes.
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const callbacks = {};
const elements = new Map();
function element(tag='div') {
  const node = {
    tagName: tag, textContent: '', children: [], dataset: {}, className: '', disabled:false,
    classList: {add(){},remove(){}},
    append(...children){this.children.push(...children);},
    replaceChildren(...children){this.children=[...children];},
    setAttribute(){}, addEventListener(){},
    get childNodes(){return this.children;}
  };
  return node;
}
function getElementById(id){
  if (id==='posDeliveryNewList' || id==='posDeliveryNewCount') {
    if (!elements.has(id)) elements.set(id,element());
    return elements.get(id);
  }
  return null;
}
let resolveFirst;
let listCalls=0;
const document={getElementById,createElement:element,addEventListener(name,cb){callbacks[name]=cb;}};
const context=vm.createContext({
  document,window:{MilkTeaContext:{}},setInterval(){},
  MilkTeaRealtime:{on(){},onReconnect(){},connect(){}},
  MilkTeaApi:{
    formatVND:v=>String(v)+'đ',
    get(url){
      assert.equal(url,'/api/v1/cashier/delivery-orders');
      listCalls++;
      if(listCalls===1)return new Promise(resolve=>{resolveFirst=resolve;});
      return Promise.resolve([{id:1,status:'NEW',recipient:'A',totalAmount:100,shopName:'UTE Tea'},
        {id:2,status:'NEW',recipient:'B',totalAmount:200,shopName:'UTE Tea'}]);
    }
  }
});
vm.runInContext(fs.readFileSync(path.join(root,'src/main/resources/static/js/cashier/delivery-inbox.js'),'utf8'),context);
(async () => {
  callbacks.DOMContentLoaded(); // first HTTP request pending
  const cashier = vm.runInContext('CashierDeliveryInbox',context);
  await cashier.reload(); // queues rather than issuing a concurrent fetch
  assert.equal(listCalls,1);
  resolveFirst([{id:1,status:'NEW',recipient:'A',totalAmount:100,shopName:'UTE Tea'}]);
  await new Promise(resolve=>setImmediate(resolve));
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(listCalls,2,'a queued refresh must fetch the newest orders');
  assert.equal(elements.get('posDeliveryNewCount').textContent,2);
  assert.equal(elements.get('posDeliveryNewList').children.length,2);
  console.log('PASS overlapping cashier WebSocket/polling refresh preserves both orders');
})().catch(e=>{console.error(e);process.exitCode=1;});
