/**
 * CampusConnect Admin Views
 * Platform dashboard metrics, user search and relationship inspection,
 * role/active status mutation, platform events and registrations catalog, and read-only audit log.
 */

(function (window) {
  'use strict';

  // --- Admin Dashboard ---
  async function renderDashboard() {
    const main = document.getElementById('app-main');
    if (!main) return;

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">Administrator Dashboard</h1>
            <p class="text-muted">Platform metrics, user distributions, event lifecycle statuses, and audit controls.</p>
          </div>
          <div class="d-flex gap-2">
            <a href="#/admin/users" class="btn btn-primary">Manage Users</a>
            <a href="#/admin/audit" class="btn btn-outline">Audit Log</a>
          </div>
        </div>

        <div id="admin-dashboard-metrics">
          <div class="loading-container py-5"><div class="spinner"></div><p>Aggregating system statistics...</p></div>
        </div>
      </div>
    `;

    const container = document.getElementById('admin-dashboard-metrics');

    try {
      const data = await window.API.admin.getDashboard();
      const d = data.dashboard;

      container.innerHTML = `
        <!-- High-level Metric Cards -->
        <div class="stats-grid mb-4">
          <div class="stat-card">
            <span class="stat-number text-navy">${d.users || 0}</span>
            <span class="stat-label">Total Users (${d.activeUsers || 0} Active)</span>
          </div>
          <div class="stat-card">
            <span class="stat-number text-primary">${d.events || 0}</span>
            <span class="stat-label">Platform Events</span>
          </div>
          <div class="stat-card">
            <span class="stat-number text-success">${d.registrations || 0}</span>
            <span class="stat-label">Total Registrations</span>
          </div>
          <div class="stat-card">
            <span class="stat-number text-warning">${d.scores || 0}</span>
            <span class="stat-label">Submitted Scores</span>
          </div>
          <div class="stat-card">
            <span class="stat-number text-navy">${d.publishedResults || 0}</span>
            <span class="stat-label">Published Results Events</span>
          </div>
        </div>

        <!-- Grouped Breakdowns -->
        <div class="row gap-4 mb-4">
          <!-- Users by Role -->
          <div class="col-4">
            <div class="card p-4 h-100">
              <h3 class="card-title text-navy mb-3">Users by Role</h3>
              <div class="breakdown-list">
                ${(d.usersByRole || []).map(item => `
                  <div class="d-flex justify-between align-center py-2 border-bottom">
                    <span class="font-medium">${window.UI.escapeHtml(item.key)}</span>
                    <span class="badge badge-secondary font-bold">${item.count}</span>
                  </div>
                `).join('')}
              </div>
              <div class="mt-3">
                <a href="#/admin/users" class="btn btn-outline btn-block btn-sm">Explore Users &rarr;</a>
              </div>
            </div>
          </div>

          <!-- Events by Lifecycle Status -->
          <div class="col-4">
            <div class="card p-4 h-100">
              <h3 class="card-title text-navy mb-3">Events by Status</h3>
              <div class="breakdown-list">
                ${(d.eventsByStatus || []).map(item => `
                  <div class="d-flex justify-between align-center py-2 border-bottom">
                    <span>${window.UI.renderStatusBadge(item.key)}</span>
                    <span class="badge badge-secondary font-bold">${item.count}</span>
                  </div>
                `).join('')}
              </div>
              <div class="mt-3">
                <a href="#/admin/events" class="btn btn-outline btn-block btn-sm">Explore Events &rarr;</a>
              </div>
            </div>
          </div>

          <!-- Registrations by Status -->
          <div class="col-4">
            <div class="card p-4 h-100">
              <h3 class="card-title text-navy mb-3">Registrations by Status</h3>
              <div class="breakdown-list">
                ${(d.registrationsByStatus || []).map(item => `
                  <div class="d-flex justify-between align-center py-2 border-bottom">
                    <span>${window.UI.renderStatusBadge(item.key)}</span>
                    <span class="badge badge-secondary font-bold">${item.count}</span>
                  </div>
                `).join('')}
              </div>
              <div class="mt-3">
                <a href="#/admin/registrations" class="btn btn-outline btn-block btn-sm">Explore Registrations &rarr;</a>
              </div>
            </div>
          </div>
        </div>
      `;
    } catch (err) {
      container.innerHTML = `<div class="alert alert-error">Failed to load dashboard metrics: ${window.UI.escapeHtml(err.message)}</div>`;
    }
  }

  // --- User Search, Details & Mutation ---
  let userSearchParams = {
    q: '',
    role: '',
    active: '',
    sort: 'displayName',
    direction: 'asc',
    page: 1,
    pageSize: 20
  };

  async function renderAdminUsers() {
    const main = document.getElementById('app-main');
    if (!main) return;

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">User Account Administration</h1>
            <p class="text-muted">Search accounts, inspect relationship constraints, promote/demote roles, and toggle account activation.</p>
          </div>
        </div>

        <!-- Filter Bar -->
        <div class="card p-3 mb-4">
          <form id="admin-user-search-form" class="filter-form">
            <div class="form-row">
              <div class="form-group flex-2">
                <label for="admin-q" class="form-label">Search Display Name / Email</label>
                <input type="text" id="admin-q" class="form-control" placeholder="Search user..." value="${window.UI.escapeHtml(userSearchParams.q)}">
              </div>

              <div class="form-group flex-1">
                <label for="admin-role-filter" class="form-label">Role</label>
                <select id="admin-role-filter" class="form-control">
                  <option value="">All Roles</option>
                  <option value="STUDENT" ${userSearchParams.role === 'STUDENT' ? 'selected' : ''}>STUDENT</option>
                  <option value="ORGANIZER" ${userSearchParams.role === 'ORGANIZER' ? 'selected' : ''}>ORGANIZER</option>
                  <option value="JUDGE" ${userSearchParams.role === 'JUDGE' ? 'selected' : ''}>JUDGE</option>
                  <option value="ADMIN" ${userSearchParams.role === 'ADMIN' ? 'selected' : ''}>ADMIN</option>
                </select>
              </div>

              <div class="form-group flex-1">
                <label for="admin-active-filter" class="form-label">Active Status</label>
                <select id="admin-active-filter" class="form-control">
                  <option value="">All</option>
                  <option value="true" ${userSearchParams.active === 'true' ? 'selected' : ''}>Active Only</option>
                  <option value="false" ${userSearchParams.active === 'false' ? 'selected' : ''}>Inactive Only</option>
                </select>
              </div>

              <div class="form-group flex-auto form-actions">
                <label class="form-label">&nbsp;</label>
                <div class="btn-group">
                  <button type="submit" class="btn btn-primary">Filter</button>
                  <button type="button" id="btn-reset-user-filters" class="btn btn-outline">Reset</button>
                </div>
              </div>
            </div>
          </form>
        </div>

        <div id="admin-users-table-container" class="card">
          <div class="loading-container py-5"><div class="spinner"></div><p>Searching accounts...</p></div>
        </div>

        <div id="admin-users-pagination" class="pagination-wrapper mt-4"></div>
      </div>
    `;

    const form = document.getElementById('admin-user-search-form');
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      userSearchParams.q = document.getElementById('admin-q').value.trim();
      userSearchParams.role = document.getElementById('admin-role-filter').value;
      userSearchParams.active = document.getElementById('admin-active-filter').value;
      userSearchParams.page = 1;
      loadAdminUsers();
    });

    document.getElementById('btn-reset-user-filters').addEventListener('click', () => {
      userSearchParams = { q: '', role: '', active: '', sort: 'displayName', direction: 'asc', page: 1, pageSize: 20 };
      form.reset();
      loadAdminUsers();
    });

    loadAdminUsers();
  }

  async function loadAdminUsers() {
    const container = document.getElementById('admin-users-table-container');
    const pagination = document.getElementById('admin-users-pagination');
    if (!container) return;

    try {
      const data = await window.API.admin.getUsers(userSearchParams);
      const users = data.users || [];

      if (users.length === 0) {
        container.innerHTML = window.UI.renderEmptyState('No Users Found', 'No accounts matched your search criteria.');
        if (pagination) pagination.innerHTML = '';
        return;
      }

      let rowsHtml = '';
      users.forEach(u => {
        rowsHtml += `
          <tr>
            <td class="font-bold text-navy">#${u.userId}</td>
            <td class="font-medium">${window.UI.escapeHtml(u.displayName)}</td>
            <td>${window.UI.escapeHtml(u.email)}</td>
            <td><span class="role-badge role-${u.role.toLowerCase()}">${u.role}</span></td>
            <td>
              <span class="badge ${u.active ? 'badge-success' : 'badge-danger'}">
                ${u.active ? 'Active' : 'Inactive'}
              </span>
            </td>
            <td class="text-sm text-muted">${window.UI.formatDateTime(u.createdAt)}</td>
            <td>
              <button class="btn btn-outline btn-sm btn-inspect-user" data-uid="${u.userId}">
                Manage &amp; Controls
              </button>
            </td>
          </tr>
        `;
      });

      container.innerHTML = `
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width: 70px;">ID</th>
                <th>Display Name</th>
                <th>Email Address</th>
                <th>Role</th>
                <th>Status</th>
                <th>Created At</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              ${rowsHtml}
            </tbody>
          </table>
        </div>
      `;

      // Bind inspect user buttons
      container.querySelectorAll('.btn-inspect-user').forEach(btn => {
        btn.addEventListener('click', () => {
          openUserControlsModal(btn.dataset.uid);
        });
      });

      // Pagination
      if (pagination && data.totalPages > 1) {
        pagination.innerHTML = `
          <div class="pagination">
            <button class="btn btn-outline btn-sm" id="u-prev" ${data.page <= 1 ? 'disabled' : ''}>&laquo; Prev</button>
            <span class="page-indicator">Page ${data.page} of ${data.totalPages} (${data.total} users)</span>
            <button class="btn btn-outline btn-sm" id="u-next" ${data.page >= data.totalPages ? 'disabled' : ''}>Next &raquo;</button>
          </div>
        `;
        document.getElementById('u-prev').addEventListener('click', () => {
          if (userSearchParams.page > 1) { userSearchParams.page--; loadAdminUsers(); }
        });
        document.getElementById('u-next').addEventListener('click', () => {
          if (userSearchParams.page < data.totalPages) { userSearchParams.page++; loadAdminUsers(); }
        });
      } else if (pagination) {
        pagination.innerHTML = '';
      }

    } catch (err) {
      container.innerHTML = `<div class="alert alert-error">${window.UI.escapeHtml(err.message)}</div>`;
    }
  }

  async function openUserControlsModal(userId) {
    const modal = window.UI.showModal({
      title: 'User Management & Controls',
      contentHtml: '<div class="loading-container py-4"><div class="spinner-small"></div> Loading user details...</div>'
    });

    try {
      const data = await window.API.admin.getUser(userId);
      const user = data.user;

      const bodyHtml = `
        <div class="user-controls-modal-body">
          <div class="card p-3 mb-3 bg-surface">
            <div class="d-flex justify-between align-center mb-2">
              <h3 class="font-bold text-navy mb-0">${window.UI.escapeHtml(user.displayName)}</h3>
              <span class="role-badge role-${user.role.toLowerCase()}">${user.role}</span>
            </div>
            <p class="text-sm text-muted mb-1">${window.UI.escapeHtml(user.email)} &bull; User ID #${user.userId}</p>
            <p class="text-sm mb-0">Status: <span class="badge ${user.active ? 'badge-success' : 'badge-danger'}">${user.active ? 'Active' : 'Inactive'}</span></p>
          </div>

          <!-- Relationship Constraints Overview -->
          <div class="mb-3">
            <h4 class="font-bold text-navy mb-2">Active Database Relationships</h4>
            <div class="stats-grid mb-2" style="grid-template-columns: repeat(4, 1fr);">
              <div class="stat-card p-2 text-center">
                <span class="stat-number text-navy" style="font-size: 1.25rem;">${user.teamMemberships || 0}</span>
                <span class="stat-label text-xs">Team Memberships</span>
              </div>
              <div class="stat-card p-2 text-center">
                <span class="stat-number text-navy" style="font-size: 1.25rem;">${user.activeRegistrations || 0}</span>
                <span class="stat-label text-xs">Active Registrations</span>
              </div>
              <div class="stat-card p-2 text-center">
                <span class="stat-number text-navy" style="font-size: 1.25rem;">${user.ownedEvents || 0}</span>
                <span class="stat-label text-xs">Owned Events</span>
              </div>
              <div class="stat-card p-2 text-center">
                <span class="stat-number text-navy" style="font-size: 1.25rem;">${user.judgeAssignments || 0}</span>
                <span class="stat-label text-xs">Judge Assignments</span>
              </div>
            </div>
            <small class="text-xs text-muted">
              Note: Account policy protects referential integrity. Role changes will be blocked if active registrations, teams, owned events, or judge assignments exist.
            </small>
          </div>

          <!-- Role Change Form -->
          <div class="card p-3 mb-3">
            <h4 class="font-bold text-navy mb-2">Modify Account Role</h4>
            <div class="input-group">
              <select id="select-change-role" class="form-control form-control-sm">
                <option value="STUDENT" ${user.role === 'STUDENT' ? 'selected' : ''}>STUDENT</option>
                <option value="ORGANIZER" ${user.role === 'ORGANIZER' ? 'selected' : ''}>ORGANIZER</option>
                <option value="JUDGE" ${user.role === 'JUDGE' ? 'selected' : ''}>JUDGE</option>
                <option value="ADMIN" ${user.role === 'ADMIN' ? 'selected' : ''}>ADMIN</option>
              </select>
              <button type="button" id="btn-update-role" class="btn btn-primary btn-sm">Update Role</button>
            </div>
          </div>

          <!-- Activation Toggle -->
          <div class="card p-3">
            <h4 class="font-bold text-navy mb-2">Account Activation State</h4>
            <p class="text-xs text-muted mb-2">
              ${user.active ? 'Deactivating prevents login and invalidates existing sessions. Existing data is retained.' : 'Reactivating restores login capability with existing credentials.'}
            </p>
            <button type="button" id="btn-toggle-active" class="btn ${user.active ? 'btn-danger' : 'btn-success'} btn-sm">
              ${user.active ? 'Deactivate Account' : 'Reactivate Account'}
            </button>
          </div>
        </div>
      `;

      document.querySelector('#modal-content .modal-body').innerHTML = bodyHtml;

      // Bind Update Role
      const updateRoleBtn = document.getElementById('btn-update-role');
      updateRoleBtn.addEventListener('click', async () => {
        const newRole = document.getElementById('select-change-role').value;
        if (newRole === user.role) {
          window.UI.showToast('User already has this role.', 'info');
          return;
        }

        const confirmed = await window.UI.confirmDialog({
          title: 'Confirm Role Mutation',
          message: `Change role of "${user.displayName}" from ${user.role} to ${newRole}? This action is audited.`,
          confirmText: 'Change Role',
          confirmType: 'warning'
        });
        if (!confirmed) return;

        window.UI.setButtonLoading(updateRoleBtn, true, 'Updating...');
        try {
          await window.API.admin.updateRole(userId, newRole);
          window.UI.showToast(`Role updated to ${newRole} successfully.`, 'success');
          modal.close();
          loadAdminUsers();
        } catch (err) {
          window.UI.showToast(err.message, 'error');
          window.UI.setButtonLoading(updateRoleBtn, false);
        }
      });

      // Bind Toggle Active
      const toggleActiveBtn = document.getElementById('btn-toggle-active');
      toggleActiveBtn.addEventListener('click', async () => {
        const targetState = !user.active;

        const confirmed = await window.UI.confirmDialog({
          title: targetState ? 'Reactivate Account' : 'Deactivate Account',
          message: targetState
            ? `Reactivate account for "${user.displayName}"?`
            : `Deactivate account for "${user.displayName}"? The user will be unable to log in.`,
          confirmText: targetState ? 'Reactivate' : 'Deactivate',
          confirmType: targetState ? 'primary' : 'danger'
        });
        if (!confirmed) return;

        window.UI.setButtonLoading(toggleActiveBtn, true, 'Updating...');
        try {
          await window.API.admin.updateStatus(userId, targetState);
          window.UI.showToast(`User account is now ${targetState ? 'active' : 'inactive'}.`, 'success');
          modal.close();
          loadAdminUsers();
        } catch (err) {
          window.UI.showToast(err.message, 'error');
          window.UI.setButtonLoading(toggleActiveBtn, false);
        }
      });

    } catch (err) {
      document.querySelector('#modal-content .modal-body').innerHTML = `
        <div class="alert alert-error">${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  // --- Platform Events View ---
  async function renderAdminEvents() {
    const main = document.getElementById('app-main');
    if (!main) return;

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">Platform Events (All Lifecycles)</h1>
            <p class="text-muted">Complete administrator catalog of all platform events, including private drafts.</p>
          </div>
        </div>

        <div id="admin-events-list-container" class="card">
          <div class="loading-container py-5"><div class="spinner"></div><p>Loading events...</p></div>
        </div>
      </div>
    `;

    const container = document.getElementById('admin-events-list-container');

    try {
      const data = await window.API.admin.getEvents({ pageSize: 50 });
      const events = data.events || [];

      if (events.length === 0) {
        container.innerHTML = window.UI.renderEmptyState('No Events Found', 'No events registered on the platform.');
        return;
      }

      let rowsHtml = '';
      events.forEach(ev => {
        rowsHtml += `
          <tr>
            <td class="font-bold text-navy">#${ev.eventId}</td>
            <td><span class="event-code font-bold">${window.UI.escapeHtml(ev.eventCode)}</span></td>
            <td><strong>${window.UI.escapeHtml(ev.eventName)}</strong></td>
            <td>${window.UI.renderModeBadge(ev.participationMode)}</td>
            <td>${window.UI.renderStatusBadge(ev.eventStatus)}</td>
            <td>${ev.confirmedCount || 0} / ${ev.capacity || 'Unlimited'}</td>
            <td class="text-sm text-muted">${window.UI.formatDateTime(ev.startsAt)}</td>
            <td>
              ${ev.eventStatus === 'DRAFT' ? '<span class="text-muted text-xs">Private Draft</span>' : `<a href="#/events/${ev.eventId}" class="btn btn-outline btn-sm">Public View</a>`}
            </td>
          </tr>
        `;
      });

      container.innerHTML = `
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width: 70px;">ID</th>
                <th>Code</th>
                <th>Event Name</th>
                <th>Mode</th>
                <th>Status</th>
                <th>Confirmed / Cap</th>
                <th>Starts At</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              ${rowsHtml}
            </tbody>
          </table>
        </div>
      `;
    } catch (err) {
      container.innerHTML = `<div class="alert alert-error">${window.UI.escapeHtml(err.message)}</div>`;
    }
  }

  // --- Platform Registrations View ---
  async function renderAdminRegistrations() {
    const main = document.getElementById('app-main');
    if (!main) return;

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">Platform Registrations</h1>
            <p class="text-muted">Platform-wide participant and team registration tracking.</p>
          </div>
        </div>

        <div id="admin-reg-list-container" class="card">
          <div class="loading-container py-5"><div class="spinner"></div><p>Loading registrations...</p></div>
        </div>
      </div>
    `;

    const container = document.getElementById('admin-reg-list-container');

    try {
      const data = await window.API.admin.getRegistrations({ pageSize: 50 });
      const regs = data.registrations || [];

      if (regs.length === 0) {
        container.innerHTML = window.UI.renderEmptyState('No Registrations', 'No registrations on the platform.');
        return;
      }

      let rowsHtml = '';
      regs.forEach(r => {
        const subjectName = r.registrationType === 'TEAM'
          ? `Team: <strong>${window.UI.escapeHtml(r.teamName || 'Team #' + r.teamId)}</strong>`
          : window.UI.escapeHtml(r.participantName || 'User #' + r.participantUserId);

        rowsHtml += `
          <tr>
            <td class="font-bold text-navy">#${r.registrationId}</td>
            <td>
              <span class="font-bold">${window.UI.escapeHtml(r.eventName || r.eventCode)}</span>
              <span class="d-block text-xs text-muted">Event #${r.eventId}</span>
            </td>
            <td>${subjectName}</td>
            <td>${window.UI.renderModeBadge(r.registrationType)}</td>
            <td>${window.UI.renderStatusBadge(r.status)}</td>
            <td class="text-sm text-muted">${window.UI.formatDateTime(r.registeredAt)}</td>
          </tr>
        `;
      });

      container.innerHTML = `
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width: 80px;">Reg ID</th>
                <th>Event</th>
                <th>Subject</th>
                <th>Type</th>
                <th>Status</th>
                <th>Registered At</th>
              </tr>
            </thead>
            <tbody>
              ${rowsHtml}
            </tbody>
          </table>
        </div>
      `;
    } catch (err) {
      container.innerHTML = `<div class="alert alert-error">${window.UI.escapeHtml(err.message)}</div>`;
    }
  }

  // --- Audit History View ---
  async function renderAdminAudit() {
    const main = document.getElementById('app-main');
    if (!main) return;

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">Administrator Audit History</h1>
            <p class="text-muted">Immutable transactional audit record of all user role mutations and activation toggles.</p>
          </div>
        </div>

        <div id="admin-audit-table-container" class="card">
          <div class="loading-container py-5"><div class="spinner"></div><p>Loading audit logs...</p></div>
        </div>
      </div>
    `;

    const container = document.getElementById('admin-audit-table-container');

    try {
      const data = await window.API.admin.getAudit({ pageSize: 50 });
      const logs = data.audit || [];

      if (logs.length === 0) {
        container.innerHTML = window.UI.renderEmptyState('No Audit Logs', 'No administrative mutations recorded.');
        return;
      }

      let rowsHtml = '';
      logs.forEach(log => {
        let actionBadge = 'badge-secondary';
        if (log.action === 'ROLE_CHANGE') actionBadge = 'badge-primary';
        else if (log.action === 'ACTIVATE') actionBadge = 'badge-success';
        else if (log.action === 'DEACTIVATE') actionBadge = 'badge-danger';

        rowsHtml += `
          <tr>
            <td class="font-bold text-navy">#${log.auditId}</td>
            <td>
              <strong>${window.UI.escapeHtml(log.actorDisplayName)}</strong>
              <span class="text-xs text-muted d-block">ID #${log.actorUserId}</span>
            </td>
            <td>
              <strong>${window.UI.escapeHtml(log.targetDisplayName)}</strong>
              <span class="text-xs text-muted d-block">ID #${log.targetUserId}</span>
            </td>
            <td><span class="badge ${actionBadge}">${log.action}</span></td>
            <td>
              <span class="text-danger font-medium">${window.UI.escapeHtml(log.previousValue)}</span>
              &rarr;
              <span class="text-success font-medium">${window.UI.escapeHtml(log.newValue)}</span>
            </td>
            <td class="text-sm text-muted">${window.UI.formatDateTime(log.createdAt)}</td>
          </tr>
        `;
      });

      container.innerHTML = `
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width: 80px;">Audit ID</th>
                <th>Actor (Admin)</th>
                <th>Target Account</th>
                <th>Action</th>
                <th>State Change</th>
                <th>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              ${rowsHtml}
            </tbody>
          </table>
        </div>
      `;
    } catch (err) {
      container.innerHTML = `<div class="alert alert-error">${window.UI.escapeHtml(err.message)}</div>`;
    }
  }

  window.Views = window.Views || {};
  window.Views.AdminDashboard = renderDashboard;
  window.Views.AdminUsers = renderAdminUsers;
  window.Views.AdminEvents = renderAdminEvents;
  window.Views.AdminRegistrations = renderAdminRegistrations;
  window.Views.AdminAudit = renderAdminAudit;
})(window);
