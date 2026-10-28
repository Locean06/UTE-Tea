/**
 * MilkTea Unified HTTP Client & API Utilities
 * Tuân thủ author_rule.md và frontend_rule.md
 */
const MilkTeaApi = (() => {
    let csrfToken = null;
    let csrfHeaderName = 'X-CSRF-TOKEN';
    let csrfRequest = null;

    function currentCsrfCookie() {
        const entry = document.cookie.split(';').map(value => value.trim()).find(value => value.startsWith('XSRF-TOKEN='));
        return entry ? decodeURIComponent(entry.substring('XSRF-TOKEN='.length)) : null;
    }

    // Khởi tạo CSRF token
    async function initCsrf() {
        const cookie = currentCsrfCookie();
        if (cookie) { csrfToken = cookie; csrfHeaderName = 'X-XSRF-TOKEN'; return csrfToken; }
        if (csrfRequest) return csrfRequest;
        csrfRequest = (async () => { try {
            const res = await fetch('/api/v1/auth/csrf');
            const json = await res.json();
            if (json && json.data) {
                csrfToken = json.data.token;
                csrfHeaderName = json.data.headerName || 'X-CSRF-TOKEN';
            }
        } catch (e) {
            console.warn('Không thể nạp CSRF token ban đầu:', e);
        } finally { csrfRequest = null; }
        return csrfToken;
        })();
        return csrfRequest;
    }

    // Refresh only when CSRF cookie was explicitly cleared after a 403.
    async function refreshCsrf() {
        csrfToken = null;
        csrfRequest = null;
        return initCsrf();
    }

    // Format tiền tệ VND
    function formatVND(amount) {
        if (amount === null || amount === undefined) return '0 ₫';
        const num = typeof amount === 'string' ? parseFloat(amount) : amount;
        return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(num);
    }

    // Unified fetch
    async function request(url, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const headers = options.headers ? { ...options.headers } : {};

        // Đính kèm CSRF token cho các mutation methods
        if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
            await initCsrf();
            const cookie = currentCsrfCookie();
            if (cookie) { csrfToken = cookie; csrfHeaderName = 'X-XSRF-TOKEN'; }
            if (csrfToken) {
                headers[csrfHeaderName] = csrfToken;
            }
        }

        // Đính kèm Guest Token nếu có trong RAM session hiện tại
        if (window.MilkTeaContext) {
            if (window.MilkTeaContext.tableToken && !headers['X-Table-Session-Token']) {
                headers['X-Table-Session-Token'] = window.MilkTeaContext.tableToken;
            }
            if (window.MilkTeaContext.counterToken && !headers['X-Counter-Order-Token']) {
                headers['X-Counter-Order-Token'] = window.MilkTeaContext.counterToken;
            }
        }

        // Nếu body là object và chưa set Content-Type
        let body = options.body;
        if (body && typeof body === 'object' && !(body instanceof FormData) && !(body instanceof URLSearchParams)) {
            headers['Content-Type'] = 'application/json';
            body = JSON.stringify(body);
        }

        const fetchOptions = {
            method,
            headers,
            credentials: 'same-origin', // Gửi HttpOnly cookie milktea_access_token tự động
            body
        };

        try {
            const response = await fetch(url, fetchOptions);

            // Kiểm tra Content-Type
            const contentType = response.headers.get('content-type') || '';
            let result = null;
            if (contentType.includes('application/json')) {
                result = await response.json();
            } else {
                result = { success: response.ok, data: await response.text() };
            }

            if (!response.ok) {
                const errorCode = result && result.error ? result.error.code : 'HTTP_' + response.status;
                const errorMessage = result && result.error ? result.error.message : response.statusText;
                
                // Nếu bị 401 hoặc 403, kiểm tra nếu cần đăng nhập
                if (response.status === 401) {
                    console.warn('Phiên đăng nhập hết hạn hoặc chưa đăng nhập');
                }

                const err = new Error(errorMessage || 'Đã có lỗi xảy ra');
                err.status = response.status;
                err.code = errorCode;
                err.traceId = result && result.traceId ? result.traceId : null;
                err.details = result && result.error ? result.error.details : null;
                throw err;
            }

            return result && result.data !== undefined ? result.data : result;
        } catch (error) {
            console.error(`API Error [${method} ${url}]:`, error);
            throw error;
        }
    }

    // Tiện ích Toast thông báo
    function showToast(message, type = 'success') {
        const toastEl = document.getElementById('liveToast');
        if (toastEl) {
            const toastBody = toastEl.querySelector('.toast-message') || toastEl.querySelector('.toast-body');
            const toastTitle = toastEl.querySelector('.toast-title');
            if (toastBody) toastBody.textContent = message;
            const variants = {
                success: { title: 'Thành công', classes: ['bg-success', 'text-white'], live: 'polite' },
                error: { title: 'Lỗi', classes: ['bg-danger', 'text-white'], live: 'assertive' },
                warning: { title: 'Cảnh báo', classes: ['bg-warning', 'text-dark'], live: 'polite' },
                info: { title: 'Thông tin', classes: ['bg-info', 'text-dark'], live: 'polite' }
            };
            const variant = variants[type] || variants.info;
            if (toastTitle) toastTitle.textContent = variant.title;
            toastEl.className = 'toast align-items-center border-0 ' + variant.classes.join(' ');
            toastEl.setAttribute('role', type === 'error' ? 'alert' : 'status');
            toastEl.setAttribute('aria-live', variant.live);
            const closeBtn = toastEl.querySelector('.btn-close');
            if (closeBtn) closeBtn.classList.toggle('btn-close-white', !variant.classes.includes('text-dark'));
            const toast = bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 3500 });
            toast.show();
        } else {
            alert(message);
        }
    }

    return {
        escapeHtml: value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])),
        get: (url, headers) => request(url, { method: 'GET', headers }),
        post: (url, body, headers) => request(url, { method: 'POST', body, headers }),
        put: (url, body, headers) => request(url, { method: 'PUT', body, headers }),
        delete: (url, headers) => request(url, { method: 'DELETE', headers }),
        upload: (url, formData) => request(url, { method: 'POST', body: formData }),
        formatVND,
        showToast,
        initCsrf,
        refreshCsrf
    };
})();

// Khởi chạy nạp CSRF khi trang load
document.addEventListener('DOMContentLoaded', () => {
    MilkTeaApi.initCsrf();
});
