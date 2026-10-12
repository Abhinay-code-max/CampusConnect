/**
 * CampusConnect API Client
 * Shared fetch helper, session tracking, and CSRF token management.
 * Complies with Phase 4-8 specifications.
 */

(function (window) {
  'use strict';

  // Determine base API path relative to application context
  function getApiBase() {
    const pathname = window.location.pathname;
    const marker = '/CampusConnect';
    const index = pathname.indexOf(marker);
    if (index !== -1) {
      return marker + '/api';
    }
    // Fallback if hosted at root or custom context
    return '/api';
  }

  const API_BASE = getApiBase();

  // In-memory state: NEVER store session identifiers or credentials in localStorage
  let inMemoryCsrfToken = null;
  let inMemoryUser = null;
  let authListeners = [];

  function notifyAuthChange(user) {
    inMemoryUser = user;
    authListeners.forEach(listener => {
      try {
        listener(user);
      } catch (err) {
        console.error('Error in auth listener:', err);
      }
    });
  }

  async function request(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
    const method = (options.method || 'GET').toUpperCase();

    const headers = {
      'Accept': 'application/json',
      ...(options.headers || {})
    };

    // State-changing requests require X-CSRF-Token
    const isStateChanging = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method);
    if (isStateChanging && inMemoryCsrfToken) {
      headers['X-CSRF-Token'] = inMemoryCsrfToken;
    }

    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(options.body);
    }

    const fetchConfig = {
      method,
      headers,
      credentials: 'include', // Ensure Tomcat JSESSIONID cookie is sent
      ...options
    };

    let response;
    try {
      response = await fetch(url, fetchConfig);
    } catch (netErr) {
      throw new Error(`Network failure: Unable to reach the server. (${netErr.message || 'Connection refused'})`);
    }

    // Try parsing JSON response
    let data = null;
    const contentType = response.headers.get('Content-Type') || '';
    if (contentType.includes('application/json')) {
      try {
        data = await response.json();
      } catch (jsonErr) {
        // Fallback for non-JSON or truncated responses
      }
    }

    // Update in-memory CSRF token if returned in response payload
    if (data && data.csrfToken) {
      inMemoryCsrfToken = data.csrfToken;
    }

    if (!response.ok) {
      let errorMessage = 'Request failed';
      if (data && data.message) {
        errorMessage = data.message;
      } else {
        switch (response.status) {
          case 400: errorMessage = 'Invalid request parameters or payload.'; break;
          case 401: errorMessage = 'Authentication required. Please log in.'; break;
          case 403: errorMessage = 'Access denied. You do not have permission for this action.'; break;
          case 404: errorMessage = 'Requested resource not found.'; break;
          case 409: errorMessage = 'Conflict: The action conflicts with current business rules or state.'; break;
          case 500: errorMessage = 'Server encountered an internal error. Please try again later.'; break;
          default: errorMessage = `Server responded with status ${response.status}.`; break;
        }
      }

      // If session expired or unauthorized on protected route, update state
      if (response.status === 401 && !endpoint.includes('/auth/login') && !endpoint.includes('/auth/register')) {
        inMemoryCsrfToken = null;
        notifyAuthChange(null);
      }

      const error = new Error(errorMessage);
      error.status = response.status;
      error.data = data;
      throw error;
    }

    return data;
  }

  const API = {
    getBaseUrl: () => API_BASE,
    getUser: () => inMemoryUser,
    getCsrfToken: () => inMemoryCsrfToken,
    onAuthChange: (listener) => {
      authListeners.push(listener);
      return () => {
        authListeners = authListeners.filter(l => l !== listener);
      };
    },

    // --- AUTHENTICATION (Phase 4) ---
    auth: {
      async register(displayName, email, password) {
        // Only displayName, email, password permitted
        return await request('/auth/register', {
          method: 'POST',
          body: { displayName, email, password }
        });
      },

      async login(email, password) {
        const res = await request('/auth/login', {
          method: 'POST',
          body: { email, password }
        });
        if (res.csrfToken) {
          inMemoryCsrfToken = res.csrfToken;
        }
        notifyAuthChange(res.user);
        return res;
      },

      async me() {
        try {
          const res = await request('/auth/me', { method: 'GET' });
          if (res.csrfToken) {
            inMemoryCsrfToken = res.csrfToken;
          }
          notifyAuthChange(res.user);
          return res.user;
        } catch (err) {
          inMemoryCsrfToken = null;
          notifyAuthChange(null);
          return null;
        }
      },

      async logout() {
        try {
          await request('/auth/logout', {
            method: 'POST',
            body: {}
          });
        } finally {
          inMemoryCsrfToken = null;
          notifyAuthChange(null);
        }
      }
    },

    // --- PUBLIC EVENTS & RESULTS (Phase 5, 7) ---
    events: {
      async listPublic(params = {}) {
        const query = new URLSearchParams();
        for (const [k, v] of Object.entries(params)) {
          if (v !== undefined && v !== null && v !== '') {
            query.append(k, v);
          }
        }
        const qs = query.toString();
        return await request(`/events${qs ? '?' + qs : ''}`);
      },

      async getPublic(eventId) {
        return await request(`/events/${encodeURIComponent(eventId)}`);
      },

      async getPublicResults(eventId, params = {}) {
        const query = new URLSearchParams();
        if (params.page) query.append('page', params.page);
        if (params.pageSize) query.append('pageSize', params.pageSize);
        const qs = query.toString();
        return await request(`/results/events/${encodeURIComponent(eventId)}${qs ? '?' + qs : ''}`);
      }
    },

    // --- STUDENT PARTICIPATION & TEAMS (Phase 6) ---
    student: {
      async getTeams() {
        return await request('/teams');
      },

      async getTeam(teamId) {
        return await request(`/teams/${encodeURIComponent(teamId)}`);
      },

      async createTeam(teamName) {
        return await request('/teams', {
          method: 'POST',
          body: { teamName }
        });
      },

      async renameTeam(teamId, teamName) {
        return await request(`/teams/${encodeURIComponent(teamId)}`, {
          method: 'PUT',
          body: { teamName }
        });
      },

      async inviteMember(teamId, inviteeUserId) {
        return await request(`/teams/${encodeURIComponent(teamId)}/invitations`, {
          method: 'POST',
          body: { inviteeUserId: Number(inviteeUserId) }
        });
      },

      async revokeInvitation(teamId, invitationId) {
        return await request(`/teams/${encodeURIComponent(teamId)}/invitations/${encodeURIComponent(invitationId)}`, {
          method: 'DELETE'
        });
      },

      async removeMember(teamId, userId) {
        return await request(`/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(userId)}`, {
          method: 'DELETE'
        });
      },

      async leaveTeam(teamId) {
        return await request(`/teams/${encodeURIComponent(teamId)}/leave`, {
          method: 'POST',
          body: {}
        });
      },

      async getInvitations() {
        return await request('/team-invitations');
      },

      async acceptInvitation(invitationId) {
        return await request(`/team-invitations/${encodeURIComponent(invitationId)}/accept`, {
          method: 'POST',
          body: {}
        });
      },

      async declineInvitation(invitationId) {
        return await request(`/team-invitations/${encodeURIComponent(invitationId)}/decline`, {
          method: 'POST',
          body: {}
        });
      },

      async registerIndividual(eventId) {
        return await request('/registrations/individual', {
          method: 'POST',
          body: { eventId: Number(eventId) }
        });
      },

      async registerTeam(teamId, eventId) {
        return await request(`/registrations/teams/${encodeURIComponent(teamId)}`, {
          method: 'POST',
          body: { eventId: Number(eventId) }
        });
      },

      async getRegistrations(page = 1, pageSize = 20) {
        return await request(`/registrations?page=${page}&pageSize=${pageSize}`);
      },

      async getRegistration(registrationId) {
        return await request(`/registrations/${encodeURIComponent(registrationId)}`);
      },

      async cancelRegistration(registrationId) {
        return await request(`/registrations/${encodeURIComponent(registrationId)}/cancel`, {
          method: 'POST',
          body: {}
        });
      }
    },

    // --- ORGANIZER & EVENT MANAGEMENT (Phase 5, 6, 7) ---
    organizer: {
      async listEvents(params = {}) {
        const query = new URLSearchParams();
        for (const [k, v] of Object.entries(params)) {
          if (v !== undefined && v !== null && v !== '') {
            query.append(k, v);
          }
        }
        const qs = query.toString();
        return await request(`/manage/events${qs ? '?' + qs : ''}`);
      },

      async getManagedEvent(eventId) {
        return await request(`/manage/events/${encodeURIComponent(eventId)}`);
      },

      async getEvent(eventId) {
        return await request(`/manage/events/${encodeURIComponent(eventId)}`);
      },

      async createEvent(eventData) {
        return await request('/manage/events', {
          method: 'POST',
          body: eventData
        });
      },

      async updateEvent(eventId, eventData) {
        return await request(`/manage/events/${encodeURIComponent(eventId)}`, {
          method: 'PUT',
          body: eventData
        });
      },

      async publishEvent(eventId) {
        return await request(`/manage/events/${encodeURIComponent(eventId)}/publish`, {
          method: 'POST',
          body: {}
        });
      },

      async closeRegistration(eventId) {
        return await request(`/manage/events/${encodeURIComponent(eventId)}/close-registration`, {
          method: 'POST',
          body: {}
        });
      },

      async cancelEvent(eventId) {
        return await request(`/manage/events/${encodeURIComponent(eventId)}/cancel`, {
          method: 'POST',
          body: {}
        });
      },

      async getRegistrations(eventId, page = 1, pageSize = 50) {
        return await request(`/manage/event-registrations/${encodeURIComponent(eventId)}?page=${page}&pageSize=${pageSize}`);
      },

      async getJudges(eventId) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/judges`);
      },

      async assignJudge(eventId, judgeUserId) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/judges`, {
          method: 'POST',
          body: { judgeUserId: Number(judgeUserId) }
        });
      },

      async unassignJudge(eventId, judgeUserId) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/judges/${encodeURIComponent(judgeUserId)}`, {
          method: 'DELETE'
        });
      },

      async getCriteria(eventId) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/criteria`);
      },

      async createCriterion(eventId, criterionData) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/criteria`, {
          method: 'POST',
          body: criterionData
        });
      },

      async updateCriterion(eventId, criterionId, criterionData) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/criteria/${encodeURIComponent(criterionId)}`, {
          method: 'PUT',
          body: criterionData
        });
      },

      async setScoringWindow(eventId, windowData) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/scoring-window`, {
          method: 'PUT',
          body: windowData
        });
      },

      async closeScoring(eventId) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/close-scoring`, {
          method: 'POST',
          body: {}
        });
      },

      async finalizeResults(eventId) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/finalize-results`, {
          method: 'POST',
          body: {}
        });
      },

      async publishResults(eventId) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/publish-results`, {
          method: 'POST',
          body: {}
        });
      },

      async getResultsPreview(eventId, page = 1, pageSize = 50) {
        return await request(`/manage/scoring/events/${encodeURIComponent(eventId)}/results?page=${page}&pageSize=${pageSize}`);
      }
    },

    // --- JUDGE (Phase 7) ---
    judge: {
      async getAssignedEvents() {
        return await request('/judging/events');
      },

      async getJudgingEvent(eventId) {
        return await request(`/judging/events/${encodeURIComponent(eventId)}`);
      },

      async getScores(eventId, registrationId) {
        return await request(`/judging/events/${encodeURIComponent(eventId)}/registrations/${encodeURIComponent(registrationId)}/scores`);
      },

      async submitScores(eventId, registrationId, scoresArray) {
        return await request(`/judging/events/${encodeURIComponent(eventId)}/registrations/${encodeURIComponent(registrationId)}/scores`, {
          method: 'PUT',
          body: { scores: scoresArray }
        });
      }
    },

    // --- ADMIN (Phase 8) ---
    admin: {
      async getDashboard() {
        return await request('/admin/dashboard');
      },

      async getUsers(params = {}) {
        const query = new URLSearchParams();
        for (const [k, v] of Object.entries(params)) {
          if (v !== undefined && v !== null && v !== '') {
            query.append(k, v);
          }
        }
        const qs = query.toString();
        return await request(`/admin/users${qs ? '?' + qs : ''}`);
      },

      async getUser(userId) {
        return await request(`/admin/users/${encodeURIComponent(userId)}`);
      },

      async updateRole(userId, role) {
        return await request(`/admin/users/${encodeURIComponent(userId)}/role`, {
          method: 'PUT',
          body: { role }
        });
      },

      async updateStatus(userId, active) {
        return await request(`/admin/users/${encodeURIComponent(userId)}/status`, {
          method: 'PUT',
          body: { active: Boolean(active) }
        });
      },

      async getEvents(params = {}) {
        const query = new URLSearchParams();
        for (const [k, v] of Object.entries(params)) {
          if (v !== undefined && v !== null && v !== '') {
            query.append(k, v);
          }
        }
        const qs = query.toString();
        return await request(`/admin/events${qs ? '?' + qs : ''}`);
      },

      async getRegistrations(params = {}) {
        const query = new URLSearchParams();
        if (params.eventId) query.append('eventId', params.eventId);
        if (params.status) query.append('status', params.status);
        if (params.page) query.append('page', params.page);
        if (params.pageSize) query.append('pageSize', params.pageSize);
        const qs = query.toString();
        return await request(`/admin/registrations${qs ? '?' + qs : ''}`);
      },

      async getAudit(params = {}) {
        const query = new URLSearchParams();
        if (params.actorUserId) query.append('actorUserId', params.actorUserId);
        if (params.targetUserId) query.append('targetUserId', params.targetUserId);
        if (params.action) query.append('action', params.action);
        if (params.page) query.append('page', params.page);
        if (params.pageSize) query.append('pageSize', params.pageSize);
        const qs = query.toString();
        return await request(`/admin/audit${qs ? '?' + qs : ''}`);
      }
    }
  };

  window.API = API;
})(window);
