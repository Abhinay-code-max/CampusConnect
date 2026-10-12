/**
 * CampusConnect Client-Side Hash Router
 * Handles hash navigation, role-based route protection, parameter extraction, and view mounting.
 */

(function (window) {
  'use strict';

  const routes = [];
  let currentRoute = null;

  function addRoute(pattern, handler, requiredRoles = null) {
    // Convert pattern like "/events/:id" to RegExp
    const paramNames = [];
    const regexPath = pattern.replace(/:([a-zA-Z0-9_]+)/g, (_, name) => {
      paramNames.push(name);
      return '([^\\/]+)';
    });
    const regex = new RegExp(`^${regexPath}$`);

    routes.push({
      pattern,
      regex,
      paramNames,
      handler,
      requiredRoles
    });
  }

  function navigate(path) {
    window.location.hash = path.startsWith('#') ? path : '#' + path;
  }

  async function handleRouting() {
    const hash = window.location.hash.slice(1) || '/';
    const [pathOnly, queryString] = hash.split('?');
    const queryParams = new URLSearchParams(queryString || '');

    let matchedRoute = null;
    let params = {};

    for (const route of routes) {
      const match = pathOnly.match(route.regex);
      if (match) {
        matchedRoute = route;
        route.paramNames.forEach((name, i) => {
          params[name] = decodeURIComponent(match[i + 1]);
        });
        break;
      }
    }

    const appContainer = document.getElementById('app-main');
    if (!appContainer) return;

    if (!matchedRoute) {
      appContainer.innerHTML = `
        <div class="container text-center py-5">
          <div class="empty-state">
            <h1 class="text-navy">404</h1>
            <h2>Page Not Found</h2>
            <p class="text-muted">The requested page does not exist or has been moved.</p>
            <a href="#/" class="btn btn-primary mt-3">Return to Home</a>
          </div>
        </div>
      `;
      updateActiveNav();
      return;
    }

    // Role check guard
    const user = window.Auth.getUser();
    if (matchedRoute.requiredRoles) {
      if (!user) {
        window.UI.showToast('Please log in to access this page.', 'warning');
        navigate(`/login?redirect=${encodeURIComponent(hash)}`);
        return;
      }

      if (!matchedRoute.requiredRoles.includes(user.role)) {
        appContainer.innerHTML = `
          <div class="container py-5">
            <div class="card p-4 text-center">
              <h2 class="text-danger">Access Denied (403)</h2>
              <p class="text-muted">Your account role (${user.role}) is not authorized to access this page.</p>
              <div class="mt-3">
                <a href="#/" class="btn btn-outline mr-2">Go to Homepage</a>
                <a href="${getRoleDashboard(user.role)}" class="btn btn-primary">Go to Your Dashboard</a>
              </div>
            </div>
          </div>
        `;
        updateActiveNav();
        return;
      }
    }

    currentRoute = matchedRoute;
    updateActiveNav();

    // Scroll to top on navigation
    window.scrollTo({ top: 0, behavior: 'instant' });

    try {
      await matchedRoute.handler(params, queryParams);
    } catch (err) {
      console.error('Route handler error:', err);
      appContainer.innerHTML = `
        <div class="container py-5">
          <div class="card card-danger p-4 text-center">
            <h2>Error Loading View</h2>
            <p class="text-muted">${window.UI.escapeHtml(err.message || 'An unexpected error occurred.')}</p>
            <button type="button" data-action="reload-app" class="btn btn-primary mt-3">Retry</button>
          </div>
        </div>
      `;
      appContainer.querySelector('[data-action="reload-app"]')
        ?.addEventListener('click', () => window.location.reload());
    }
  }

  function getRoleDashboard(role) {
    switch (role) {
      case 'STUDENT': return '#/student/dashboard';
      case 'ORGANIZER': return '#/organizer/dashboard';
      case 'JUDGE': return '#/judge/events';
      case 'ADMIN': return '#/admin/dashboard';
      default: return '#/events';
    }
  }

  function updateActiveNav() {
    const currentHash = window.location.hash || '#/';
    const links = document.querySelectorAll('.nav-link');
    links.forEach(link => {
      const href = link.getAttribute('href');
      if (href && (currentHash === href || (href !== '#/' && currentHash.startsWith(href)))) {
        link.classList.add('active');
      } else {
        link.classList.remove('active');
      }
    });
  }

  function initRouter() {
    // Re-evaluate route when auth state changes (e.g., login or logout on event detail)
    window.API.onAuthChange(() => {
      const hash = window.location.hash || '#/';
      if (hash.startsWith('#/events/')) {
        handleRouting();
      }
    });

    window.addEventListener('hashchange', handleRouting);
    handleRouting();
  }

  window.Router = {
    addRoute,
    navigate,
    initRouter,
    updateActiveNav,
    getRoleDashboard
  };
})(window);
