/**
 * Global Notifications Dropdown & Real-Time Sync Client
 */
document.addEventListener('DOMContentLoaded', function () {
    // Initial fetch to ensure badges and dropdown are populated
    loadGlobalNotificationsDropdown();
});

function getValidJwtToken() {
    const token = localStorage.getItem('token');
    if (token && typeof token === 'string') {
        const trimmed = token.trim();
        if (trimmed && trimmed !== 'null' && trimmed !== 'undefined' && trimmed !== '[object Object]' && trimmed.split('.').length === 3) {
            return trimmed;
        }
    }
    return null;
}

async function loadGlobalNotificationsDropdown() {
    const containers = [
        document.getElementById('globalNotificationDropdownList'),
        document.getElementById('saNotificationDropdownList'),
        document.getElementById('adminNotificationDropdownList')
    ].filter(Boolean);

    if (containers.length === 0) return;

    try {
        const token = getValidJwtToken();
        const headers = { 'Content-Type': 'application/json' };
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        const response = await fetch('/api/notifications', { method: 'GET', headers });
        if (!response.ok) {
            containers.forEach(c => {
                c.innerHTML = '<div class="text-center py-3 text-secondary small">Failed to load notifications.</div>';
            });
            return;
        }

        const notifications = await response.json();
        const unreadCount = notifications.filter(n => !n.isRead).length;

        // Update badge counts
        updateNotificationBadges(unreadCount);

        if (!notifications || notifications.length === 0) {
            containers.forEach(c => {
                c.innerHTML = `
                    <div class="text-center py-4 text-secondary small">
                        <i class="fa-solid fa-bell-slash fs-4 mb-2 d-block opacity-50"></i>
                        <p class="mb-0">No notifications logged.</p>
                    </div>`;
            });
            return;
        }

        const html = notifications.map(n => {
            const isUnread = !n.isRead;
            const iconClass = n.icon ? n.icon : 'fa-bell';
            const timeFormatted = n.createdAt ? new Date(n.createdAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '';

            return `
                <div id="dropdown-item-${n.id}" class="p-2.5 mb-1.5 rounded-3 ${isUnread ? 'bg-secondary bg-opacity-10 border border-secondary border-opacity-25' : 'border border-transparent'}" style="transition: all 0.2s ease;">
                    <div class="d-flex align-items-start justify-content-between gap-2">
                        <div class="d-flex align-items-start gap-2.5">
                            <div class="avatar-initials-circle flex-shrink-0" style="width: 32px; height: 32px; font-size: 0.85rem; background: rgba(245, 158, 11, 0.15); color: #f59e0b; display: flex; align-items: center; justify-content: center; border-radius: 50%;">
                                <i class="fa-solid ${iconClass}"></i>
                            </div>
                            <div>
                                <div class="d-flex align-items-center gap-1.5 mb-0.5">
                                    <span class="fw-bold text-white x-small">${escapeHtml(n.title)}</span>
                                    ${isUnread ? `<span class="badge bg-danger rounded-pill px-1.5" style="font-size: 0.55rem;" id="dropdown-unread-tag-${n.id}">NEW</span>` : ''}
                                </div>
                                <p class="text-secondary mb-1" style="font-size: 0.75rem; line-height: 1.35; margin-bottom: 0.25rem;">${escapeHtml(n.message)}</p>
                                <div class="d-flex align-items-center gap-2" style="font-size: 0.68rem; color: #94a3b8;">
                                    <span><i class="fa-solid fa-clock me-1"></i>${timeFormatted}</span>
                                    ${n.actionUrl ? `<a href="${n.actionUrl}" class="text-warning text-decoration-none fw-semibold">Action <i class="fa-solid fa-arrow-right" style="font-size: 0.6rem;"></i></a>` : ''}
                                </div>
                            </div>
                        </div>
                        ${isUnread ? `
                            <button type="button" class="btn btn-sm text-warning p-1 border-0 rounded-circle flex-shrink-0 header-read-btn" 
                                    id="btn-dropdown-read-${n.id}"
                                    onclick="markSingleGlobalNotificationRead(event, ${n.id})" title="Mark as Read" style="line-height: 1; background: rgba(245, 158, 11, 0.1);">
                                <i class="fa-solid fa-check" style="font-size: 0.8rem;"></i>
                            </button>
                        ` : ''}
                    </div>
                </div>
            `;
        }).join('');

        containers.forEach(c => { c.innerHTML = html; });

    } catch (err) {
        console.error('Error fetching global notifications:', err);
    }
}

async function markSingleGlobalNotificationRead(event, id) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    const btn = document.getElementById('btn-dropdown-read-' + id);
    if (btn) btn.disabled = true;

    try {
        const token = getValidJwtToken();
        const headers = { 'Content-Type': 'application/json' };
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        const response = await fetch(`/api/notifications/${id}/read`, { method: 'POST', headers });
        if (response.ok) {
            const item = document.getElementById('dropdown-item-' + id);
            if (item) {
                item.classList.remove('bg-secondary', 'bg-opacity-10', 'border', 'border-secondary', 'border-opacity-25');
                item.classList.add('border-transparent');
            }
            const unreadTag = document.getElementById('dropdown-unread-tag-' + id);
            if (unreadTag) unreadTag.remove();
            if (btn) btn.remove();

            // Also update /notifications page if user is currently on it
            const card = document.getElementById('notification-card-' + id);
            if (card) {
                card.setAttribute('data-read', 'true');
                card.classList.remove('border-primary', 'border-opacity-25', 'unread');
            }
            const cardBadge = document.getElementById('unread-badge-' + id);
            if (cardBadge) cardBadge.remove();
            const cardBtn = document.getElementById('btn-read-' + id);
            if (cardBtn) cardBtn.remove();

            decrementGlobalNotificationBadges();
        }
    } catch (err) {
        console.error('Error marking notification as read:', err);
        if (btn) btn.disabled = false;
    }
}

