/**
 * CampusConnect Organizer Views
 * Dashboard, event creation/editing, lifecycle actions, registrations listing,
 * judges assignment, criteria configuration, scoring window, results preview, and finalization.
 */

(function (window) {
  'use strict';

  // Helper to convert ISO string to datetime-local input value
  function isoToInput(isoStr) {
    if (!isoStr) return '';
    try {
      const d = new Date(isoStr);
      if (isNaN(d.getTime())) return '';
      const pad = (n) => String(n).padStart(2, '0');
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    } catch {
      return '';
    }
  }

  // Helper to convert input datetime-local to ISO-8601 with local timezone offset
  function inputToIso(inputVal) {
    if (!inputVal) return null;
    const d = new Date(inputVal);
    if (isNaN(d.getTime())) return null;
    // Format with timezone offset e.g. 2026-10-15T09:00:00+05:30
    const pad = (n) => String(n).padStart(2, '0');
    const tzo = -d.getTimezoneOffset();
    const dif = tzo >= 0 ? '+' : '-';
    const tzHours = pad(Math.floor(Math.abs(tzo) / 60));
    const tzMins = pad(Math.abs(tzo) % 60);

    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00${dif}${tzHours}:${tzMins}`;
  }

  // --- Organizer Dashboard ---
  async function renderOrganizerDashboard() {
    const main = document.getElementById('app-main');
    if (!main) return;

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">Organizer Dashboard</h1>
            <p class="text-muted">Manage your hosted events, lifecycle states, registrations, criteria, and results.</p>
          </div>
          <div>
            <a href="#/organizer/events/new" class="btn btn-primary">+ Create New Event</a>
          </div>
        </div>

        <div id="organizer-events-container">
          <div class="loading-container py-5">
            <div class="spinner"></div>
            <p>Loading your managed events...</p>
          </div>
        </div>
      </div>
    `;

    loadOrganizerEvents();
  }

  async function loadOrganizerEvents() {
    const container = document.getElementById('organizer-events-container');
    if (!container) return;

    try {
      const data = await window.API.organizer.listEvents({ pageSize: 50 });
      const events = data.events || [];

      if (events.length === 0) {
        container.innerHTML = window.UI.renderEmptyState(
          'No Managed Events Found',
          'You have not created any events yet. Start by drafting a new event competition.',
          '<a href="#/organizer/events/new" class="btn btn-primary">+ Create Your First Event</a>'
        );
        return;
      }

      let cardsHtml = '<div class="events-grid">';
      events.forEach(event => {
        const isDraft = event.eventStatus === 'DRAFT';
        const isPublished = event.eventStatus === 'PUBLISHED';
        const isCompleted = event.eventStatus === 'COMPLETED';
        const isCancelled = event.eventStatus === 'CANCELLED';

        cardsHtml += `
          <div class="event-card">
            <div class="event-card-header">
              <span class="event-code font-bold">${window.UI.escapeHtml(event.eventCode)}</span>
              ${window.UI.renderStatusBadge(event.eventStatus)}
            </div>

            <h3 class="event-card-title">
              <a href="#/events/${event.eventId}">${window.UI.escapeHtml(event.eventName)}</a>
            </h3>

            <div class="event-card-meta">
              <div class="meta-item">
                <span class="meta-label">Mode:</span>
                <span class="meta-value">${window.UI.renderModeBadge(event.participationMode)}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Confirmed / Capacity:</span>
                <span class="meta-value font-medium">
                  ${event.confirmedCount || 0} / ${event.capacity ? event.capacity : 'Unlimited'}
                </span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Event Starts:</span>
                <span class="meta-value">${window.UI.formatDateTime(event.startsAt)}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Registration:</span>
                <span class="meta-value ${event.registrationOpen ? 'text-success' : 'text-danger'} font-medium">
                  ${event.registrationOpen ? 'Open' : 'Closed'}
                </span>
              </div>
            </div>

            <div class="event-card-footer d-flex flex-column gap-2 mt-3 pt-3 border-top">
              <div class="btn-group w-100">
                <a href="#/organizer/events/${event.eventId}/registrations" class="btn btn-outline btn-sm flex-1 text-center">
                  Registrations (${event.registrationCount || 0})
                </a>
                <a href="#/organizer/events/${event.eventId}/scoring" class="btn btn-outline btn-sm flex-1 text-center">
                  Scoring &amp; Results
                </a>
              </div>

              <div class="btn-group w-100 mt-1">
                ${isDraft ? `
                  <button class="btn btn-success btn-sm flex-1 btn-publish-event" data-id="${event.eventId}" data-name="${window.UI.escapeHtml(event.eventName)}">
                    Publish Event
                  </button>
                  <a href="#/organizer/events/${event.eventId}/edit" class="btn btn-outline btn-sm flex-1 text-center">
                    Edit
                  </a>
                  <button class="btn btn-danger btn-sm flex-1 btn-cancel-event" data-id="${event.eventId}" data-name="${window.UI.escapeHtml(event.eventName)}">
                    Cancel
                  </button>
                ` : ''}

                ${isPublished ? `
                  <a href="#/organizer/events/${event.eventId}/edit" class="btn btn-outline btn-sm flex-1 text-center">
                    Edit
                  </a>
                  ${event.registrationOpen ? `
                    <button class="btn btn-warning btn-sm flex-1 btn-close-reg" data-id="${event.eventId}" data-name="${window.UI.escapeHtml(event.eventName)}">
                      Close Reg
                    </button>
                  ` : ''}
                  <button class="btn btn-danger btn-sm flex-1 btn-cancel-event" data-id="${event.eventId}" data-name="${window.UI.escapeHtml(event.eventName)}">
                    Cancel
                  </button>
                ` : ''}

                ${isCompleted ? `
                  <span class="badge badge-primary w-100 text-center py-2">Event Completed</span>
                ` : ''}

                ${isCancelled ? `
                  <span class="badge badge-danger w-100 text-center py-2">Event Cancelled</span>
                ` : ''}
              </div>
            </div>
          </div>
        `;
      });
      cardsHtml += '</div>';

      container.innerHTML = cardsHtml;

      // Bind Lifecycle Buttons
      // 1. Publish (DRAFT -> PUBLISHED)
      container.querySelectorAll('.btn-publish-event').forEach(btn => {
        btn.addEventListener('click', async () => {
          const eventId = btn.dataset.id;
          const eventName = btn.dataset.name;

          const confirmed = await window.UI.confirmDialog({
            title: 'Publish Event',
            message: `Publishing "${eventName}" will make it visible to the public and permit registrations. Proceed?`,
            confirmText: 'Publish Now',
            confirmType: 'primary'
          });
          if (!confirmed) return;

          window.UI.setButtonLoading(btn, true, 'Publishing...');
          try {
            await window.API.organizer.publishEvent(eventId);
            window.UI.showToast(`"${eventName}" is now published!`, 'success');
            loadOrganizerEvents();
          } catch (err) {
            window.UI.showToast(err.message, 'error');
            window.UI.setButtonLoading(btn, false);
          }
        });
      });

      // 2. Close Registration (PUBLISHED open -> closed)
      container.querySelectorAll('.btn-close-reg').forEach(btn => {
        btn.addEventListener('click', async () => {
          const eventId = btn.dataset.id;
          const eventName = btn.dataset.name;

          const confirmed = await window.UI.confirmDialog({
            title: 'Close Registration',
            message: `Immediately close new registrations for "${eventName}"? Existing registrations will be preserved.`,
            confirmText: 'Close Registration',
            confirmType: 'warning'
          });
          if (!confirmed) return;

          window.UI.setButtonLoading(btn, true, 'Closing...');
          try {
            await window.API.organizer.closeRegistration(eventId);
            window.UI.showToast(`Registration closed for "${eventName}".`, 'info');
            loadOrganizerEvents();
          } catch (err) {
            window.UI.showToast(err.message, 'error');
            window.UI.setButtonLoading(btn, false);
          }
        });
      });

      // 3. Cancel Event
      container.querySelectorAll('.btn-cancel-event').forEach(btn => {
        btn.addEventListener('click', async () => {
          const eventId = btn.dataset.id;
          const eventName = btn.dataset.name;

          const confirmed = await window.UI.confirmDialog({
            title: 'Cancel Event',
            message: `Are you sure you want to cancel "${eventName}"? This is a terminal lifecycle state.`,
            confirmText: 'Yes, Cancel Event',
            confirmType: 'danger'
          });
          if (!confirmed) return;

          window.UI.setButtonLoading(btn, true, 'Cancelling...');
          try {
            await window.API.organizer.cancelEvent(eventId);
            window.UI.showToast(`"${eventName}" has been cancelled.`, 'info');
            loadOrganizerEvents();
          } catch (err) {
            window.UI.showToast(err.message, 'error');
            window.UI.setButtonLoading(btn, false);
          }
        });
      });

    } catch (err) {
      container.innerHTML = `
        <div class="alert alert-error">Failed to load events: ${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  // --- Event Create & Edit Form ---
  async function renderEventForm(params) {
    const main = document.getElementById('app-main');
    if (!main) return;

    const isEdit = Boolean(params && params.id);
    const eventId = isEdit ? params.id : null;

    main.innerHTML = `
      <div class="container py-4">
        <div class="mb-3">
          <a href="#/organizer/dashboard" class="text-muted">&larr; Back to Organizer Dashboard</a>
        </div>

        <div class="card p-4 mx-auto" style="max-width: 800px;">
          <h1 class="page-title text-navy mb-2">${isEdit ? 'Edit Managed Event' : 'Create New Event'}</h1>
          <p class="text-muted mb-4">
            ${isEdit ? 'Update event parameters according to business constraints.' : 'Draft a new campus competition. Once created, you can configure criteria and publish.'}
          </p>

          <div id="event-form-alert"></div>

          <form id="event-editor-form" novalidate>
            <div class="row gap-3">
              <div class="col-6">
                <div class="form-group mb-3">
                  <label for="event-code" class="form-label">Event Code</label>
                  <input type="text" id="event-code" class="form-control" maxlength="40" required ${isEdit ? 'readonly' : ''} placeholder="e.g. HACKATHON-2026">
                  <small class="form-text text-muted">1–40 alphanumeric/dash characters. Immutable after creation.</small>
                </div>
              </div>

              <div class="col-6">
                <div class="form-group mb-3">
                  <label for="event-mode" class="form-label">Participation Mode</label>
                  <select id="event-mode" class="form-control" required>
                    <option value="INDIVIDUAL">Individual (Single participant)</option>
                    <option value="TEAM">Team (2–5 active students)</option>
                  </select>
                  <small class="form-text text-muted">Cannot change once registrations exist.</small>
                </div>
              </div>
            </div>

            <div class="form-group mb-3">
              <label for="event-name" class="form-label">Event Name</label>
              <input type="text" id="event-name" class="form-control" maxlength="160" required placeholder="e.g. Annual Campus Hackathon 2026">
              <small class="form-text text-muted">1–160 characters.</small>
            </div>

            <div class="form-group mb-3">
              <label for="event-desc" class="form-label">Event Description</label>
              <textarea id="event-desc" class="form-control" rows="4" maxlength="2000" placeholder="Provide overview, guidelines, and competition details..."></textarea>
              <small class="form-text text-muted">Maximum 2,000 characters.</small>
            </div>

            <div class="row gap-3">
              <div class="col-6">
                <div class="form-group mb-3">
                  <label for="event-location" class="form-label">Location / Hall</label>
                  <input type="text" id="event-location" class="form-control" maxlength="200" placeholder="e.g. Science Auditorium Room 101">
                  <small class="form-text text-muted">Optional, max 200 characters.</small>
                </div>
              </div>

              <div class="col-6">
                <div class="form-group mb-3">
                  <label for="event-capacity" class="form-label">Confirmed Capacity Limit</label>
                  <input type="number" id="event-capacity" class="form-control" min="1" max="99999999" placeholder="Leave empty for unlimited">
                  <small class="form-text text-muted">Counts confirmed entries. Unlimited if left blank.</small>
                </div>
              </div>
            </div>

            <h3 class="section-subtitle mt-4 mb-2">Schedule &amp; Dates</h3>
            <p class="text-xs text-muted mb-3">Requirement: Registration Opens &le; Registration Closes &le; Starts At &lt; Ends At. Registration close and starts must be in the future.</p>

            <div class="row gap-3">
              <div class="col-6">
                <div class="form-group mb-3">
                  <label for="event-reg-opens" class="form-label">Registration Opens</label>
                  <input type="datetime-local" id="event-reg-opens" class="form-control" required>
                </div>
              </div>

              <div class="col-6">
                <div class="form-group mb-3">
                  <label for="event-reg-closes" class="form-label">Registration Closes</label>
                  <input type="datetime-local" id="event-reg-closes" class="form-control" required>
                </div>
              </div>
            </div>

            <div class="row gap-3">
              <div class="col-6">
                <div class="form-group mb-3">
                  <label for="event-starts-at" class="form-label">Event Starts At</label>
                  <input type="datetime-local" id="event-starts-at" class="form-control" required>
                </div>
              </div>

              <div class="col-6">
                <div class="form-group mb-3">
                  <label for="event-ends-at" class="form-label">Event Ends At</label>
                  <input type="datetime-local" id="event-ends-at" class="form-control" required>
                </div>
              </div>
            </div>

            <div class="d-flex justify-between align-center mt-4 pt-3 border-top">
              <a href="#/organizer/dashboard" class="btn btn-outline">Cancel</a>
              <button type="submit" id="btn-save-event" class="btn btn-primary btn-lg">
                ${isEdit ? 'Save Changes' : 'Create Draft Event'}
              </button>
            </div>
          </form>
        </div>
      </div>
    `;

    // Populate existing values if editing
    if (isEdit) {
      try {
        const res = await window.API.organizer.getManagedEvent(eventId);
        const ev = res.event;
        document.getElementById('event-code').value = ev.eventCode || '';
        document.getElementById('event-name').value = ev.eventName || '';
        document.getElementById('event-mode').value = ev.participationMode || 'INDIVIDUAL';
        document.getElementById('event-desc').value = ev.description || '';
        document.getElementById('event-location').value = ev.location || '';
        if (ev.capacity) document.getElementById('event-capacity').value = ev.capacity;
        document.getElementById('event-reg-opens').value = isoToInput(ev.registrationOpens);
        document.getElementById('event-reg-closes').value = isoToInput(ev.registrationCloses);
        document.getElementById('event-starts-at').value = isoToInput(ev.startsAt);
        document.getElementById('event-ends-at').value = isoToInput(ev.endsAt);
      } catch (err) {
        document.getElementById('event-form-alert').innerHTML = `
          <div class="alert alert-error">Failed to load event: ${window.UI.escapeHtml(err.message)}</div>
        `;
      }
    }

    // Bind form submit
    const form = document.getElementById('event-editor-form');
    const saveBtn = document.getElementById('btn-save-event');
    const alertBox = document.getElementById('event-form-alert');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertBox.innerHTML = '';

      const code = document.getElementById('event-code').value.trim();
      const name = document.getElementById('event-name').value.trim();
      const mode = document.getElementById('event-mode').value;
      const desc = document.getElementById('event-desc').value.trim();
      const location = document.getElementById('event-location').value.trim();
      const capVal = document.getElementById('event-capacity').value.trim();
      const capacity = capVal ? parseInt(capVal, 10) : null;

      const regOpens = inputToIso(document.getElementById('event-reg-opens').value);
      const regCloses = inputToIso(document.getElementById('event-reg-closes').value);
      const startsAt = inputToIso(document.getElementById('event-starts-at').value);
      const endsAt = inputToIso(document.getElementById('event-ends-at').value);

      if (!code || !name || !regOpens || !regCloses || !startsAt || !endsAt) {
        alertBox.innerHTML = '<div class="alert alert-error">Please fill in all required fields.</div>';
        return;
      }

      const payload = {
        eventCode: code,
        eventName: name,
        participationMode: mode,
        description: desc || null,
        location: location || null,
        capacity: capacity,
        registrationOpens: regOpens,
        registrationCloses: regCloses,
        startsAt: startsAt,
        endsAt: endsAt
      };

      window.UI.setButtonLoading(saveBtn, true, isEdit ? 'Updating...' : 'Creating...');

      try {
        if (isEdit) {
          await window.API.organizer.updateEvent(eventId, payload);
          window.UI.showToast('Event updated successfully.', 'success');
        } else {
          await window.API.organizer.createEvent(payload);
          window.UI.showToast('Event created in DRAFT state. You may now configure criteria or publish.', 'success');
        }
        window.Router.navigate('/organizer/dashboard');
      } catch (err) {
        alertBox.innerHTML = `
          <div class="alert alert-error mb-3" role="alert">
            ${window.UI.escapeHtml(err.message || 'Failed to save event.')}
          </div>
        `;
        window.UI.setButtonLoading(saveBtn, false);
      }
    });
  }

  // --- Event Registrations View ---
  async function renderOrganizerRegistrations(params) {
    const main = document.getElementById('app-main');
    if (!main) return;

    const eventId = params.id;

    main.innerHTML = `
      <div class="container py-4">
        <div class="mb-3">
          <a href="#/organizer/dashboard" class="text-muted">&larr; Back to Organizer Dashboard</a>
        </div>

        <div class="page-header">
          <div>
            <h1 class="page-title">Event Registrations</h1>
            <p class="text-muted">Review participant rosters, confirmed allocations, and waitlisted entries for Event #${eventId}.</p>
          </div>
        </div>

        <div id="organizer-reg-list-container" class="card">
          <div class="loading-container py-5">
            <div class="spinner"></div>
            <p>Loading registrations...</p>
          </div>
        </div>
      </div>
    `;

    const container = document.getElementById('organizer-reg-list-container');

    try {
      const data = await window.API.organizer.getRegistrations(eventId, 1, 100);
      const list = data.registrations || [];

      if (list.length === 0) {
        container.innerHTML = window.UI.renderEmptyState(
          'No Registrations Yet',
          'No participants or teams have registered for this event yet.'
        );
        return;
      }

      let rowsHtml = '';
      list.forEach(r => {
        const displayName = r.registrationType === 'TEAM'
          ? `Team: <strong>${window.UI.escapeHtml(r.teamName || 'Team #' + r.teamId)}</strong>`
          : window.UI.escapeHtml(r.participantName || 'Participant #' + r.participantUserId);

        rowsHtml += `
          <tr>
            <td class="font-bold text-navy">#${r.registrationId}</td>
            <td>${displayName}</td>
            <td>${window.UI.renderModeBadge(r.registrationType)}</td>
            <td>${window.UI.renderStatusBadge(r.registrationStatus)}</td>
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
                <th>Participant / Team</th>
                <th>Mode</th>
                <th>Status</th>
                <th>Registration Timestamp</th>
              </tr>
            </thead>
            <tbody>
              ${rowsHtml}
            </tbody>
          </table>
        </div>
      `;
    } catch (err) {
      container.innerHTML = `
        <div class="p-4 alert alert-error">Failed to load registrations: ${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  // --- Scoring & Results Configuration View ---
  async function renderOrganizerScoring(params) {
    const main = document.getElementById('app-main');
    if (!main) return;

    const eventId = params.id;

    main.innerHTML = `
      <div class="container py-4">
        <div class="mb-3">
          <a href="#/organizer/dashboard" class="text-muted">&larr; Back to Organizer Dashboard</a>
        </div>

        <div class="page-header">
          <div>
            <h1 class="page-title">Scoring, Criteria &amp; Results</h1>
            <p class="text-muted">Configure criteria weights (must total 100%), assign judges, set scoring windows, and finalize results for Event #${eventId}.</p>
          </div>
        </div>

        <!-- Navigation Tabs -->
        <div class="tabs-nav mb-4">
          <button class="tab-btn active" data-tab="tab-criteria">Scoring Criteria</button>
          <button class="tab-btn" data-tab="tab-judges">Judge Assignments</button>
          <button class="tab-btn" data-tab="tab-window">Scoring Window</button>
          <button class="tab-btn" data-tab="tab-results">Finalize &amp; Publish</button>
        </div>

        <!-- Tab 1: Criteria -->
        <div id="tab-criteria" class="tab-pane active">
          <div class="row gap-4">
            <div class="col-4">
              <div class="card p-4">
                <h3 class="card-title text-navy mb-3">+ Add Criterion</h3>
                <form id="add-criterion-form">
                  <div class="form-group mb-3">
                    <label for="crit-name" class="form-label">Criterion Name</label>
                    <input type="text" id="crit-name" class="form-control" maxlength="120" required placeholder="e.g. Technical Execution">
                  </div>
                  <div class="form-group mb-3">
                    <label for="crit-max" class="form-label">Max Points</label>
                    <input type="number" id="crit-max" class="form-control" min="0.01" max="999999.99" step="0.01" required placeholder="e.g. 50.00">
                  </div>
                  <div class="form-group mb-3">
                    <label for="crit-weight" class="form-label">Weight Percentage (%)</label>
                    <input type="number" id="crit-weight" class="form-control" min="0.01" max="100.00" step="0.01" required placeholder="e.g. 40.00">
                  </div>
                  <div class="form-group mb-3">
                    <label for="crit-order" class="form-label">Display Order</label>
                    <input type="number" id="crit-order" class="form-control" min="1" max="9999" value="1" required>
                  </div>
                  <button type="submit" id="btn-add-crit" class="btn btn-primary btn-block">Add Criterion</button>
                </form>
              </div>
            </div>

            <div class="col-8">
              <div class="card p-4">
                <div class="d-flex justify-between align-center mb-3">
                  <h3 class="card-title text-navy mb-0">Configured Criteria</h3>
                  <div id="weight-total-indicator" class="font-bold">Total Weight: 0%</div>
                </div>
                <div id="criteria-list-container">
                  <div class="loading-container py-4"><div class="spinner-small"></div> Loading criteria...</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Tab 2: Judges -->
        <div id="tab-judges" class="tab-pane">
          <div class="row gap-4">
            <div class="col-4">
              <div class="card p-4">
                <h3 class="card-title text-navy mb-3">+ Assign Judge</h3>
                <p class="text-sm text-muted mb-3">Judges must have an active JUDGE account. Provide their numeric User ID.</p>
                <form id="assign-judge-form">
                  <div class="form-group mb-3">
                    <label for="judge-uid" class="form-label">Judge User ID</label>
                    <input type="number" id="judge-uid" class="form-control" min="1" required placeholder="e.g. 4">
                  </div>
                  <button type="submit" id="btn-assign-judge" class="btn btn-primary btn-block">Assign Judge</button>
                </form>
              </div>
            </div>

            <div class="col-8">
              <div class="card p-4">
                <h3 class="card-title text-navy mb-3">Assigned Judges Panel</h3>
                <div id="judges-list-container">
                  <div class="loading-container py-4"><div class="spinner-small"></div> Loading judges...</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Tab 3: Window -->
        <div id="tab-window" class="tab-pane">
          <div class="card p-4 max-w-600 mx-auto">
            <h3 class="card-title text-navy mb-2">Configure Scoring Window</h3>
            <p class="text-muted text-sm mb-4">
              Judges can only submit scores during this active scoring window. Scoring close must be in the future when configured.
            </p>

            <form id="scoring-window-form">
              <div class="form-group mb-3">
                <label for="window-opens" class="form-label">Scoring Opens At</label>
                <input type="datetime-local" id="window-opens" class="form-control" required>
              </div>
              <div class="form-group mb-4">
                <label for="window-closes" class="form-label">Scoring Closes At</label>
                <input type="datetime-local" id="window-closes" class="form-control" required>
              </div>
              <div class="d-flex justify-between align-center">
                <button type="button" id="btn-close-scoring-early" class="btn btn-warning">Close Scoring Early</button>
                <button type="submit" id="btn-save-window" class="btn btn-primary">Save Scoring Window</button>
              </div>
            </form>
          </div>
        </div>

        <!-- Tab 4: Finalize & Publish -->
        <div id="tab-results" class="tab-pane">
          <div class="card p-4 mb-4">
            <div class="d-flex justify-between align-center flex-wrap gap-2 mb-3">
              <div>
                <h3 class="card-title text-navy mb-1">Results Lifecycle &amp; Publication</h3>
                <p class="text-muted text-sm mb-0">
                  Prerequisites: Closed scoring window, criteria weights total exactly 100%, and every assigned judge has scored every criterion for every confirmed registration.
                </p>
              </div>
              <div class="btn-group">
                <button id="btn-finalize-results" class="btn btn-warning">Finalize Results</button>
                <button id="btn-publish-results" class="btn btn-success">Publish Results to Public</button>
              </div>
            </div>
          </div>

          <div class="card p-4">
            <h3 class="card-title text-navy mb-3">Organizer Results Preview</h3>
            <div id="results-preview-container">
              <div class="loading-container py-4"><div class="spinner-small"></div> Loading results preview...</div>
            </div>
          </div>
        </div>
      </div>
    `;

    // Tab switching logic
    main.querySelectorAll('.tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        main.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        main.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById(btn.dataset.tab).classList.add('active');
      });
    });

    // Wire Criteria
    setupCriteriaTab(eventId);
    // Wire Judges
    setupJudgesTab(eventId);
    // Wire Window
    setupWindowTab(eventId);
    // Wire Results
    setupResultsTab(eventId);
  }

  async function setupCriteriaTab(eventId) {
    const listContainer = document.getElementById('criteria-list-container');
    const totalIndicator = document.getElementById('weight-total-indicator');

    const loadCriteria = async () => {
      try {
        const data = await window.API.organizer.getCriteria(eventId);
        const criteria = data.criteria || [];

        let totalWeight = 0;
        criteria.forEach(c => totalWeight += Number(c.weightPercent));

        if (totalIndicator) {
          const isExact100 = Math.abs(totalWeight - 100) < 0.001;
          totalIndicator.innerHTML = `Total Weight: <span class="${isExact100 ? 'text-success' : 'text-danger'}">${totalWeight.toFixed(2)}% ${isExact100 ? '✓ (Valid 100%)' : '⚠ (Must equal 100%)'}</span>`;
        }

        if (criteria.length === 0) {
          listContainer.innerHTML = '<p class="text-muted mb-0">No criteria configured yet. Add criteria on the left.</p>';
          return;
        }

        let rowsHtml = '';
        criteria.forEach(c => {
          rowsHtml += `
            <tr>
              <td class="font-bold">${c.displayOrder}</td>
              <td>${window.UI.escapeHtml(c.criterionName)}</td>
              <td>${Number(c.maxPoints).toFixed(2)} pts</td>
              <td class="font-bold text-navy">${Number(c.weightPercent).toFixed(2)}%</td>
            </tr>
          `;
        });

        listContainer.innerHTML = `
          <div class="table-responsive">
            <table class="data-table">
              <thead>
                <tr>
                  <th style="width: 60px;">Order</th>
                  <th>Criterion Name</th>
                  <th>Max Points</th>
                  <th>Weight %</th>
                </tr>
              </thead>
              <tbody>
                ${rowsHtml}
              </tbody>
            </table>
          </div>
        `;
      } catch (err) {
        listContainer.innerHTML = `<div class="alert alert-error">${window.UI.escapeHtml(err.message)}</div>`;
      }
    };

    // Form submit
    const form = document.getElementById('add-criterion-form');
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const payload = {
        criterionName: document.getElementById('crit-name').value.trim(),
        maxPoints: parseFloat(document.getElementById('crit-max').value),
        weightPercent: parseFloat(document.getElementById('crit-weight').value),
        displayOrder: parseInt(document.getElementById('crit-order').value, 10)
      };

      try {
        await window.API.organizer.createCriterion(eventId, payload);
        window.UI.showToast('Criterion added successfully.', 'success');
        form.reset();
        document.getElementById('crit-order').value = '1';
        loadCriteria();
      } catch (err) {
        window.UI.showToast(err.message, 'error');
      }
    });

    loadCriteria();
  }

  async function setupJudgesTab(eventId) {
    const listContainer = document.getElementById('judges-list-container');

    const loadJudges = async () => {
      try {
        const data = await window.API.organizer.getJudges(eventId);
        const judges = data.judges || [];

        if (judges.length === 0) {
          listContainer.innerHTML = '<p class="text-muted mb-0">No judges assigned yet. Assign an active judge using their User ID.</p>';
          return;
        }

        let rowsHtml = '';
        judges.forEach(j => {
          rowsHtml += `
            <tr>
              <td class="font-bold text-navy">#${j.userId}</td>
              <td>${window.UI.escapeHtml(j.displayName)}</td>
              <td><span class="badge ${j.activeFlag === 'Y' || j.active === true ? 'badge-success' : 'badge-danger'}">Active</span></td>
              <td class="text-sm text-muted">${window.UI.formatDateTime(j.assignedAt)}</td>
              <td>
                <button class="btn btn-danger btn-xs btn-unassign-judge" data-id="${j.userId}" data-name="${window.UI.escapeHtml(j.displayName)}">
                  Unassign
                </button>
              </td>
            </tr>
          `;
        });

        listContainer.innerHTML = `
          <div class="table-responsive">
            <table class="data-table">
              <thead>
                <tr>
                  <th>User ID</th>
                  <th>Judge Name</th>
                  <th>Status</th>
                  <th>Assigned At</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                ${rowsHtml}
              </tbody>
            </table>
          </div>
        `;

        // Unassign handlers
        listContainer.querySelectorAll('.btn-unassign-judge').forEach(btn => {
          btn.addEventListener('click', async () => {
            const jid = btn.dataset.id;
            const jname = btn.dataset.name;

            const confirmed = await window.UI.confirmDialog({
              title: 'Unassign Judge',
              message: `Remove judge "${jname}" (User #${jid}) from this event?`,
              confirmText: 'Unassign',
              confirmType: 'danger'
            });
            if (!confirmed) return;

            try {
              await window.API.organizer.unassignJudge(eventId, jid);
              window.UI.showToast(`Judge ${jname} unassigned.`, 'info');
              loadJudges();
            } catch (err) {
              window.UI.showToast(err.message, 'error');
            }
          });
        });
      } catch (err) {
        listContainer.innerHTML = `<div class="alert alert-error">${window.UI.escapeHtml(err.message)}</div>`;
      }
    };

    const form = document.getElementById('assign-judge-form');
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const jid = document.getElementById('judge-uid').value.trim();
      if (!jid) return;

      try {
        await window.API.organizer.assignJudge(eventId, jid);
        window.UI.showToast(`Judge #${jid} assigned successfully.`, 'success');
        form.reset();
        loadJudges();
      } catch (err) {
        window.UI.showToast(err.message, 'error');
      }
    });

    loadJudges();
  }

  async function setupWindowTab(eventId) {
    const form = document.getElementById('scoring-window-form');
    const closeEarlyBtn = document.getElementById('btn-close-scoring-early');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const opens = inputToIso(document.getElementById('window-opens').value);
      const closes = inputToIso(document.getElementById('window-closes').value);

      if (!opens || !closes) {
        window.UI.showToast('Please provide both open and close timestamps.', 'error');
        return;
      }

      try {
        await window.API.organizer.setScoringWindow(eventId, {
          scoringOpens: opens,
          scoringCloses: closes
        });
        window.UI.showToast('Scoring window configured successfully.', 'success');
      } catch (err) {
        window.UI.showToast(err.message, 'error');
      }
    });

    closeEarlyBtn.addEventListener('click', async () => {
      const confirmed = await window.UI.confirmDialog({
        title: 'Close Scoring Early',
        message: 'Immediately close the scoring window? Judges will no longer be able to submit scores.',
        confirmText: 'Close Scoring Now',
        confirmType: 'warning'
      });
      if (!confirmed) return;

      try {
        await window.API.organizer.closeScoring(eventId);
        window.UI.showToast('Scoring window closed.', 'info');
      } catch (err) {
        window.UI.showToast(err.message, 'error');
      }
    });
  }

  async function setupResultsTab(eventId) {
    const previewContainer = document.getElementById('results-preview-container');
    const finalizeBtn = document.getElementById('btn-finalize-results');
    const publishBtn = document.getElementById('btn-publish-results');

    const loadPreview = async () => {
      try {
        const data = await window.API.organizer.getResultsPreview(eventId);
        const results = data.results || [];

        if (results.length === 0) {
          previewContainer.innerHTML = `
            <div class="text-muted py-3 text-center">
              No results preview available yet. Finalize the event results first once all judges have completed scoring.
            </div>
          `;
          return;
        }

        let rowsHtml = '';
        results.forEach(r => {
          rowsHtml += `
            <tr>
              <td class="font-bold text-center">#${r.resultRank}</td>
              <td class="font-medium">${window.UI.escapeHtml(r.registrantName)}</td>
              <td>${window.UI.renderModeBadge(r.registrationType)}</td>
              <td class="font-bold text-navy">${Number(r.weightedScore).toFixed(2)}</td>
              <td>${r.criteriaScored} criteria</td>
            </tr>
          `;
        });

        previewContainer.innerHTML = `
          <div class="table-responsive">
            <table class="data-table">
              <thead>
                <tr>
                  <th style="width: 80px;" class="text-center">Rank</th>
                  <th>Registrant / Team</th>
                  <th>Type</th>
                  <th>Weighted Score</th>
                  <th>Criteria Scored</th>
                </tr>
              </thead>
              <tbody>
                ${rowsHtml}
              </tbody>
            </table>
          </div>
        `;
      } catch {
        previewContainer.innerHTML = `
          <div class="text-muted py-3 text-center">
            Results preview is available after scoring is complete and results are finalized.
          </div>
        `;
      }
    };

    finalizeBtn.addEventListener('click', async () => {
      const confirmed = await window.UI.confirmDialog({
        title: 'Finalize Event Results',
        message: 'Finalizing locks all scores, marks the event as COMPLETED, and generates official rankings. Confirm finalization?',
        confirmText: 'Finalize Results',
        confirmType: 'warning'
      });
      if (!confirmed) return;

      window.UI.setButtonLoading(finalizeBtn, true, 'Finalizing...');
      try {
        await window.API.organizer.finalizeResults(eventId);
        window.UI.showToast('Results finalized successfully! The event is now COMPLETED.', 'success');
        loadPreview();
      } catch (err) {
        window.UI.showToast(err.message, 'error');
      } finally {
        window.UI.setButtonLoading(finalizeBtn, false);
      }
    });

    publishBtn.addEventListener('click', async () => {
      const confirmed = await window.UI.confirmDialog({
        title: 'Publish Results',
        message: 'Publishing will immediately make official rankings and scores visible to the public on the event detail page. Proceed?',
        confirmText: 'Publish to Public',
        confirmType: 'primary'
      });
      if (!confirmed) return;

      window.UI.setButtonLoading(publishBtn, true, 'Publishing...');
      try {
        await window.API.organizer.publishResults(eventId);
        window.UI.showToast('Results published successfully to the public!', 'success');
      } catch (err) {
        window.UI.showToast(err.message, 'error');
      } finally {
        window.UI.setButtonLoading(publishBtn, false);
      }
    });

    loadPreview();
  }

  window.Views = window.Views || {};
  window.Views.OrganizerDashboard = renderOrganizerDashboard;
  window.Views.OrganizerEventForm = renderEventForm;
  window.Views.OrganizerRegistrations = renderOrganizerRegistrations;
  window.Views.OrganizerScoring = renderOrganizerScoring;
})(window);
