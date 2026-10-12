/**
 * CampusConnect Main Application Bootstrapper
 * Registers application routes, binds global navigation events,
 * and initializes in-memory authentication from session cookies.
 */

(function (window) {
  'use strict';

  async function initApp() {
    // Register Public Routes
    window.Router.addRoute('/', window.Views.Home);
    window.Router.addRoute('/events', window.Views.Events);
    window.Router.addRoute('/events/:id', window.Views.EventDetail);
    window.Router.addRoute('/login', window.Views.Login);
    window.Router.addRoute('/register', window.Views.Register);

    // Register Student Routes (role: STUDENT)
    window.Router.addRoute('/student/dashboard', window.Views.StudentDashboard, ['STUDENT']);
    window.Router.addRoute('/student/registrations', window.Views.StudentRegistrations, ['STUDENT']);
    window.Router.addRoute('/student/teams', window.Views.StudentTeams, ['STUDENT']);

    // Register Organizer Routes (role: ORGANIZER, ADMIN)
    window.Router.addRoute('/organizer/dashboard', window.Views.OrganizerDashboard, ['ORGANIZER', 'ADMIN']);
    window.Router.addRoute('/organizer/events/new', window.Views.OrganizerEventForm, ['ORGANIZER', 'ADMIN']);
    window.Router.addRoute('/organizer/events/:id/edit', window.Views.OrganizerEventForm, ['ORGANIZER', 'ADMIN']);
    window.Router.addRoute('/organizer/events/:id/registrations', window.Views.OrganizerRegistrations, ['ORGANIZER', 'ADMIN']);
    window.Router.addRoute('/organizer/events/:id/scoring', window.Views.OrganizerScoring, ['ORGANIZER', 'ADMIN']);

    // Register Judge Routes (role: JUDGE)
    window.Router.addRoute('/judge/events', window.Views.JudgeAssignedEvents, ['JUDGE']);
    window.Router.addRoute('/judge/events/:id', window.Views.JudgeSheet, ['JUDGE']);

    // Register Admin Routes (role: ADMIN)
    window.Router.addRoute('/admin/dashboard', window.Views.AdminDashboard, ['ADMIN']);
    window.Router.addRoute('/admin/users', window.Views.AdminUsers, ['ADMIN']);
    window.Router.addRoute('/admin/events', window.Views.AdminEvents, ['ADMIN']);
    window.Router.addRoute('/admin/registrations', window.Views.AdminRegistrations, ['ADMIN']);
    window.Router.addRoute('/admin/audit', window.Views.AdminAudit, ['ADMIN']);

    // Bind Global Header Logout Button
    const logoutBtn = document.getElementById('btn-header-logout');
    if (logoutBtn) {
      logoutBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        try {
          await window.API.auth.logout();
          window.UI.showToast('You have been logged out safely.', 'info');
          window.Router.navigate('/events');
        } catch (err) {
          window.UI.showToast(err.message, 'error');
        }
      });
    }

    // Bind Mobile Navigation Hamburger
    const mobileToggle = document.getElementById('mobile-nav-toggle');
    const navLinks = document.getElementById('main-nav-links');
    if (mobileToggle && navLinks) {
      mobileToggle.addEventListener('click', () => {
        navLinks.classList.toggle('mobile-open');
      });

      // Close mobile menu when a nav link is clicked
      navLinks.addEventListener('click', (e) => {
        if (e.target.tagName === 'A' || e.target.closest('a')) {
          navLinks.classList.remove('mobile-open');
        }
      });
    }

    // Initialize Auth Session Check via /api/auth/me
    await window.Auth.initAuth();

    // Start Routing
    window.Router.initRouter();
  }

  // Boot on DOMContentLoaded
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initApp);
  } else {
    initApp();
  }
})(window);
