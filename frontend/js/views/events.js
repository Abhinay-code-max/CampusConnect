/**
 * CampusConnect Events Catalog View
 * Event search, filters (mode, registration open, date bounds), sorting, and pagination.
 */

(function (window) {
  'use strict';

  let currentParams = {
    page: 1,
    pageSize: 12,
    q: '',
    participationMode: '',
    registrationOpen: '',
    sort: 'startsAt',
    direction: 'asc'
  };

  async function renderEvents(routeParams, queryParams) {
    const main = document.getElementById('app-main');
    if (!main) return;

    // Read initial params from query params if available
    if (queryParams.has('page')) currentParams.page = parseInt(queryParams.get('page'), 10) || 1;
    if (queryParams.has('q')) currentParams.q = queryParams.get('q');
    if (queryParams.has('participationMode')) currentParams.participationMode = queryParams.get('participationMode');
    if (queryParams.has('registrationOpen')) currentParams.registrationOpen = queryParams.get('registrationOpen');
    if (queryParams.has('sort')) currentParams.sort = queryParams.get('sort');
    if (queryParams.has('direction')) currentParams.direction = queryParams.get('direction');

    main.innerHTML = `
      <div class="container py-4">
        <div class="page-header">
          <div>
            <h1 class="page-title">Event Catalog</h1>
            <p class="text-muted">Browse published, completed, and historical campus competitions and events.</p>
          </div>
        </div>

        <!-- Filter Bar -->
        <div class="card p-3 mb-4 filter-bar-card">
          <form id="events-filter-form" class="filter-form">
            <div class="form-row">
              <div class="form-group flex-2">
                <label for="filter-q" class="form-label">Search Event Name</label>
                <input type="text" id="filter-q" class="form-control" placeholder="Search keywords..." value="${window.UI.escapeHtml(currentParams.q)}">
              </div>

              <div class="form-group flex-1">
                <label for="filter-mode" class="form-label">Participation Mode</label>
                <select id="filter-mode" class="form-control">
                  <option value="">All Modes</option>
                  <option value="INDIVIDUAL" ${currentParams.participationMode === 'INDIVIDUAL' ? 'selected' : ''}>Individual</option>
                  <option value="TEAM" ${currentParams.participationMode === 'TEAM' ? 'selected' : ''}>Team</option>
                </select>
              </div>

              <div class="form-group flex-1">
                <label for="filter-reg-open" class="form-label">Registration</label>
                <select id="filter-reg-open" class="form-control">
                  <option value="">All Statuses</option>
                  <option value="true" ${currentParams.registrationOpen === 'true' ? 'selected' : ''}>Open Now</option>
                  <option value="false" ${currentParams.registrationOpen === 'false' ? 'selected' : ''}>Closed</option>
                </select>
              </div>

              <div class="form-group flex-1">
                <label for="filter-sort" class="form-label">Sort By</label>
                <select id="filter-sort" class="form-control">
                  <option value="startsAt" ${currentParams.sort === 'startsAt' ? 'selected' : ''}>Start Date</option>
                  <option value="eventName" ${currentParams.sort === 'eventName' ? 'selected' : ''}>Event Name</option>
                  <option value="createdAt" ${currentParams.sort === 'createdAt' ? 'selected' : ''}>Recently Added</option>
                </select>
              </div>

              <div class="form-group flex-1">
                <label for="filter-dir" class="form-label">Direction</label>
                <select id="filter-dir" class="form-control">
                  <option value="asc" ${currentParams.direction === 'asc' ? 'selected' : ''}>Ascending</option>
                  <option value="desc" ${currentParams.direction === 'desc' ? 'selected' : ''}>Descending</option>
                </select>
              </div>

              <div class="form-group form-actions flex-auto">
                <label class="form-label">&nbsp;</label>
                <div class="btn-group">
                  <button type="submit" class="btn btn-primary">Apply</button>
                  <button type="button" id="filter-reset-btn" class="btn btn-outline">Reset</button>
                </div>
              </div>
            </div>
          </form>
        </div>

        <!-- Events List Container -->
        <div id="events-list-container">
          <div class="loading-container py-5">
            <div class="spinner"></div>
            <p>Loading events...</p>
          </div>
        </div>

        <!-- Pagination -->
        <div id="events-pagination-container" class="pagination-wrapper mt-4"></div>
      </div>
    `;

    // Bind filter submit
    const form = document.getElementById('events-filter-form');
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      currentParams.q = document.getElementById('filter-q').value.trim();
      currentParams.participationMode = document.getElementById('filter-mode').value;
      currentParams.registrationOpen = document.getElementById('filter-reg-open').value;
      currentParams.sort = document.getElementById('filter-sort').value;
      currentParams.direction = document.getElementById('filter-dir').value;
      currentParams.page = 1; // Reset to page 1
      loadEvents();
    });

    // Reset button
    document.getElementById('filter-reset-btn').addEventListener('click', () => {
      currentParams = {
        page: 1,
        pageSize: 12,
        q: '',
        participationMode: '',
        registrationOpen: '',
        sort: 'startsAt',
        direction: 'asc'
      };
      form.reset();
      loadEvents();
    });

    await loadEvents();
  }

  async function loadEvents() {
    const listContainer = document.getElementById('events-list-container');
    const paginationContainer = document.getElementById('events-pagination-container');
    if (!listContainer) return;

    listContainer.innerHTML = `
      <div class="loading-container py-5">
        <div class="spinner"></div>
        <p>Loading events...</p>
      </div>
    `;

    try {
      const data = await window.API.events.listPublic(currentParams);

      if (!data.events || data.events.length === 0) {
        listContainer.innerHTML = window.UI.renderEmptyState(
          'No Events Found',
          'No events match your current filter criteria. Try adjusting your search query or filters.'
        );
        if (paginationContainer) paginationContainer.innerHTML = '';
        return;
      }

      let gridHtml = '<div class="events-grid">';
      data.events.forEach(event => {
        const capacityText = event.capacity ? `${event.confirmedCount || 0} / ${event.capacity}` : `${event.confirmedCount || 0} (Unlimited)`;
        const isFull = event.capacity && (event.confirmedCount >= event.capacity);

        gridHtml += `
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
                <span class="meta-label">Ends:</span>
                <span class="meta-value">${window.UI.formatDateTime(event.endsAt)}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Confirmed Entries:</span>
                <span class="meta-value font-medium ${isFull ? 'text-warning' : ''}">${capacityText}</span>
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
              <a href="#/events/${event.eventId}" class="btn btn-primary btn-block">View Details &amp; Results</a>
            </div>
          </div>
        `;
      });
      gridHtml += '</div>';

      listContainer.innerHTML = gridHtml;

      // Render pagination
      renderPagination(data.page, data.totalPages, data.total);
    } catch (err) {
      listContainer.innerHTML = `
        <div class="alert alert-error">
          Failed to load events: ${window.UI.escapeHtml(err.message)}
        </div>
      `;
      if (paginationContainer) paginationContainer.innerHTML = '';
    }
  }

  function renderPagination(currentPage, totalPages, totalItems) {
    const container = document.getElementById('events-pagination-container');
    if (!container) return;

    if (totalPages <= 1) {
      container.innerHTML = `<div class="text-muted text-center py-2">Showing all ${totalItems} events</div>`;
      return;
    }

    container.innerHTML = `
      <div class="pagination">
        <button class="btn btn-outline btn-sm" id="page-prev" ${currentPage <= 1 ? 'disabled' : ''}>
          &laquo; Previous
        </button>
        <span class="page-indicator">
          Page <strong>${currentPage}</strong> of <strong>${totalPages}</strong> (${totalItems} total)
        </span>
        <button class="btn btn-outline btn-sm" id="page-next" ${currentPage >= totalPages ? 'disabled' : ''}>
          Next &raquo;
        </button>
      </div>
    `;

    document.getElementById('page-prev').addEventListener('click', () => {
      if (currentParams.page > 1) {
        currentParams.page--;
        loadEvents();
      }
    });

    document.getElementById('page-next').addEventListener('click', () => {
      if (currentParams.page < totalPages) {
        currentParams.page++;
        loadEvents();
      }
    });
  }

  window.Views = window.Views || {};
  window.Views.Events = renderEvents;
})(window);
