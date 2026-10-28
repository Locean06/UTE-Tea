// No CDN libraries available: connect() must not throw or block HTTP-driven screens.
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../src/main/resources/static/js/common/websocket.js'),'utf8');
const context=vm.createContext({window:{MilkTeaContext:{}}, console:{log(){},warn(){},error(){}}, setTimeout(){}});
vm.runInContext(source,context);
const client=vm.runInContext('MilkTeaRealtime',context);
assert.doesNotThrow(()=>client.connect());
assert.doesNotThrow(()=>client.connect());
console.log('PASS missing SockJS/STOMP libraries degrade to HTTP refresh without crashing');
