/**
 * Bulletproof Logout Confirmation Handler
 * Works across Admin, Super Admin, and Customer dashboards.
 * Handles modal show/hide with or without Bootstrap data-api,
 * deduplicates DOM elements, clears client storage, and submits Spring Security logout form with CSRF.
 */

// 1. Explicitly attach functions to global window object immediately
window.openLogoutModal = function openLogoutModal(e) {
    if (e) {
        if (typeof e.preventDefault === 'function') e.preventDefault();
        if (typeof e.stopPropagation === 'function') e.stopPropagation();
    }

    // Clean up duplicate modals if present
    const modals = document.querySelectorAll('#logoutConfirmModal');
    if (modals.length > 1) {
        for (let i = 1; i < modals.length; i++) {
            modals[i].remove();
        }
    }

    const modalEl = document.getElementById('logoutConfirmModal');
    if (!modalEl) {
        console.warn('logoutConfirmModal element not found in DOM! Executing direct logout fallback.');
        window.handleConfirmLogout();
        return;
    }

    // Move modal and form directly to <body> to avoid CSS stacking context & overflow issues
    if (modalEl.parentElement !== document.body) {
        document.body.appendChild(modalEl);
    }

    const formEl = document.getElementById('globalLogoutForm');
    if (formEl && formEl.parentElement !== document.body) {
        document.body.appendChild(formEl);
    }

    // Try Bootstrap 5 Modal API first
    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        try {
            const modalInstance = bootstrap.Modal.getOrCreateInstance(modalEl, {
                backdrop: 'static',
                keyboard: true
            });
            modalInstance.show();
            return;
        } catch (err) {
            console.warn('bootstrap.Modal error, using manual fallback:', err);
        }
    }

    // Pure DOM Fallback if Bootstrap JS is unavailable or errored
    modalEl.classList.add('show');
    modalEl.style.display = 'block';
    modalEl.setAttribute('aria-modal', 'true');
    modalEl.removeAttribute('aria-hidden');

    if (!document.querySelector('.modal-backdrop')) {
        const backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop fade show';
        backdrop.onclick = window.closeLogoutModal;
        document.body.appendChild(backdrop);
    }
    document.body.classList.add('modal-open');
};

window.closeLogoutModal = function closeLogoutModal() {
    const modalEl = document.getElementById('logoutConfirmModal');
    if (modalEl) {
        if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
            try {
                const inst = bootstrap.Modal.getInstance(modalEl);
                if (inst) inst.hide();
            } catch (e) {}
        }
        modalEl.classList.remove('show');
        modalEl.style.display = 'none';
        modalEl.setAttribute('aria-hidden', 'true');
        modalEl.removeAttribute('aria-modal');
    }
    document.querySelectorAll('.modal-backdrop').forEach(b => b.remove());
    document.body.classList.remove('modal-open');
    document.body.style.removeProperty('overflow');
    document.body.style.removeProperty('padding-right');
};

window.handleConfirmLogout = function handleConfirmLogout() {
    const confirmLogoutBtn = document.getElementById('confirmLogoutBtn');
    if (confirmLogoutBtn) {
        confirmLogoutBtn.disabled = true;
        confirmLogoutBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Logging out...';
    }

    // 1. Clear all client-side authentication tokens, user info & session storage
    try {
        localStorage.clear();
        sessionStorage.clear();
    } catch (err) {
        console.warn('Storage cleanup warning:', err);
    }

    // 2. Submit CSRF-enabled Spring Security logout form
    const globalLogoutForm = document.getElementById('globalLogoutForm');
    if (globalLogoutForm) {
        globalLogoutForm.submit();
        return;
    }

    // Fallback form search
    const fallbackForm = document.querySelector('form[action*="/logout"]');
    if (fallbackForm) {
        fallbackForm.submit();
        return;
    }

    // Final fallback redirect
    window.location.href = '/login?logout';
};

// Aliases in local scope
const openLogoutModal = window.openLogoutModal;
const closeLogoutModal = window.closeLogoutModal;
const handleConfirmLogout = window.handleConfirmLogout;

// Attach event listeners as soon as DOM is ready
function initLogoutHandlers() {
    const modals = document.querySelectorAll('#logoutConfirmModal');
    if (modals.length > 1) {
        for (let i = 1; i < modals.length; i++) {
            modals[i].remove();
        }
    }

    const modalEl = document.getElementById('logoutConfirmModal');
    if (modalEl && modalEl.parentElement !== document.body) {
        document.body.appendChild(modalEl);
    }

    const formEl = document.getElementById('globalLogoutForm');
    if (formEl && formEl.parentElement !== document.body) {
        document.body.appendChild(formEl);
    }

    const confirmBtn = document.getElementById('confirmLogoutBtn');
    if (confirmBtn) {
        confirmBtn.removeEventListener('click', handleConfirmLogout);
        confirmBtn.addEventListener('click', handleConfirmLogout);
    }

    const cancelBtn = document.getElementById('cancelLogoutBtn');
    if (cancelBtn) {
        cancelBtn.removeEventListener('click', closeLogoutModal);
        cancelBtn.addEventListener('click', closeLogoutModal);
    }
}

// Global click delegation for all logout buttons
document.addEventListener('click', function (e) {
    const target = e.target;
    if (!target) return;

    // Check if clicked element or any parent is a logout trigger button or link
    const logoutTrigger = target.closest(
        '[data-bs-target="#logoutConfirmModal"], ' +
        '.sa-logout-btn, ' +
        '[data-action="logout"], ' +
        '#adminNavbarLogoutBtn, ' +
        '#sidebarFooterLogoutBtn, ' +
        '#headerDropdownLogoutBtn, ' +
        '#saSidebarLogoutBtn, ' +
        '#saNavbarLogoutBtn, ' +
        '#saFooterLogoutBtn, ' +
        '#logoutBtn, ' +
        '#signOutBtn'
    );

    if (logoutTrigger) {
        e.preventDefault();
        e.stopPropagation();
        window.openLogoutModal(e);
        return;
    }

    // Intercept any direct submit button inside a logout form
    const submitInLogoutForm = target.closest('form[action*="/logout"] button, form[action*="/logout"] input[type="submit"]');
    if (submitInLogoutForm) {
        const form = submitInLogoutForm.closest('form');
        if (form && form.id !== 'globalLogoutForm') {
            e.preventDefault();
            e.stopPropagation();
            window.openLogoutModal(e);
            return;
        }
    }
});

// Intercept form submit event as an extra safety net
document.addEventListener('submit', function (e) {
    const form = e.target;
    if (form && form.id !== 'globalLogoutForm' && form.matches && form.matches('form[action*="/logout"], form[action$="/logout"]')) {
        e.preventDefault();
        e.stopPropagation();
        window.openLogoutModal(e);
    }
});

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initLogoutHandlers);
} else {
    initLogoutHandlers();
}
