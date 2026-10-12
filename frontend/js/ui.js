/**
 * CampusConnect UI Utilities
 * Toast alerts, accessible modals, DOM security helpers, status badges, and formatters.
 */

(function (window) {
  'use strict';

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatDateTime(isoString) {
    if (!isoString) return '—';
    try {
      const d = new Date(isoString);
      if (isNaN(d.getTime())) return escapeHtml(isoString);
      return d.toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return escapeHtml(isoString);
    }
  }

  function formatDate(isoString) {
    if (!isoString) return '—';
    try {
      const d = new Date(isoString);
      if (isNaN(d.getTime())) return escapeHtml(isoString);
      return d.toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      });
    } catch {
      return escapeHtml(isoString);
    }
  }

  // Toasts
  function showToast(message, type = 'info', duration = 5000) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'polite');

    const iconMap = {
      success: '✓',
      error: '✕',
      warning: '⚠',
      info: 'ℹ'
    };

    toast.innerHTML = `
      <span class="toast-icon">${iconMap[type] || 'ℹ'}</span>
      <div class="toast-message">${escapeHtml(message)}</div>
      <button class="toast-close" aria-label="Close notification">&times;</button>
    `;

    const closeBtn = toast.querySelector('.toast-close');
    let timer = null;

    const remove = () => {
      if (timer) clearTimeout(timer);
      toast.classList.add('toast-hiding');
      setTimeout(() => {
        if (toast.parentNode) toast.parentNode.removeChild(toast);
      }, 300);
    };

    closeBtn.addEventListener('click', remove);
    if (duration > 0) {
      timer = setTimeout(remove, duration);
    }

    container.appendChild(toast);
  }

  // Modals & Confirm Dialogs
  let activeModalElement = null;
  let previousActiveElement = null;

  function showModal({ title, contentHtml, footerHtml, onClose }) {
    previousActiveElement = document.activeElement;
    const overlay = document.getElementById('modal-overlay');
    const container = document.getElementById('modal-content');
    if (!overlay || !container) return;

    container.innerHTML = `
      <div class="modal-header">
        <h2 class="modal-title" id="modal-title">${escapeHtml(title)}</h2>
        <button class="modal-close-btn" aria-label="Close dialog">&times;</button>
      </div>
      <div class="modal-body">${contentHtml}</div>
      ${footerHtml ? `<div class="modal-footer">${footerHtml}</div>` : ''}
    `;

    overlay.classList.add('active');
    overlay.setAttribute('aria-hidden', 'false');
    activeModalElement = overlay;

    const close = () => {
      overlay.classList.remove('active');
      overlay.setAttribute('aria-hidden', 'true');
      container.innerHTML = '';
      activeModalElement = null;
      if (previousActiveElement && previousActiveElement.focus) {
        previousActiveElement.focus();
      }
      if (onClose) onClose();
    };

    container.querySelector('.modal-close-btn').addEventListener('click', close);
    overlay.onclick = (e) => {
      if (e.target === overlay) close();
    };

    // Esc key handling
    const keyHandler = (e) => {
      if (e.key === 'Escape' && activeModalElement) {
        close();
        document.removeEventListener('keydown', keyHandler);
      }
    };
    document.addEventListener('keydown', keyHandler);

    return { close };
  }

  function confirmDialog({ title, message, confirmText = 'Confirm', confirmType = 'primary' }) {
    return new Promise((resolve) => {
      const contentHtml = `<p class="confirm-message">${escapeHtml(message)}</p>`;
      const footerHtml = `
        <button type="button" class="btn btn-outline" id="confirm-cancel-btn">Cancel</button>
        <button type="button" class="btn btn-${confirmType}" id="confirm-ok-btn">${escapeHtml(confirmText)}</button>
      `;

      let modal;
      modal = showModal({
        title,
        contentHtml,
        footerHtml,
        onClose: () => resolve(false)
      });

      const cancelBtn = document.getElementById('confirm-cancel-btn');
      const okBtn = document.getElementById('confirm-ok-btn');

      cancelBtn.addEventListener('click', () => {
        modal.close();
        resolve(false);
      });

      okBtn.addEventListener('click', () => {
        modal.close();
        resolve(true);
      });
    });
  }

  // Badges
  function renderStatusBadge(status) {
    if (!status) return '';
    const s = String(status).toUpperCase();
    let badgeClass = 'badge-secondary';

    if (['PUBLISHED', 'CONFIRMED', 'ACTIVE', 'ACCEPTED'].includes(s)) {
      badgeClass = 'badge-success';
    } else if (['WAITLISTED', 'PENDING'].includes(s)) {
      badgeClass = 'badge-warning';
    } else if (['CANCELLED', 'INACTIVE', 'DECLINED'].includes(s)) {
      badgeClass = 'badge-danger';
    } else if (['DRAFT'].includes(s)) {
      badgeClass = 'badge-neutral';
    } else if (['COMPLETED'].includes(s)) {
      badgeClass = 'badge-primary';
    }

    return `<span class="badge ${badgeClass}">${escapeHtml(status)}</span>`;
  }

  function renderModeBadge(mode) {
    if (!mode) return '';
    const m = String(mode).toUpperCase();
    const isTeam = m === 'TEAM';
    return `<span class="badge ${isTeam ? 'badge-team' : 'badge-individual'}">${isTeam ? '👥 Team' : '👤 Individual'}</span>`;
  }

  function renderEmptyState(title, description, actionHtml = '') {
    return `
      <div class="empty-state">
        <div class="empty-state-icon">📋</div>
        <h3 class="empty-state-title">${escapeHtml(title)}</h3>
        <p class="empty-state-desc">${escapeHtml(description)}</p>
        ${actionHtml ? `<div class="empty-state-action">${actionHtml}</div>` : ''}
      </div>
    `;
  }

  function setButtonLoading(button, isLoading, loadingText = 'Processing...') {
    if (!button) return;
    if (isLoading) {
      button.dataset.originalHtml = button.innerHTML;
      button.disabled = true;
      button.innerHTML = `<span class="spinner-small" aria-hidden="true"></span> ${escapeHtml(loadingText)}`;
    } else {
      button.disabled = false;
      if (button.dataset.originalHtml) {
        button.innerHTML = button.dataset.originalHtml;
      }
    }
  }

  window.UI = {
    escapeHtml,
    formatDateTime,
    formatDate,
    showToast,
    showModal,
    confirmDialog,
    renderStatusBadge,
    renderModeBadge,
    renderEmptyState,
    setButtonLoading
  };
})(window);
