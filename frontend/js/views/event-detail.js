/**
 * CampusConnect Event Detail View
 * Event specifications, capacity, direct student registration flow, and published leaderboard.
 */

(function (window) {
  'use strict';

  async function renderEventDetail(params) {
    const main = document.getElementById('app-main');
    if (!main) return;

    const eventId = params.id;
    if (!eventId) {
      window.Router.navigate('/events');
      return;
    }

    main.innerHTML = `
      <div class="container py-4">
        <div class="mb-3">
          <a href="#/events" class="text-muted">&larr; Back to Events Catalog</a>
        </div>
        <div id="event-detail-content" class="loading-container py-5">
          <div class="spinner"></div>
          <p>Loading event specifications...</p>
        </div>
      </div>
    `;

    const container = document.getElementById('event-detail-content');

    try {
      const response = await window.API.events.getPublic(eventId);
      const event = response.event;
      const user = window.Auth.getUser();

      const isFull = event.capacity && (event.confirmedCount >= event.capacity);
      const capacityText = event.capacity
        ? `${event.confirmedCount || 0} / ${event.capacity} confirmed places`
        : `${event.confirmedCount || 0} entries confirmed (Unlimited)`;

      let regActionHtml = '';

      if (!user) {
        regActionHtml = `
          <div class="registration-cta-box">
            <h4>Ready to participate?</h4>
            <p class="text-muted">Sign in with your student account to register for this event.</p>
            <a href="#/login?redirect=${encodeURIComponent('/events/' + event.eventId)}" class="btn btn-primary">
              Log In to Register
            </a>
          </div>
        `;
      } else if (user.role === 'STUDENT') {
        if (event.eventStatus !== 'PUBLISHED' || !event.registrationOpen) {
          regActionHtml = `
            <div class="alert alert-warning">
              <strong>Registration Closed:</strong> This event is not currently accepting new registrations.
            </div>
          `;
        } else if (event.participationMode === 'INDIVIDUAL') {
          regActionHtml = `
            <div class="registration-cta-box">
              <h4>Individual Event Registration</h4>
              <p class="text-muted">
                ${isFull ? '<strong>Note:</strong> Capacity is currently full. Your registration will be placed on the waitlist.' : 'Places are available. Register now to secure your confirmed spot.'}
              </p>
              <button id="btn-register-individual" class="btn btn-primary btn-lg">
                ${isFull ? 'Join Waitlist (Individual)' : 'Confirm Individual Registration'}
              </button>
            </div>
          `;
        } else if (event.participationMode === 'TEAM') {
          regActionHtml = `
            <div class="registration-cta-box" id="team-reg-container">
              <h4>Team Event Registration</h4>
              <p class="text-muted">Team owners may register their eligible team (2–5 active student members).</p>
              <div id="team-select-wrapper" class="py-2">
                <div class="spinner-small"></div> Loading your eligible teams...
              </div>
            </div>
          `;
        }
      } else {
        regActionHtml = `
          <div class="alert alert-info">
            You are currently signed in as <strong>${user.role}</strong>. Public event registration is reserved for student accounts.
          </div>
        `;
      }

      container.innerHTML = `
        <div class="event-detail-header card p-4 mb-4">
          <div class="d-flex justify-between align-start flex-wrap gap-2">
            <div>
              <span class="event-code font-bold text-navy">${window.UI.escapeHtml(event.eventCode)}</span>
              <h1 class="page-title mt-1">${window.UI.escapeHtml(event.eventName)}</h1>
            </div>
            <div class="d-flex gap-2 align-center">
              ${window.UI.renderStatusBadge(event.eventStatus)}
              ${window.UI.renderModeBadge(event.participationMode)}
            </div>
          </div>

          <div class="event-detail-grid mt-4">
            <div class="detail-block">
              <span class="detail-label">Location / Venue</span>
              <span class="detail-value font-medium">${window.UI.escapeHtml(event.location || 'To be announced')}</span>
            </div>
            <div class="detail-block">
              <span class="detail-label">Capacity &amp; Status</span>
              <span class="detail-value font-medium ${isFull ? 'text-warning' : 'text-success'}">
                ${capacityText}
              </span>
            </div>
            <div class="detail-block">
              <span class="detail-label">Event Schedule</span>
              <span class="detail-value">
                ${window.UI.formatDateTime(event.startsAt)} &ndash; ${window.UI.formatDateTime(event.endsAt)}
              </span>
            </div>
            <div class="detail-block">
              <span class="detail-label">Registration Period</span>
              <span class="detail-value">
                ${window.UI.formatDateTime(event.registrationOpens)} &ndash; ${window.UI.formatDateTime(event.registrationCloses)}
              </span>
            </div>
          </div>
        </div>

        <div class="row gap-4 mb-4">
          <!-- Description & Rules -->
          <div class="col-8">
            <div class="card p-4 h-100">
              <h2 class="section-title mb-3">Event Overview</h2>
              <div class="event-description-text mb-4">
                ${window.UI.escapeHtml(event.description || 'No detailed description provided by the organizer.').replace(/\n/g, '<br>')}
              </div>

              <h3 class="section-subtitle mb-2">Participation Guidelines</h3>
              <ul class="guidelines-list">
                <li><strong>Mode:</strong> ${event.participationMode === 'TEAM' ? 'Teams of 2 to 5 active students. Team roster is locked upon first event registration.' : 'Individual entry. Each participant holds one registration spot.'}</li>
                <li><strong>Capacity:</strong> Each confirmed individual or team entry consumes one capacity spot. Once filled, subsequent valid registrations join the waitlist.</li>
                <li><strong>Cancellations:</strong> Participants may cancel before event commencement. Confirmed cancellations automatically promote the oldest eligible waitlisted entry.</li>
              </ul>
            </div>
          </div>

          <!-- Registration Action Box -->
          <div class="col-4">
            <div class="card p-4 h-100">
              <h3 class="section-title mb-3">Registration</h3>
              ${regActionHtml}
            </div>
          </div>
        </div>

        <!-- Official Results Section -->
        <div class="card p-4 mb-4" id="event-results-section">
          <h2 class="section-title mb-3">Official Results</h2>
          <div id="results-table-container">
            <div class="loading-container py-3">
              <div class="spinner-small"></div> Checking published results...
            </div>
          </div>
        </div>
      `;

      // Wire up registration action handlers
      if (user && user.role === 'STUDENT' && event.eventStatus === 'PUBLISHED' && event.registrationOpen) {
        if (event.participationMode === 'INDIVIDUAL') {
          const btnInd = document.getElementById('btn-register-individual');
          if (btnInd) {
            btnInd.addEventListener('click', async () => {
              const confirmed = await window.UI.confirmDialog({
                title: 'Confirm Registration',
                message: `Are you sure you want to register for "${event.eventName}"?`,
                confirmText: 'Register Now',
                confirmType: 'primary'
              });
              if (!confirmed) return;

              window.UI.setButtonLoading(btnInd, true, 'Registering...');
              try {
                const regRes = await window.API.student.registerIndividual(event.eventId);
                const status = regRes.registration.registrationStatus;
                if (status === 'CONFIRMED') {
                  window.UI.showToast('Registration confirmed successfully!', 'success');
                } else {
                  window.UI.showToast('Event is at capacity. You are placed on the waitlist!', 'warning');
                }
                window.Router.navigate('/student/registrations');
              } catch (err) {
                window.UI.showToast(err.message, 'error');
                window.UI.setButtonLoading(btnInd, false);
              }
            });
          }
        } else if (event.participationMode === 'TEAM') {
          loadTeamRegistrationOptions(event);
        }
      }

      // Load public results
      loadPublicResults(event.eventId);

    } catch (err) {
      container.innerHTML = `
        <div class="card p-4 text-center">
          <h2 class="text-danger">Event Not Found</h2>
          <p class="text-muted">${window.UI.escapeHtml(err.message || 'The requested event is not available or private.')}</p>
          <a href="#/events" class="btn btn-primary mt-3">Back to Catalog</a>
        </div>
      `;
    }
  }

  async function loadTeamRegistrationOptions(event) {
    const wrapper = document.getElementById('team-select-wrapper');
    if (!wrapper) return;

    try {
      const data = await window.API.student.getTeams();
      const user = window.Auth.getUser();
      // Filter to teams where user is the owner
      const ownedTeams = (data.teams || []).filter(t => t.ownerUserId === user.userId);

      if (ownedTeams.length === 0) {
        wrapper.innerHTML = `
          <div class="alert alert-warning mb-2">
            You do not own any teams. You must create a team (with 2–5 members) to register for team events.
          </div>
          <a href="#/student/teams" class="btn btn-outline btn-block">+ Create a Team</a>
        `;
        return;
      }

      wrapper.innerHTML = `
        <div class="form-group mb-3">
          <label for="select-team-for-reg" class="form-label">Select Your Team</label>
          <select id="select-team-for-reg" class="form-control">
            ${ownedTeams.map(t => `
              <option value="${t.teamId}">
                ${window.UI.escapeHtml(t.teamName)} (${t.memberCount} members)
              </option>
            `).join('')}
          </select>
        </div>
        <button id="btn-register-team" class="btn btn-primary btn-block">Register Team</button>
        <p class="text-muted text-sm mt-2 text-center">
          Need to invite members first? <a href="#/student/teams">Manage teams</a>
        </p>
      `;

      const regBtn = document.getElementById('btn-register-team');
      regBtn.addEventListener('click', async () => {
        const select = document.getElementById('select-team-for-reg');
        const teamId = select.value;
        const selectedTeam = ownedTeams.find(t => String(t.teamId) === String(teamId));

        const confirmed = await window.UI.confirmDialog({
          title: 'Register Team',
          message: `Register team "${selectedTeam.teamName}" for "${event.eventName}"?`,
          confirmText: 'Register Team',
          confirmType: 'primary'
        });
        if (!confirmed) return;

        window.UI.setButtonLoading(regBtn, true, 'Registering...');
        try {
          const regRes = await window.API.student.registerTeam(teamId, event.eventId);
          const status = regRes.registration.registrationStatus;
          if (status === 'CONFIRMED') {
            window.UI.showToast(`Team ${selectedTeam.teamName} confirmed successfully!`, 'success');
          } else {
            window.UI.showToast(`Capacity full. Team ${selectedTeam.teamName} is waitlisted!`, 'warning');
          }
          window.Router.navigate('/student/registrations');
        } catch (err) {
          window.UI.showToast(err.message, 'error');
          window.UI.setButtonLoading(regBtn, false);
        }
      });
    } catch (err) {
      wrapper.innerHTML = `
        <div class="alert alert-error">Failed to load teams: ${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  async function loadPublicResults(eventId) {
    const container = document.getElementById('results-table-container');
    if (!container) return;

    try {
      const data = await window.API.events.getPublicResults(eventId);
      const results = data.results || [];

      if (results.length === 0) {
        container.innerHTML = `
          <div class="text-muted py-3 text-center">
            Official results have not been finalized or published for this event yet.
          </div>
        `;
        return;
      }

      let rowsHtml = '';
      results.forEach(r => {
        const rankMedal = r.resultRank === 1 ? '🥇 ' : r.resultRank === 2 ? '🥈 ' : r.resultRank === 3 ? '🥉 ' : '';
        rowsHtml += `
          <tr>
            <td class="font-bold text-center">${rankMedal}#${r.resultRank}</td>
            <td class="font-medium">${window.UI.escapeHtml(r.registrantName)}</td>
            <td><span class="badge ${r.registrationType === 'TEAM' ? 'badge-team' : 'badge-individual'}">${r.registrationType}</span></td>
            <td class="font-bold text-navy">${Number(r.weightedScore).toFixed(2)}</td>
            <td>${r.criteriaScored} criteria</td>
          </tr>
        `;
      });

      container.innerHTML = `
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width: 80px;" class="text-center">Rank</th>
                <th>Participant / Team Name</th>
                <th>Type</th>
                <th>Weighted Score</th>
                <th>Criteria Evaluated</th>
              </tr>
            </thead>
            <tbody>
              ${rowsHtml}
            </tbody>
          </table>
        </div>
      `;
    } catch {
      // 404 means results not published yet
      container.innerHTML = `
        <div class="text-muted py-3 text-center">
          Official results have not been finalized or published for this event yet.
        </div>
      `;
    }
  }

  window.Views = window.Views || {};
  window.Views.EventDetail = renderEventDetail;
})(window);
