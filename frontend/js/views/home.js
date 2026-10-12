/**
 * CampusConnect Home View
 * Landing page with hero banner, quick search, featured events, and platform overview.
 */

(function (window) {
  'use strict';

  async function renderHome() {
    const main = document.getElementById('app-main');
    if (!main) return;

    main.innerHTML = `
      <section class="hero-section">
        <div class="container hero-container">
          <div class="hero-content">
            <span class="hero-pill">CampusConnect 2026</span>
            <h1 class="hero-title">Elevate Your College Event Experience</h1>
            <p class="hero-subtitle">
              Discover, organize, and participate in campus hackathons, technical showcases, and cultural competitions. Seamless team management, live scoring, and verified results.
            </p>
            <div class="hero-actions">
              <a href="#/events" class="btn btn-primary btn-lg">Browse All Events</a>
              <a href="#/register" class="btn btn-outline btn-lg guest-only-action">Create Student Account</a>
            </div>
          </div>
        </div>
      </section>

      <section class="container py-5">
        <div class="section-header">
          <div>
            <h2 class="section-title">Upcoming & Active Events</h2>
            <p class="text-muted">Explore highlighted events currently open for registration or taking place soon.</p>
          </div>
          <a href="#/events" class="btn btn-outline">View Catalog &rarr;</a>
        </div>

        <div id="home-featured-events" class="events-grid">
          <div class="loading-container">
            <div class="spinner"></div>
            <p>Loading featured events...</p>
          </div>
        </div>
      </section>

      <section class="bg-surface py-5">
        <div class="container">
          <div class="text-center mb-5">
            <h2 class="section-title">Built For The Entire Campus Ecosystem</h2>
            <p class="text-muted max-w-600 mx-auto">From individual showcase entries to campus-wide hackathon scoring, CampusConnect powers fair and transparent competitions.</p>
          </div>

          <div class="features-grid">
            <div class="feature-card">
              <div class="feature-icon">🎓</div>
              <h3 class="feature-title">For Students</h3>
              <p class="feature-desc">Form teams of 2–5 members, send and accept invitations, register individually or as a team, and track your confirmed or waitlisted status in real time.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon">🎪</div>
              <h3 class="feature-title">For Organizers</h3>
              <p class="feature-desc">Draft and publish events, configure custom scoring criteria with weighted percentages, assign judges, manage capacity, and publish verified results.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon">⚖️</div>
              <h3 class="feature-title">For Judges</h3>
              <p class="feature-desc">Review assigned participants, submit criterion-specific scores with constructive feedback during designated scoring windows with instant batch validation.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon">🛡️</div>
              <h3 class="feature-title">For Administrators</h3>
              <p class="feature-desc">Comprehensive system oversight: manage user roles and account statuses with strict protection policies, view audit logs, and monitor platform health.</p>
            </div>
          </div>
        </div>
      </section>
    `;

    // Update guest-only buttons visibility based on current user
    const user = window.Auth.getUser();
    const guestOnlyBtn = main.querySelector('.guest-only-action');
    if (guestOnlyBtn && user) {
      guestOnlyBtn.style.display = 'none';
    }

    // Load featured events
    const featuredContainer = document.getElementById('home-featured-events');
    try {
      const data = await window.API.events.listPublic({
        page: 1,
        pageSize: 3,
        sort: 'startsAt',
        direction: 'asc'
      });

      if (!data.events || data.events.length === 0) {
        featuredContainer.innerHTML = window.UI.renderEmptyState(
          'No Upcoming Events Yet',
          'Check back soon or browse historical events in the event catalog.',
          '<a href="#/events" class="btn btn-primary">Browse All Events</a>'
        );
        return;
      }

      let cardsHtml = '';
      data.events.forEach(event => {
        cardsHtml += `
          <div class="event-card">
            <div class="event-card-header">
              <span class="event-code">${window.UI.escapeHtml(event.eventCode)}</span>
              ${window.UI.renderStatusBadge(event.eventStatus)}
            </div>
            <h3 class="event-card-title">
              <a href="#/events/${event.eventId}">${window.UI.escapeHtml(event.eventName)}</a>
            </h3>
            <p class="event-card-desc">${window.UI.escapeHtml(event.description || 'No description provided.')}</p>
            
            <div class="event-card-meta">
              <div class="meta-item">
                <span class="meta-label">Mode:</span>
                <span class="meta-value">${window.UI.renderModeBadge(event.participationMode)}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Starts:</span>
                <span class="meta-value">${window.UI.formatDateTime(event.startsAt)}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Location:</span>
                <span class="meta-value">${window.UI.escapeHtml(event.location || 'TBA')}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Registration:</span>
                <span class="meta-value ${event.registrationOpen ? 'text-success' : 'text-danger'} font-medium">
                  ${event.registrationOpen ? 'Open Now' : 'Closed'}
                </span>
              </div>
            </div>

            <div class="event-card-footer">
              <a href="#/events/${event.eventId}" class="btn btn-outline btn-block">View Details &amp; Results</a>
            </div>
          </div>
        `;
      });

      featuredContainer.innerHTML = cardsHtml;
    } catch (err) {
      featuredContainer.innerHTML = `
        <div class="alert alert-error">
          Failed to load featured events: ${window.UI.escapeHtml(err.message)}
        </div>
      `;
    }
  }

  window.Views = window.Views || {};
  window.Views.Home = renderHome;
})(window);
