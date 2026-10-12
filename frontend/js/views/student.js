/**
 * CampusConnect Student Views
 * Dashboard, profile, team management, invitations, registrations list, and cancellation flow.
 */

(function (window) {
  'use strict';

  // --- Student Dashboard ---
  async function renderDashboard() {
    const main = document.getElementById('app-main');
    if (!main) return;

    const user = window.Auth.getUser();

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">Student Dashboard</h1>
            <p class="text-muted">Welcome back, <strong>${window.UI.escapeHtml(user.displayName)}</strong>!</p>
          </div>
          <div>
            <a href="#/events" class="btn btn-primary">Browse Events</a>
          </div>
        </div>

        <div class="row gap-4 mb-4">
          <!-- Profile Card -->
          <div class="col-4">
            <div class="card p-4 h-100">
              <h2 class="card-title text-navy mb-3">Your Student Profile</h2>
              <div class="detail-block mb-3">
                <span class="detail-label">Your User ID</span>
                <span class="detail-value font-bold text-navy" style="font-size: 1.25rem;">
                  #${user.userId}
                </span>
                <small class="text-muted d-block mt-1">Share this ID with team owners to receive team invitations.</small>
              </div>
              <div class="detail-block mb-2">
                <span class="detail-label">Display Name</span>
                <span class="detail-value font-medium">${window.UI.escapeHtml(user.displayName)}</span>
              </div>
              <div class="detail-block mb-2">
                <span class="detail-label">Email Address</span>
                <span class="detail-value">${window.UI.escapeHtml(user.email)}</span>
              </div>
              <div class="detail-block">
                <span class="detail-label">Role</span>
                <span class="detail-value">${window.UI.renderStatusBadge(user.role)}</span>
              </div>
            </div>
          </div>

          <!-- Quick Stats / Invitations Alert -->
          <div class="col-8">
            <div class="card p-4 h-100">
              <h2 class="card-title text-navy mb-3">Participation Overview</h2>
              <div id="student-overview-loading" class="loading-container py-4">
                <div class="spinner-small"></div>
                <p>Loading participation summary...</p>
              </div>
              <div id="student-overview-content" style="display: none;">
                <div class="stats-grid mb-4">
                  <div class="stat-card">
                    <span class="stat-number text-navy" id="stat-reg-count">0</span>
                    <span class="stat-label">Active Registrations</span>
                  </div>
                  <div class="stat-card">
                    <span class="stat-number text-navy" id="stat-team-count">0</span>
                    <span class="stat-label">My Teams</span>
                  </div>
                  <div class="stat-card">
                    <span class="stat-number text-warning" id="stat-invite-count">0</span>
                    <span class="stat-label">Pending Invites</span>
                  </div>
                </div>

                <div id="dashboard-invitations-banner"></div>

                <div class="d-flex gap-2 flex-wrap mt-3">
                  <a href="#/student/registrations" class="btn btn-outline">View My Registrations &rarr;</a>
                  <a href="#/student/teams" class="btn btn-outline">Manage Teams &amp; Invitations &rarr;</a>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;

    // Load data
    try {
      const [regsData, teamsData, invitesData] = await Promise.all([
        window.API.student.getRegistrations(1, 100),
        window.API.student.getTeams(),
        window.API.student.getInvitations()
      ]);

      const activeRegs = (regsData.registrations || []).filter(r => r.registrationStatus !== 'CANCELLED');
      const teams = teamsData.teams || [];
      const invites = invitesData.invitations || [];

      document.getElementById('stat-reg-count').textContent = activeRegs.length;
      document.getElementById('stat-team-count').textContent = teams.length;
      document.getElementById('stat-invite-count').textContent = invites.length;

      const banner = document.getElementById('dashboard-invitations-banner');
      if (invites.length > 0) {
        banner.innerHTML = `
          <div class="alert alert-warning mb-3">
            <strong>You have ${invites.length} pending team invitation(s)!</strong>
            <a href="#/student/teams" class="font-medium ml-2 text-navy">Review invitations now &rarr;</a>
          </div>
        `;
      } else {
        banner.innerHTML = '';
      }

      document.getElementById('student-overview-loading').style.display = 'none';
      document.getElementById('student-overview-content').style.display = 'block';
    } catch (err) {
      document.getElementById('student-overview-loading').innerHTML = `
        <div class="alert alert-error">Failed to load overview: ${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  // --- Student Registrations ---
  async function renderRegistrations(params, queryParams) {
    const main = document.getElementById('app-main');
    if (!main) return;

    const page = parseInt(queryParams.get('page'), 10) || 1;

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">My Registrations</h1>
            <p class="text-muted">Track your individual and team event registrations, statuses, and history.</p>
          </div>
          <div>
            <a href="#/events" class="btn btn-primary">+ Register for Events</a>
          </div>
        </div>

        <div class="card p-3 mb-3">
          <p class="text-sm text-muted mb-0">
            <strong>Registration Status Policy:</strong> 
            <span class="badge badge-success">CONFIRMED</span> entries consume event capacity. 
            <span class="badge badge-warning">WAITLISTED</span> entries join the queue and are promoted automatically when a confirmed spot is cancelled. 
            <span class="badge badge-danger">CANCELLED</span> entries are terminal and cannot be re-registered.
          </p>
        </div>

        <div id="registrations-list-container" class="card">
          <div class="loading-container py-5">
            <div class="spinner"></div>
            <p>Loading your registrations...</p>
          </div>
        </div>

        <div id="registrations-pagination" class="pagination-wrapper mt-4"></div>
      </div>
    `;

    const container = document.getElementById('registrations-list-container');
    const paginationContainer = document.getElementById('registrations-pagination');

    try {
      const data = await window.API.student.getRegistrations(page, 20);
      const list = data.registrations || [];

      if (list.length === 0) {
        container.innerHTML = window.UI.renderEmptyState(
          'No Registrations Found',
          'You have not registered for any events yet. Explore upcoming competitions in the event catalog!',
          '<a href="#/events" class="btn btn-primary">Browse Events</a>'
        );
        return;
      }

      let rowsHtml = '';
      list.forEach(r => {
        const canCancel = r.registrationStatus !== 'CANCELLED';
        const teamInfo = r.registrationType === 'TEAM' ? `Team: <strong>${window.UI.escapeHtml(r.teamName || 'Team #' + r.teamId)}</strong>` : 'Individual Entry';

        rowsHtml += `
          <tr>
            <td>
              <a href="#/events/${r.eventId}" class="font-bold text-navy">
                ${window.UI.escapeHtml(r.eventName || r.eventCode)}
              </a>
              <span class="d-block text-xs text-muted">${window.UI.escapeHtml(r.eventCode)}</span>
            </td>
            <td>
              ${window.UI.renderModeBadge(r.registrationType)}
              <span class="d-block text-xs text-muted mt-1">${teamInfo}</span>
            </td>
            <td>
              ${window.UI.renderStatusBadge(r.registrationStatus)}
            </td>
            <td class="text-sm text-muted">
              ${window.UI.formatDateTime(r.registeredAt)}
            </td>
            <td>
              <div class="btn-group">
                <a href="#/events/${r.eventId}" class="btn btn-outline btn-sm">Event Details</a>
                ${canCancel ? `
                  <button class="btn btn-danger btn-sm btn-cancel-reg" data-id="${r.registrationId}" data-name="${window.UI.escapeHtml(r.eventName || r.eventCode)}">
                    Cancel
                  </button>
                ` : '<span class="text-muted text-xs">Cancelled</span>'}
              </div>
            </td>
          </tr>
        `;
      });

      container.innerHTML = `
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th>Event</th>
                <th>Type / Team</th>
                <th>Status</th>
                <th>Registered At</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              ${rowsHtml}
            </tbody>
          </table>
        </div>
      `;

      // Cancel button listeners
      container.querySelectorAll('.btn-cancel-reg').forEach(btn => {
        btn.addEventListener('click', async () => {
          const regId = btn.dataset.id;
          const eventName = btn.dataset.name;

          const confirmed = await window.UI.confirmDialog({
            title: 'Cancel Event Registration',
            message: `Are you sure you want to cancel your registration for "${eventName}"? This action cannot be undone, and re-registration will not be permitted.`,
            confirmText: 'Yes, Cancel Registration',
            confirmType: 'danger'
          });

          if (!confirmed) return;

          window.UI.setButtonLoading(btn, true, 'Cancelling...');
          try {
            await window.API.student.cancelRegistration(regId);
            window.UI.showToast('Registration cancelled successfully.', 'success');
            renderRegistrations(params, queryParams);
          } catch (err) {
            window.UI.showToast(err.message, 'error');
            window.UI.setButtonLoading(btn, false);
          }
        });
      });

    } catch (err) {
      container.innerHTML = `
        <div class="p-4 text-center">
          <div class="alert alert-error">Failed to load registrations: ${window.UI.escapeHtml(err.message)}</div>
        </div>
      `;
    }
  }

  // --- Student Teams & Invitations ---
  async function renderTeams() {
    const main = document.getElementById('app-main');
    if (!main) return;

    const user = window.Auth.getUser();

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">Teams &amp; Invitations</h1>
            <p class="text-muted">Create teams (2–5 members), send invitations by User ID, and manage team rosters.</p>
          </div>
        </div>

        <!-- Pending Invitations Section -->
        <div class="card p-4 mb-4" id="invitations-section">
          <h2 class="card-title text-navy mb-3">Pending Invitations Received</h2>
          <div id="invitations-container">
            <div class="loading-container py-3">
              <div class="spinner-small"></div>
              <p>Checking pending invitations...</p>
            </div>
          </div>
        </div>

        <div class="row gap-4">
          <!-- Create Team Form -->
          <div class="col-4">
            <div class="card p-4 mb-4">
              <h2 class="card-title text-navy mb-3">+ Create New Team</h2>
              <form id="create-team-form">
                <div class="form-group mb-3">
                  <label for="create-team-name" class="form-label">Team Name</label>
                  <input type="text" id="create-team-name" class="form-control" maxlength="120" required placeholder="e.g. CyberKnights">
                  <small class="form-text text-muted">1–120 characters. You will automatically become the owner and first member.</small>
                </div>
                <button type="submit" id="btn-create-team-submit" class="btn btn-primary btn-block">
                  Create Team
                </button>
              </form>
            </div>

            <div class="card p-3 bg-surface">
              <h4 class="font-bold text-navy mb-2">Team Rules &amp; Policies</h4>
              <ul class="text-sm text-muted pl-3 mb-0">
                <li>Team size must be between <strong>2 and 5 active students</strong>.</li>
                <li>The team owner cannot leave or be removed; ownership cannot be transferred.</li>
                <li>Roster is <strong>permanently locked</strong> once the team registers for any event.</li>
                <li>Invite members using their numeric User ID.</li>
              </ul>
            </div>
          </div>

          <!-- Teams List -->
          <div class="col-8">
            <div class="card p-4">
              <h2 class="card-title text-navy mb-3">Your Teams</h2>
              <div id="teams-list-container">
                <div class="loading-container py-4">
                  <div class="spinner"></div>
                  <p>Loading your teams...</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;

    // Bind create team form
    const createForm = document.getElementById('create-team-form');
    const createBtn = document.getElementById('btn-create-team-submit');
    createForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const teamName = document.getElementById('create-team-name').value.trim();
      if (!teamName) return;

      window.UI.setButtonLoading(createBtn, true, 'Creating...');
      try {
        await window.API.student.createTeam(teamName);
        window.UI.showToast(`Team "${teamName}" created successfully!`, 'success');
        createForm.reset();
        loadTeamsList();
      } catch (err) {
        window.UI.showToast(err.message, 'error');
      } finally {
        window.UI.setButtonLoading(createBtn, false);
      }
    });

    loadInvitations();
    loadTeamsList();
  }

  async function loadInvitations() {
    const container = document.getElementById('invitations-container');
    if (!container) return;

    try {
      const data = await window.API.student.getInvitations();
      const invites = data.invitations || [];

      if (invites.length === 0) {
        container.innerHTML = `
          <p class="text-muted mb-0">You have no pending team invitations at this time.</p>
        `;
        return;
      }

      let cardsHtml = '<div class="invites-grid">';
      invites.forEach(inv => {
        cardsHtml += `
          <div class="invite-card">
            <div>
              <h4 class="font-bold text-navy mb-1">${window.UI.escapeHtml(inv.teamName)}</h4>
              <p class="text-sm text-muted mb-2">Invited by User #${inv.ownerUserId} &bull; ${window.UI.formatDate(inv.createdAt)}</p>
            </div>
            <div class="btn-group">
              <button class="btn btn-success btn-sm btn-accept-invite" data-id="${inv.invitationId}">
                Accept
              </button>
              <button class="btn btn-outline btn-sm btn-decline-invite" data-id="${inv.invitationId}">
                Decline
              </button>
            </div>
          </div>
        `;
      });
      cardsHtml += '</div>';

      container.innerHTML = cardsHtml;

      // Bind accept / decline
      container.querySelectorAll('.btn-accept-invite').forEach(btn => {
        btn.addEventListener('click', async () => {
          const invId = btn.dataset.id;
          window.UI.setButtonLoading(btn, true, 'Accepting...');
          try {
            await window.API.student.acceptInvitation(invId);
            window.UI.showToast('Joined team successfully!', 'success');
            loadInvitations();
            loadTeamsList();
          } catch (err) {
            window.UI.showToast(err.message, 'error');
            window.UI.setButtonLoading(btn, false);
          }
        });
      });

      container.querySelectorAll('.btn-decline-invite').forEach(btn => {
        btn.addEventListener('click', async () => {
          const invId = btn.dataset.id;
          window.UI.setButtonLoading(btn, true, 'Declining...');
          try {
            await window.API.student.declineInvitation(invId);
            window.UI.showToast('Invitation declined.', 'info');
            loadInvitations();
          } catch (err) {
            window.UI.showToast(err.message, 'error');
            window.UI.setButtonLoading(btn, false);
          }
        });
      });

    } catch (err) {
      container.innerHTML = `
        <div class="alert alert-error">Failed to load invitations: ${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  async function loadTeamsList() {
    const container = document.getElementById('teams-list-container');
    if (!container) return;

    try {
      const data = await window.API.student.getTeams();
      const teams = data.teams || [];
      const user = window.Auth.getUser();

      if (teams.length === 0) {
        container.innerHTML = window.UI.renderEmptyState(
          'No Teams Joined',
          'You do not belong to any teams yet. Create a new team on the left or wait for an invitation.'
        );
        return;
      }

      let html = '<div class="teams-list">';
      teams.forEach(t => {
        const isOwner = t.ownerUserId === user.userId;
        html += `
          <div class="team-item-card card p-3 mb-3">
            <div class="d-flex justify-between align-center flex-wrap gap-2">
              <div>
                <h3 class="font-bold text-navy mb-1">${window.UI.escapeHtml(t.teamName)}</h3>
                <span class="badge ${isOwner ? 'badge-primary' : 'badge-secondary'} mr-2">
                  ${isOwner ? 'Owner' : 'Member'}
                </span>
                <span class="text-sm text-muted">
                  ${t.memberCount} / 5 members
                </span>
              </div>
              <div class="btn-group">
                <button class="btn btn-outline btn-sm btn-manage-roster" data-id="${t.teamId}">
                  Manage Roster &amp; Details
                </button>
              </div>
            </div>
          </div>
        `;
      });
      html += '</div>';

      container.innerHTML = html;

      // Bind manage roster buttons
      container.querySelectorAll('.btn-manage-roster').forEach(btn => {
        btn.addEventListener('click', () => {
          openTeamDetailsModal(btn.dataset.id);
        });
      });

    } catch (err) {
      container.innerHTML = `
        <div class="alert alert-error">Failed to load teams: ${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  async function openTeamDetailsModal(teamId) {
    const user = window.Auth.getUser();

    const modal = window.UI.showModal({
      title: 'Team Details & Roster',
      contentHtml: '<div class="loading-container py-4"><div class="spinner-small"></div> Loading team...</div>'
    });

    try {
      const data = await window.API.student.getTeam(teamId);
      const team = data.team;
      const isOwner = team.ownerUserId === user.userId;
      const members = team.members || [];

      let membersListHtml = '<ul class="roster-list">';
      members.forEach(m => {
        const isMemberOwner = m.userId === team.ownerUserId;
        membersListHtml += `
          <li class="roster-item">
            <div>
              <strong class="text-navy">${window.UI.escapeHtml(m.displayName)}</strong>
              <span class="text-xs text-muted ml-1">(User #${m.userId})</span>
              ${isMemberOwner ? '<span class="badge badge-primary text-xs ml-2">Owner</span>' : ''}
            </div>
            ${isOwner && !isMemberOwner ? `
              <button class="btn btn-danger btn-xs btn-remove-member" data-uid="${m.userId}" data-name="${window.UI.escapeHtml(m.displayName)}">
                Remove
              </button>
            ` : ''}
          </li>
        `;
      });
      membersListHtml += '</ul>';

      const bodyHtml = `
        <div class="team-modal-body">
          <div class="d-flex justify-between align-center mb-3">
            <h3 class="font-bold text-navy" id="modal-team-name">${window.UI.escapeHtml(team.teamName)}</h3>
            <span class="badge ${isOwner ? 'badge-primary' : 'badge-secondary'}">${isOwner ? 'You are Owner' : 'Member'}</span>
          </div>

          <!-- Rename section (Owner only) -->
          ${isOwner ? `
            <div class="rename-section card p-3 mb-3 bg-surface">
              <label class="form-label text-sm">Rename Team</label>
              <div class="input-group">
                <input type="text" id="rename-team-input" class="form-control form-control-sm" value="${window.UI.escapeHtml(team.teamName)}" maxlength="120">
                <button type="button" id="btn-rename-team" class="btn btn-outline btn-sm">Save Name</button>
              </div>
              <small class="text-xs text-muted">Note: Renaming is blocked once registered for any event.</small>
            </div>
          ` : ''}

          <!-- Roster section -->
          <div class="mb-3">
            <div class="d-flex justify-between align-center mb-2">
              <h4 class="font-medium text-navy">Current Roster (${members.length}/5)</h4>
              <span class="text-xs ${members.length >= 2 ? 'text-success' : 'text-warning'} font-medium">
                ${members.length >= 2 ? '✓ Minimum size met' : '⚠ Need at least 2 members'}
              </span>
            </div>
            ${membersListHtml}
          </div>

          <!-- Invite section (Owner only, if < 5 members) -->
          ${isOwner && members.length < 5 ? `
            <div class="invite-member-section card p-3 mb-3 bg-surface">
              <h4 class="font-medium text-navy mb-2">Invite Student Member</h4>
              <div class="input-group">
                <input type="number" id="invite-uid-input" class="form-control form-control-sm" placeholder="Student User ID (e.g. 15)" min="1">
                <button type="button" id="btn-send-invite" class="btn btn-primary btn-sm">Send Invite</button>
              </div>
              <small class="text-xs text-muted mt-1 d-block">
                Enter the student's numeric ID. Student accounts are shown their User ID on their dashboard.
              </small>
            </div>
          ` : ''}

          <!-- Non-owner leave button -->
          ${!isOwner ? `
            <div class="pt-3 border-top text-right">
              <button type="button" id="btn-leave-team" class="btn btn-danger btn-sm">
                Leave Team
              </button>
            </div>
          ` : ''}
        </div>
      `;

      document.querySelector('#modal-content .modal-body').innerHTML = bodyHtml;

      // Bind Rename
      if (isOwner) {
        const renameBtn = document.getElementById('btn-rename-team');
        renameBtn.addEventListener('click', async () => {
          const newName = document.getElementById('rename-team-input').value.trim();
          if (!newName) return;
          window.UI.setButtonLoading(renameBtn, true, 'Saving...');
          try {
            await window.API.student.renameTeam(teamId, newName);
            window.UI.showToast('Team renamed successfully.', 'success');
            document.getElementById('modal-team-name').textContent = newName;
            loadTeamsList();
          } catch (err) {
            window.UI.showToast(err.message, 'error');
          } finally {
            window.UI.setButtonLoading(renameBtn, false);
          }
        });

        // Bind Invite
        const inviteBtn = document.getElementById('btn-send-invite');
        if (inviteBtn) {
          inviteBtn.addEventListener('click', async () => {
            const uidInput = document.getElementById('invite-uid-input');
            const targetUid = uidInput.value.trim();
            if (!targetUid) return;

            window.UI.setButtonLoading(inviteBtn, true, 'Inviting...');
            try {
              await window.API.student.inviteMember(teamId, targetUid);
              window.UI.showToast(`Invitation sent to User #${targetUid}!`, 'success');
              uidInput.value = '';
            } catch (err) {
              window.UI.showToast(err.message, 'error');
            } finally {
              window.UI.setButtonLoading(inviteBtn, false);
            }
          });
        }

        // Bind Remove Member
        document.querySelectorAll('.btn-remove-member').forEach(rmBtn => {
          rmBtn.addEventListener('click', async () => {
            const memberUid = rmBtn.dataset.uid;
            const memberName = rmBtn.dataset.name;

            const confirmed = await window.UI.confirmDialog({
              title: 'Remove Team Member',
              message: `Remove "${memberName}" (User #${memberUid}) from team "${team.teamName}"?`,
              confirmText: 'Remove Member',
              confirmType: 'danger'
            });
            if (!confirmed) return;

            try {
              await window.API.student.removeMember(teamId, memberUid);
              window.UI.showToast(`${memberName} removed from team.`, 'info');
              modal.close();
              loadTeamsList();
            } catch (err) {
              window.UI.showToast(err.message, 'error');
            }
          });
        });
      }

      // Bind Leave Team (Non-owner)
      if (!isOwner) {
        const leaveBtn = document.getElementById('btn-leave-team');
        if (leaveBtn) {
          leaveBtn.addEventListener('click', async () => {
            const confirmed = await window.UI.confirmDialog({
              title: 'Leave Team',
              message: `Are you sure you want to leave "${team.teamName}"?`,
              confirmText: 'Leave Team',
              confirmType: 'danger'
            });
            if (!confirmed) return;

            try {
              await window.API.student.leaveTeam(teamId);
              window.UI.showToast(`You left ${team.teamName}.`, 'info');
              modal.close();
              loadTeamsList();
            } catch (err) {
              window.UI.showToast(err.message, 'error');
            }
          });
        }
      }

    } catch (err) {
      document.querySelector('#modal-content .modal-body').innerHTML = `
        <div class="alert alert-error">Failed to load team details: ${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  window.Views = window.Views || {};
  window.Views.StudentDashboard = renderDashboard;
  window.Views.StudentRegistrations = renderRegistrations;
  window.Views.StudentTeams = renderTeams;
})(window);
