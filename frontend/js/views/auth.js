/**
 * CampusConnect Auth Views
 * Login and registration forms with validation and accessible feedback.
 */

(function (window) {
  'use strict';

  function renderLogin(params, queryParams) {
    const main = document.getElementById('app-main');
    if (!main) return;

    if (window.Auth.isLoggedIn()) {
      window.Router.navigate(window.Router.getRoleDashboard(window.Auth.getUser().role));
      return;
    }

    const redirectPath = queryParams.get('redirect') || '';

    main.innerHTML = `
      <div class="container py-5">
        <div class="auth-card-wrapper mx-auto">
          <div class="card p-4">
            <div class="text-center mb-4">
              <h1 class="page-title text-navy">Welcome Back</h1>
              <p class="text-muted">Log in to your CampusConnect account</p>
            </div>

            <div id="login-alert-container"></div>

            <form id="login-form" novalidate>
              <div class="form-group mb-3">
                <label for="login-email" class="form-label">Email Address</label>
                <input type="email" id="login-email" class="form-control" autocomplete="email" required placeholder="student@example.edu">
                <div class="invalid-feedback" id="login-email-error"></div>
              </div>

              <div class="form-group mb-4">
                <label for="login-password" class="form-label">Password</label>
                <input type="password" id="login-password" class="form-control" autocomplete="current-password" required placeholder="Your password">
                <div class="invalid-feedback" id="login-password-error"></div>
              </div>

              <button type="submit" id="btn-login-submit" class="btn btn-primary btn-block btn-lg">
                Log In
              </button>
            </form>

            <div class="text-center mt-4 pt-3 border-top">
              <p class="text-muted text-sm">
                Don't have an account yet? 
                <a href="#/register${redirectPath ? '?redirect=' + encodeURIComponent(redirectPath) : ''}" class="font-medium">
                  Register as a Student
                </a>
              </p>
            </div>
          </div>
        </div>
      </div>
    `;

    const form = document.getElementById('login-form');
    const submitBtn = document.getElementById('btn-login-submit');
    const alertContainer = document.getElementById('login-alert-container');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertContainer.innerHTML = '';

      const email = document.getElementById('login-email').value.trim();
      const password = document.getElementById('login-password').value;

      if (!email) {
        document.getElementById('login-email-error').textContent = 'Please enter your email address.';
        document.getElementById('login-email').focus();
        return;
      }
      if (!password) {
        document.getElementById('login-password-error').textContent = 'Please enter your password.';
        document.getElementById('login-password').focus();
        return;
      }

      window.UI.setButtonLoading(submitBtn, true, 'Signing in...');

      try {
        const res = await window.API.auth.login(email, password);
        window.UI.showToast(`Welcome back, ${res.user.displayName}!`, 'success');

        if (redirectPath) {
          window.Router.navigate(redirectPath);
        } else {
          window.Router.navigate(window.Router.getRoleDashboard(res.user.role));
        }
      } catch (err) {
        alertContainer.innerHTML = `
          <div class="alert alert-error mb-3" role="alert">
            ${window.UI.escapeHtml(err.message || 'Invalid email or password.')}
          </div>
        `;
        window.UI.setButtonLoading(submitBtn, false);
      }
    });
  }

  function renderRegister(params, queryParams) {
    const main = document.getElementById('app-main');
    if (!main) return;

    if (window.Auth.isLoggedIn()) {
      window.Router.navigate(window.Router.getRoleDashboard(window.Auth.getUser().role));
      return;
    }

    const redirectPath = queryParams.get('redirect') || '';

    main.innerHTML = `
      <div class="container py-5">
        <div class="auth-card-wrapper mx-auto">
          <div class="card p-4">
            <div class="text-center mb-4">
              <h1 class="page-title text-navy">Create Account</h1>
              <p class="text-muted">Register as a Student for campus competitions</p>
            </div>

            <div id="register-alert-container"></div>

            <form id="register-form" novalidate>
              <div class="form-group mb-3">
                <label for="reg-name" class="form-label">Full Display Name</label>
                <input type="text" id="reg-name" class="form-control" maxlength="120" required placeholder="e.g. Alex Johnson">
                <small class="form-text text-muted">1–120 characters.</small>
                <div class="invalid-feedback" id="reg-name-error"></div>
              </div>

              <div class="form-group mb-3">
                <label for="reg-email" class="form-label">Email Address</label>
                <input type="email" id="reg-email" class="form-control" autocomplete="email" required placeholder="student@example.edu">
                <small class="form-text text-muted">Must be a valid email address.</small>
                <div class="invalid-feedback" id="reg-email-error"></div>
              </div>

              <div class="form-group mb-3">
                <label for="reg-password" class="form-label">Password</label>
                <input type="password" id="reg-password" class="form-control" autocomplete="new-password" minlength="12" maxlength="128" required placeholder="Minimum 12 characters">
                <small class="form-text text-muted">12–128 characters. Spaces and passphrases are supported.</small>
                <div class="invalid-feedback" id="reg-password-error"></div>
              </div>

              <div class="form-group mb-4">
                <label for="reg-password-confirm" class="form-label">Confirm Password</label>
                <input type="password" id="reg-password-confirm" class="form-control" autocomplete="new-password" required placeholder="Re-type your password">
                <div class="invalid-feedback" id="reg-confirm-error"></div>
              </div>

              <button type="submit" id="btn-register-submit" class="btn btn-primary btn-block btn-lg">
                Create Student Account
              </button>
            </form>

            <div class="text-center mt-4 pt-3 border-top">
              <p class="text-muted text-sm">
                Already have an account? 
                <a href="#/login${redirectPath ? '?redirect=' + encodeURIComponent(redirectPath) : ''}" class="font-medium">
                  Log In
                </a>
              </p>
            </div>
          </div>
        </div>
      </div>
    `;

    const form = document.getElementById('register-form');
    const submitBtn = document.getElementById('btn-register-submit');
    const alertContainer = document.getElementById('register-alert-container');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertContainer.innerHTML = '';

      const name = document.getElementById('reg-name').value.trim();
      const email = document.getElementById('reg-email').value.trim();
      const password = document.getElementById('reg-password').value;
      const confirmPassword = document.getElementById('reg-password-confirm').value;

      // Validation
      if (!name || name.length < 1 || name.length > 120) {
        alertContainer.innerHTML = '<div class="alert alert-error">Display name must be between 1 and 120 characters.</div>';
        document.getElementById('reg-name').focus();
        return;
      }
      if (!email || !email.includes('@')) {
        alertContainer.innerHTML = '<div class="alert alert-error">Please provide a valid email address.</div>';
        document.getElementById('reg-email').focus();
        return;
      }
      if (!password || password.length < 12 || password.length > 128) {
        alertContainer.innerHTML = '<div class="alert alert-error">Password must be between 12 and 128 characters.</div>';
        document.getElementById('reg-password').focus();
        return;
      }
      if (password !== confirmPassword) {
        alertContainer.innerHTML = '<div class="alert alert-error">Passwords do not match. Please re-enter.</div>';
        document.getElementById('reg-password-confirm').focus();
        return;
      }

      window.UI.setButtonLoading(submitBtn, true, 'Creating account...');

      try {
        await window.API.auth.register(name, email, password);
        window.UI.showToast('Account created successfully! Please log in.', 'success');

        // Automatically log in the student after registration for smooth user experience
        try {
          const loginRes = await window.API.auth.login(email, password);
          if (redirectPath) {
            window.Router.navigate(redirectPath);
          } else {
            window.Router.navigate(window.Router.getRoleDashboard(loginRes.user.role));
          }
        } catch {
          window.Router.navigate('/login');
        }
      } catch (err) {
        alertContainer.innerHTML = `
          <div class="alert alert-error mb-3" role="alert">
            ${window.UI.escapeHtml(err.message || 'Registration failed. Please check your inputs.')}
          </div>
        `;
        window.UI.setButtonLoading(submitBtn, false);
      }
    });
  }

  window.Views = window.Views || {};
  window.Views.Login = renderLogin;
  window.Views.Register = renderRegister;
})(window);
