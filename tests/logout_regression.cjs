/** Offline regression: logout must not navigate unless the server confirms deletion. */
const fs = require('fs'), vm = require('vm'), assert = require('assert'), path = require('path');
const root = path.resolve(__dirname, '..');
const txt = fs.readFileSync(path.join(root, 'src/main/resources/templates/fragments/header.html'), 'utf8');
const code = txt.match(/<script>\s*(async function logoutAccount\(\) \{[\s\S]*?\})\s*<\/script>/)[1];
async function check(scenario) {
    let calls = 0, refresh = 0;
    const redirects = [], toasts = [];
    const button = { disabled: false, setAttribute(){}, removeAttribute(){} };
    const context = {
        document: { getElementById: () => button, cookie: '' },
        window: { location: { replace: value => redirects.push(value) } },
        MilkTeaApi: {
            post: async () => {
                calls++;
                if (scenario === 'stale-csrf' && calls === 1) { const error = new Error('Invalid CSRF'); error.status = 403; throw error; }
                if (scenario === 'offline') throw new Error('Network unavailable');
            },
            refreshCsrf: async () => { refresh++; },
            showToast: (value, type) => toasts.push({value,type})
        }
    };
    vm.runInNewContext(code, context);
    await context.logoutAccount();
    if (scenario === 'offline') {
        assert.equal(redirects.length, 0);
        assert.equal(button.disabled, false);
        assert.equal(toasts[0].type, 'error');
    } else {
        assert.deepEqual(redirects, ['/login?loggedOut=1']);
        assert.equal(button.disabled, true);
    }
    if (scenario === 'stale-csrf') { assert.equal(refresh, 1); assert.equal(calls, 2); }
}
(async () => {
    await check('normal');
    await check('stale-csrf');
    await check('offline');
    console.log('PASS logout normal, stale CSRF retry, offline error');
})().catch(error => { console.error(error); process.exit(1); });