async function markAllGlobalNotificationsAsRead(event) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }

    try {
        const token = getValidJwtToken();
        const headers = { 'Content-Type': 'application/json' };
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        const response = await fetch('/api/notifications/read-all', { method: 'POST', headers });
        if (response.ok) {
            // Update dropdown items
            document.querySelectorAll('[id^="dropdown-item-"]').forEach(item => {
                item.classList.remove('bg-secondary', 'bg-opacity-10', 'border', 'border-secondary', 'border-opacity-25');
                item.classList.add('border-transparent');
            });
            document.querySelectorAll('[id^="dropdown-unread-tag-"]').forEach(t => t.remove());
            document.querySelectorAll('[id^="btn-dropdown-read-"]').forEach(b => b.remove());

            // Update /notifications page if user is currently on it
            document.querySelectorAll('.notification-card').forEach(card => {
                card.setAttribute('data-read', 'true');
                card.classList.remove('border-primary', 'border-opacity-25', 'unread');
            });
            document.querySelectorAll('[id^="unread-badge-"]').forEach(b => b.remove());
            document.querySelectorAll('[id^="btn-read-"]').forEach(b => b.remove());

            updateNotificationBadges(0);
        }
    } catch (err) {
        console.error('Error marking all notifications as read:', err);
    }
}

function updateNotificationBadges(count) {
    const badges = [
        document.getElementById('headerNotificationBadge'),
        document.getElementById('saHeaderNotificationBadge'),
        document.getElementById('adminHeaderNotificationBadge'),
        document.getElementById('dropdownUnreadBadge'),
        document.getElementById('saDropdownUnreadBadge'),
        document.getElementById('adminDropdownUnreadBadge'),
        document.getElementById('unreadCountTab'),
        document.getElementById('unreadAlertsHeaderBadge')
    ].filter(Boolean);

    badges.forEach(b => {
        if (b.id === 'unreadAlertsHeaderBadge') {
            b.textContent = count + ' Unread Alerts';
        } else if (b.id === 'dropdownUnreadBadge' || b.id === 'saDropdownUnreadBadge' || b.id === 'adminDropdownUnreadBadge') {
            b.textContent = count > 0 ? count + ' unread' : '0 unread';
            if (count === 0) b.classList.add('d-none');
            else b.classList.remove('d-none');
        } else {
            if (count === 0) {
                b.style.display = 'none';
            } else {
                b.style.display = '';
                b.textContent = count;
            }
        }
    });
}

function decrementGlobalNotificationBadges() {
    const badge = document.getElementById('headerNotificationBadge') || 
                  document.getElementById('saHeaderNotificationBadge') || 
                  document.getElementById('adminHeaderNotificationBadge');
    let current = badge ? parseInt(badge.textContent.trim()) || 0 : 0;
    const newCount = Math.max(0, current - 1);
    updateNotificationBadges(newCount);
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, "&amp;")
              .replace(/</g, "&lt;")
              .replace(/>/g, "&gt;")
              .replace(/"/g, "&quot;")
              .replace(/'/g, "&#039;");
}
