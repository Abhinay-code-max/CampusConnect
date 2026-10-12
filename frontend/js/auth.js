/**
 * CampusConnect Auth Controller
 * Coordinates authentication state, header navbar updates, and role-based permissions.
 */

(function (window) {
  'use strict';

  let currentUser = null;

  async function initAuth() {
    try {
      currentUser = await window.API.auth.me();
    } catch {
      currentUser = null;
    }
    updateNavUI(currentUser);
    return currentUser;
  }

  function getUser() {
    return currentUser;
  }

  function isLoggedIn() {
    return currentUser !== null;
  }

  function hasRole(...roles) {
    if (!currentUser || !currentUser.role) return false;
    return roles.includes(currentUser.role);
  }

  function updateNavUI(user) {
    const authNav = document.getElementById('auth-nav-items');
    const guestNav = document.getElementById('guest-nav-items');
    const userDisplay = document.getElementById('user-display-name');
    const userRoleBadge = document.getElementById('user-role-badge');
    const roleNavLinks = document.getElementById('role-nav-links');

    if (!authNav || !guestNav) return;

    if (user) {
      guestNav.style.display = 'none';
      authNav.style.display = 'flex';

      if (userDisplay) {
        userDisplay.textContent = user.displayName;
      }
      if (userRoleBadge) {
        userRoleBadge.textContent = user.role;
        userRoleBadge.className = `role-badge role-${user.role.toLowerCase()}`;
      }

      // Generate role-specific navigation links
      if (roleNavLinks) {
        let linksHtml = '';

        if (user.role === 'STUDENT') {
          linksHtml = `
            <a href="#/student/dashboard" class="nav-link" data-route="/student/dashboard">Student Dashboard</a>
            <a href="#/student/registrations" class="nav-link" data-route="/student/registrations">My Registrations</a>
            <a href="#/student/teams" class="nav-link" data-route="/student/teams">My Teams</a>
          `;
        } else if (user.role === 'ORGANIZER') {
          linksHtml = `
            <a href="#/organizer/dashboard" class="nav-link" data-route="/organizer/dashboard">Organizer Dashboard</a>
            <a href="#/organizer/events/new" class="nav-link" data-route="/organizer/events/new">+ Create Event</a>
          `;
        } else if (user.role === 'JUDGE') {
          linksHtml = `
            <a href="#/judge/events" class="nav-link" data-route="/judge/events">Judging Dashboard</a>
          `;
        } else if (user.role === 'ADMIN') {
          linksHtml = `
            <a href="#/admin/dashboard" class="nav-link" data-route="/admin/dashboard">Admin Dashboard</a>
            <a href="#/admin/users" class="nav-link" data-route="/admin/users">Users</a>
            <a href="#/admin/events" class="nav-link" data-route="/admin/events">Platform Events</a>
            <a href="#/admin/registrations" class="nav-link" data-route="/admin/registrations">Registrations</a>
            <a href="#/admin/audit" class="nav-link" data-route="/admin/audit">Audit History</a>
          `;
        }

        roleNavLinks.innerHTML = linksHtml;
      }
    } else {
      guestNav.style.display = 'flex';
      authNav.style.display = 'none';
      if (roleNavLinks) roleNavLinks.innerHTML = '';
    }

    // Refresh active nav link highlight
    if (window.Router && window.Router.updateActiveNav) {
      window.Router.updateActiveNav();
    }
  }

  // Subscribe to API auth changes
  window.API.onAuthChange((user) => {
    currentUser = user;
    updateNavUI(user);
  });

  window.Auth = {
    initAuth,
    getUser,
    isLoggedIn,
    hasRole,
    updateNavUI
  };
})(window);
