/**
 * CampusConnect Judge Views
 * Assigned events listing, eligible participant rosters, criteria inspection,
 * score entry/editing, transactional batch submissions, and scoring-window guards.
 */

(function (window) {
  'use strict';

  // --- Assigned Events Listing ---
  async function renderAssignedEvents() {
    const main = document.getElementById('app-main');
    if (!main) return;

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">Judging Dashboard</h1>
            <p class="text-muted">Review events to which you are officially assigned as a judge and enter scoring evaluations.</p>
          </div>
        </div>

        <div id="assigned-events-container">
          <div class="loading-container py-5">
            <div class="spinner"></div>
            <p>Loading assigned events...</p>
          </div>
        </div>
      </div>
    `;

    const container = document.getElementById('assigned-events-container');

    try {
      const data = await window.API.judge.getAssignedEvents();
      const events = data.events || [];

      if (events.length === 0) {
        container.innerHTML = window.UI.renderEmptyState(
          'No Assigned Events',
          'You are not currently assigned to judge any events. Contact event organizers to receive judging assignments.'
        );
        return;
      }

      let cardsHtml = '<div class="events-grid">';
      const now = new Date();

      events.forEach(event => {
        let windowStatusBadge = '<span class="badge badge-neutral">No Window Set</span>';
        let isWindowOpen = false;

        if (event.scoringOpens && event.scoringCloses) {
          const opens = new Date(event.scoringOpens);
          const closes = new Date(event.scoringCloses);

          if (now >= opens && now <= closes) {
            windowStatusBadge = '<span class="badge badge-success">● Scoring Window Active</span>';
            isWindowOpen = true;
          } else if (now < opens) {
            windowStatusBadge = '<span class="badge badge-warning">Scoring Window Upcoming</span>';
          } else {
            windowStatusBadge = '<span class="badge badge-danger">Scoring Window Closed</span>';
          }
        }

        cardsHtml += `
          <div class="event-card">
            <div class="event-card-header">
              <span class="event-code font-bold">${window.UI.escapeHtml(event.eventCode)}</span>
              ${window.UI.renderStatusBadge(event.eventStatus)}
            </div>

            <h3 class="event-card-title">${window.UI.escapeHtml(event.eventName)}</h3>

            <div class="event-card-meta">
              <div class="meta-item">
                <span class="meta-label">Scoring Status:</span>
                <span class="meta-value">${windowStatusBadge}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Window Opens:</span>
                <span class="meta-value">${window.UI.formatDateTime(event.scoringOpens)}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Window Closes:</span>
                <span class="meta-value">${window.UI.formatDateTime(event.scoringCloses)}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Event Dates:</span>
                <span class="meta-value">${window.UI.formatDate(event.startsAt)} &ndash; ${window.UI.formatDate(event.endsAt)}</span>
              </div>
            </div>

            <div class="event-card-footer mt-3 pt-3 border-top">
              <a href="#/judge/events/${event.eventId}" class="btn btn-primary btn-block">
                ${isWindowOpen ? 'Evaluate &amp; Score Participants &rarr;' : 'View Scoring Sheet &rarr;'}
              </a>
            </div>
          </div>
        `;
      });
      cardsHtml += '</div>';

      container.innerHTML = cardsHtml;
    } catch (err) {
      container.innerHTML = `
        <div class="alert alert-error">Failed to load assigned events: ${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  // --- Judging Event & Score Sheet ---
  async function renderJudgingSheet(params) {
    const main = document.getElementById('app-main');
    if (!main) return;

    const eventId = params.id;

    main.innerHTML = `
      <div class="container py-4">
        <div class="mb-3">
          <a href="#/judge/events" class="text-muted">&larr; Back to Judging Dashboard</a>
        </div>

        <div id="judging-sheet-container">
          <div class="loading-container py-5">
            <div class="spinner"></div>
            <p>Loading judging criteria and participant rosters...</p>
          </div>
        </div>
      </div>
    `;

    const container = document.getElementById('judging-sheet-container');

    try {
      const data = await window.API.judge.getJudgingEvent(eventId);
      const event = data.assignment.event;
      const criteria = data.assignment.criteria || [];
      const registrations = data.assignment.registrations || [];

      // Check window active state
      const now = new Date();
      let windowNoticeHtml = '';
      let isWindowOpen = false;

      if (event.scoringOpens && event.scoringCloses) {
        const opens = new Date(event.scoringOpens);
        const closes = new Date(event.scoringCloses);

        if (now >= opens && now <= closes) {
          isWindowOpen = true;
          windowNoticeHtml = `
            <div class="alert alert-success mb-4">
              <strong>Scoring Window is OPEN:</strong> Submissions are currently active and accepted until ${window.UI.formatDateTime(event.scoringCloses)}.
            </div>
          `;
        } else if (now < opens) {
          windowNoticeHtml = `
            <div class="alert alert-warning mb-4">
              <strong>Scoring Window Not Yet Open:</strong> Opens on ${window.UI.formatDateTime(event.scoringOpens)}. Submissions will be accepted during the configured window.
            </div>
          `;
        } else {
          windowNoticeHtml = `
            <div class="alert alert-danger mb-4">
              <strong>Scoring Window is CLOSED:</strong> The scoring window closed on ${window.UI.formatDateTime(event.scoringCloses)}. New score submissions are locked.
            </div>
          `;
        }
      } else {
        windowNoticeHtml = `
          <div class="alert alert-warning mb-4">
            <strong>Scoring Window Pending:</strong> The event organizer has not yet configured the scoring window dates.
          </div>
        `;
      }

      container.innerHTML = `
        <div class="card p-4 mb-4">
          <div class="d-flex justify-between align-start flex-wrap gap-2 mb-3">
            <div>
              <span class="event-code font-bold text-navy">${window.UI.escapeHtml(event.eventCode)}</span>
              <h1 class="page-title mt-1">${window.UI.escapeHtml(event.eventName)}</h1>
            </div>
            <div>
              ${window.UI.renderStatusBadge(event.eventStatus)}
            </div>
          </div>
          ${windowNoticeHtml}

          <!-- Criteria Overview -->
          <h3 class="section-subtitle mb-2">Evaluation Criteria</h3>
          <div class="criteria-tags d-flex flex-wrap gap-2 mb-2">
            ${criteria.map(c => `
              <span class="badge badge-secondary p-2">
                <strong>${window.UI.escapeHtml(c.criterionName)}</strong>: Max ${Number(c.maxPoints).toFixed(2)} pts (${Number(c.weightPercent).toFixed(2)}% wt)
              </span>
            `).join('')}
          </div>
        </div>

        <div class="row gap-4">
          <!-- Participant Roster List -->
          <div class="col-4">
            <div class="card p-3">
              <h3 class="card-title text-navy mb-2">Confirmed Participants (${registrations.length})</h3>
              <p class="text-xs text-muted mb-3">Select a participant to evaluate or update your score submission.</p>

              <div id="participant-roster-list" class="roster-select-list">
                ${registrations.length === 0 ? '<p class="text-muted p-2">No confirmed participants found.</p>' : ''}
                ${registrations.map((r, idx) => `
                  <button type="button" class="participant-item-btn ${idx === 0 ? 'active' : ''}" data-reg-id="${r.registrationId}" data-name="${window.UI.escapeHtml(r.registrantName)}">
                    <div class="d-flex justify-between align-center">
                      <strong class="text-navy">${window.UI.escapeHtml(r.registrantName)}</strong>
                      <span class="badge ${r.registrationType === 'TEAM' ? 'badge-team' : 'badge-individual'} text-xs">${r.registrationType}</span>
                    </div>
                    <span class="text-xs text-muted d-block mt-1">Registration #${r.registrationId}</span>
                  </button>
                `).join('')}
              </div>
            </div>
          </div>

          <!-- Score Entry Panel -->
          <div class="col-8">
            <div class="card p-4" id="score-entry-panel">
              <div class="loading-container py-4"><div class="spinner-small"></div> Loading score sheet...</div>
            </div>
          </div>
        </div>
      `;

      // Select initial participant or bind clicks
      const rosterBtns = container.querySelectorAll('.participant-item-btn');
      rosterBtns.forEach(btn => {
        btn.addEventListener('click', () => {
          rosterBtns.forEach(b => b.classList.remove('active'));
          btn.classList.add('active');
          loadParticipantScores(eventId, btn.dataset.regId, btn.dataset.name, criteria, isWindowOpen);
        });
      });

      if (registrations.length > 0) {
        loadParticipantScores(eventId, registrations[0].registrationId, registrations[0].registrantName, criteria, isWindowOpen);
      } else {
        document.getElementById('score-entry-panel').innerHTML = `
          <div class="text-muted text-center py-4">No confirmed participants available to score for this event.</div>
        `;
      }

    } catch (err) {
      container.innerHTML = `
        <div class="alert alert-error">Failed to load judging sheet: ${window.UI.escapeHtml(err.message)}</div>
      `;
    }
  }

  async function loadParticipantScores(eventId, regId, registrantName, criteria, isWindowOpen) {
    const panel = document.getElementById('score-entry-panel');
    if (!panel) return;

    panel.innerHTML = '<div class="loading-container py-4"><div class="spinner-small"></div> Loading previous scores...</div>';

    try {
      const data = await window.API.judge.getScores(eventId, regId);
      const existingScores = data.scores || [];
      const scoreMap = new Map();
      existingScores.forEach(s => scoreMap.set(s.criterionId, s));

      let criteriaInputsHtml = '';
      criteria.forEach(crit => {
        const saved = scoreMap.get(crit.criterionId);
        const awardedVal = saved ? saved.awardedPoints : '';
        const feedbackVal = saved && saved.feedback ? saved.feedback : '';

        criteriaInputsHtml += `
          <div class="criterion-score-row card p-3 mb-3 bg-surface" data-crit-id="${crit.criterionId}" data-max="${crit.maxPoints}">
            <div class="d-flex justify-between align-center mb-2">
              <label class="form-label font-bold text-navy mb-0">
                ${window.UI.escapeHtml(crit.criterionName)}
              </label>
              <span class="text-xs text-muted">
                Maximum: <strong>${Number(crit.maxPoints).toFixed(2)} points</strong> (${Number(crit.weightPercent).toFixed(2)}% weight)
              </span>
            </div>

            <div class="row gap-3">
              <div class="col-4">
                <div class="form-group mb-0">
                  <label class="form-label text-xs">Awarded Points</label>
                  <input type="number" class="form-control input-awarded-points" min="0" max="${crit.maxPoints}" step="0.01" value="${awardedVal}" placeholder="0.00 - ${crit.maxPoints}" required ${!isWindowOpen ? 'disabled' : ''}>
                </div>
              </div>
              <div class="col-8">
                <div class="form-group mb-0">
                  <label class="form-label text-xs">Feedback / Comments (Optional)</label>
                  <input type="text" class="form-control input-crit-feedback" maxlength="500" value="${window.UI.escapeHtml(feedbackVal)}" placeholder="Constructive evaluation notes..." ${!isWindowOpen ? 'disabled' : ''}>
                </div>
              </div>
            </div>
          </div>
        `;
      });

      panel.innerHTML = `
        <div class="d-flex justify-between align-center mb-3">
          <div>
            <h2 class="section-title mb-0">Scoring: ${window.UI.escapeHtml(registrantName)}</h2>
            <span class="text-xs text-muted">Registration #${regId}</span>
          </div>
          <div>
            ${existingScores.length === criteria.length ? '<span class="badge badge-success">✓ All Criteria Scored</span>' : '<span class="badge badge-warning">Scoring Incomplete</span>'}
          </div>
        </div>

        <form id="submit-batch-scores-form">
          <div id="criteria-inputs-wrapper">
            ${criteriaInputsHtml}
          </div>

          <div class="d-flex justify-between align-center mt-4 pt-3 border-top">
            <span class="text-xs text-muted">
              ${isWindowOpen ? 'All scores in this batch are submitted in an atomic transaction.' : 'Scoring window closed. Submissions are disabled.'}
            </span>
            <button type="submit" id="btn-submit-scores" class="btn btn-primary btn-lg" ${!isWindowOpen ? 'disabled' : ''}>
              Submit Scores (Batch)
            </button>
          </div>
        </form>
      `;

      // Bind batch submit
      const form = document.getElementById('submit-batch-scores-form');
      const submitBtn = document.getElementById('btn-submit-scores');

      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const scoreRows = panel.querySelectorAll('.criterion-score-row');
        const batch = [];
        let hasError = false;

        scoreRows.forEach(row => {
          const critId = parseInt(row.dataset.critId, 10);
          const maxPoints = parseFloat(row.dataset.max);
          const ptsInput = row.querySelector('.input-awarded-points');
          const fbInput = row.querySelector('.input-crit-feedback');

          const awardedPoints = parseFloat(ptsInput.value);
          if (isNaN(awardedPoints) || awardedPoints < 0 || awardedPoints > maxPoints) {
            window.UI.showToast(`Points for criterion must be between 0 and ${maxPoints}.`, 'error');
            ptsInput.focus();
            hasError = true;
            return;
          }

          batch.push({
            criterionId: critId,
            awardedPoints: awardedPoints,
            feedback: fbInput.value.trim() || null
          });
        });

        if (hasError) return;

        window.UI.setButtonLoading(submitBtn, true, 'Submitting batch...');
        try {
          await window.API.judge.submitScores(eventId, regId, batch);
          window.UI.showToast(`Scores submitted successfully for "${registrantName}"!`, 'success');
          loadParticipantScores(eventId, regId, registrantName, criteria, isWindowOpen);
        } catch (err) {
          window.UI.showToast(err.message, 'error');
          window.UI.setButtonLoading(submitBtn, false);
        }
      });

    } catch (err) {
      panel.innerHTML = `<div class="alert alert-error">Failed to load scores: ${window.UI.escapeHtml(err.message)}</div>`;
    }
  }

  window.Views = window.Views || {};
  window.Views.JudgeAssignedEvents = renderAssignedEvents;
  window.Views.JudgeSheet = renderJudgingSheet;
})(window);
