/**
 * CampusConnect Notification System
 * Listens for new events uploaded by Organizers/Admins,
 * alerts students with the event title and a brief description,
 * displays an unread notification count on a small bell icon,
 * and redirects to the happening events when pressed.
 */

(function (window) {
  'use strict';

  const STORAGE_SEEN_KEY = 'campusconnect_seen_event_ids';
  const CHANNEL_NAME = 'campusconnect_events_channel';

  let broadcastChannel = null;
  let pollIntervalId = null;
  let cachedEvents = [];
  let isDropdownOpen = false;

  // Initialize BroadcastChannel if supported
  if (typeof BroadcastChannel !== 'undefined') {
    try {
      broadcastChannel = new BroadcastChannel(CHANNEL_NAME);
    } catch (e) {
      console.warn('BroadcastChannel not supported in this context:', e);
    }
  }

  function getSeenEventIds() {
    try {
      const data = localStorage.getItem(STORAGE_SEEN_KEY);
      return data ? JSON.parse(data) : [];
    } catch {
      return [];
    }
  }

  function markEventsAsSeen(eventIds) {
    try {
      const seen = new Set(getSeenEventIds());
      eventIds.forEach(id => seen.add(String(id)));
      localStorage.setItem(STORAGE_SEEN_KEY, JSON.stringify([...seen]));
      updateBadge();
    } catch (err) {
      console.warn('Failed to save seen event IDs:', err);
    }
  }

  function getUnreadEvents() {
    const seen = new Set(getSeenEventIds().map(String));
    return cachedEvents.filter(ev => !seen.has(String(ev.eventId)));
  }

  function updateBadge() {
    const badge = document.getElementById('notification-badge');
    const bellBtn = document.getElementById('btn-notification-bell');
    if (!badge) return;

    const unread = getUnreadEvents();
    if (unread.length > 0) {
      badge.textContent = unread.length > 9 ? '9+' : String(unread.length);
      badge.style.display = 'inline-flex';
      if (bellBtn) bellBtn.classList.add('has-unread');
    } else {
      badge.style.display = 'none';
      if (bellBtn) bellBtn.classList.remove('has-unread');
    }
  }

  async function fetchHappeningEvents() {
    try {
      if (!window.API || !window.API.events) return;
      // Fetch public events (PUBLISHED / happening)
      const data = await window.API.events.listPublic({ pageSize: 20, sort: 'startsAt', direction: 'asc' });
      if (data && Array.isArray(data.events)) {
        const previousIds = new Set(cachedEvents.map(e => String(e.eventId)));
        cachedEvents = data.events;

        // Check if there are newly uploaded events that weren't cached before
        if (previousIds.size > 0) {
          const newArrivals = cachedEvents.filter(e => !previousIds.has(String(e.eventId)));
          newArrivals.forEach(ev => {
            showLiveEventNotification(ev);
          });
        }

        updateBadge();
        renderNotificationList();
      }
    } catch (err) {
      console.warn('Could not fetch happening events for notifications:', err);
    }
  }

  function showLiveEventNotification(event) {
    const title = event.eventName || 'New Event';
    const brief = getBriefDescription(event.description);
    
    // Display an interactive toast message
    if (window.UI && window.UI.showToast) {
      const toastContent = `🎉 Event Announcement: "${title}" is happening! ${brief ? '— ' + brief : ''}`;
      window.UI.showToast(toastContent, 'info', 7000);
    }
  }

  function getBriefDescription(desc, maxLength = 110) {
    if (!desc || !desc.trim()) return 'No description provided yet.';
    const clean = desc.trim().replace(/\s+/g, ' ');
    if (clean.length <= maxLength) return clean;
    return clean.substring(0, maxLength) + '...';
  }

  function renderNotificationList() {
    const container = document.getElementById('notification-items-container');
    if (!container) return;

    if (!cachedEvents || cachedEvents.length === 0) {
      container.innerHTML = `
        <div class="notification-empty text-center py-4">
          <div style="font-size: 2rem; margin-bottom: 0.5rem;">📅</div>
          <p class="font-medium text-navy mb-1">No Events Happening Right Now</p>
          <p class="text-xs text-muted mb-0">Check back soon for new announcements.</p>
        </div>
      `;
      return;
    }

    const seenSet = new Set(getSeenEventIds().map(String));

    let html = '';
    cachedEvents.slice(0, 8).forEach(ev => {
      const isUnread = !seenSet.has(String(ev.eventId));
      const briefDesc = getBriefDescription(ev.description);
      const modeLabel = ev.participationMode === 'TEAM' ? 'Team Event' : 'Individual Event';
      const dateText = window.UI && window.UI.formatDateTime ? window.UI.formatDateTime(ev.startsAt) : ev.startsAt;

      html += `
        <div class="notification-item ${isUnread ? 'is-unread' : ''}" data-event-id="${ev.eventId}">
          <div class="notification-item-header">
            <span class="notification-item-title font-bold">${window.UI.escapeHtml(ev.eventName)}</span>
            ${isUnread ? '<span class="notification-pill-new">NEW</span>' : ''}
          </div>
          <p class="notification-item-desc text-xs text-muted mb-1">
            <strong>Brief:</strong> ${window.UI.escapeHtml(briefDesc)}
          </p>
          <div class="notification-item-meta d-flex justify-between align-center">
            <span class="text-xs text-navy font-medium">🕒 Starts: ${window.UI.escapeHtml(dateText)}</span>
            <span class="badge badge-sm badge-secondary">${window.UI.escapeHtml(modeLabel)}</span>
          </div>
        </div>
      `;
    });

    container.innerHTML = html;

    // Attach click listeners to individual event items to navigate directly
    container.querySelectorAll('.notification-item').forEach(item => {
      item.addEventListener('click', (e) => {
        const id = item.dataset.eventId;
        if (id) {
          markEventsAsSeen([id]);
          closeDropdown();
          if (window.Router) {
            window.Router.navigate(`/events/${id}`);
          } else {
            window.location.hash = `#/events/${id}`;
          }
        }
      });
    });
  }

  function toggleDropdown() {
    const dropdown = document.getElementById('notification-dropdown');
    if (!dropdown) return;

    if (isDropdownOpen) {
      closeDropdown();
    } else {
      openDropdown();
    }
  }

  function openDropdown() {
    const dropdown = document.getElementById('notification-dropdown');
    const bellBtn = document.getElementById('btn-notification-bell');
    if (!dropdown) return;

    dropdown.style.display = 'block';
    isDropdownOpen = true;
    if (bellBtn) bellBtn.setAttribute('aria-expanded', 'true');

    // Mark current cached events as seen when opened
    if (cachedEvents.length > 0) {
      markEventsAsSeen(cachedEvents.map(e => e.eventId));
    }
  }

  function closeDropdown() {
    const dropdown = document.getElementById('notification-dropdown');
    const bellBtn = document.getElementById('btn-notification-bell');
    if (!dropdown) return;

    dropdown.style.display = 'none';
    isDropdownOpen = false;
    if (bellBtn) bellBtn.setAttribute('aria-expanded', 'false');
  }

  function handleBellPress(e) {
    e.preventDefault();
    e.stopPropagation();

    // Toggle the notification popover
    toggleDropdown();
  }

  function broadcastNewEvent(eventData) {
    if (!eventData) return;
    
    // Broadcast via BroadcastChannel
    if (broadcastChannel) {
      try {
        broadcastChannel.postMessage({
          type: 'EVENT_UPLOADED',
          event: eventData,
          timestamp: Date.now()
        });
      } catch (err) {
        console.warn('Failed to broadcast event:', err);
      }
    }

    // Immediately trigger local refresh
    fetchHappeningEvents();
  }

  function initNotifications() {
    const bellBtn = document.getElementById('btn-notification-bell');
    const closeBtn = document.getElementById('notification-close-btn');
    const viewAllBtn = document.getElementById('btn-redirect-events');

    if (bellBtn) {
      bellBtn.removeEventListener('click', handleBellPress);
      bellBtn.addEventListener('click', handleBellPress);
    }

    if (closeBtn) {
      closeBtn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        closeDropdown();
      });
    }

    if (viewAllBtn) {
      viewAllBtn.addEventListener('click', (e) => {
        closeDropdown();
        // Redirect to events happening catalog
        if (window.Router) {
          window.Router.navigate('/events');
        } else {
          window.location.hash = '#/events';
        }
      });
    }

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
      const wrapper = document.getElementById('notification-wrapper');
      if (wrapper && !wrapper.contains(e.target) && isDropdownOpen) {
        closeDropdown();
      }
    });

    // Close on Escape key
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && isDropdownOpen) {
        closeDropdown();
      }
    });

    // Listen for broadcast channel messages from organizer/admin uploads
    if (broadcastChannel) {
      broadcastChannel.onmessage = (messageEvent) => {
        if (messageEvent.data && messageEvent.data.type === 'EVENT_UPLOADED') {
          const ev = messageEvent.data.event;
          showLiveEventNotification(ev);
          fetchHappeningEvents();
        }
      };
    }

    // Initial fetch of happening events
    fetchHappeningEvents();

    // Polling interval every 12 seconds to catch newly uploaded events
    if (pollIntervalId) clearInterval(pollIntervalId);
    pollIntervalId = setInterval(fetchHappeningEvents, 12000);
  }

  // Export module globally
  window.Notifications = {
    init: initNotifications,
    fetchEvents: fetchHappeningEvents,
    broadcastNewEvent: broadcastNewEvent,
    markSeen: markEventsAsSeen
  };

  // Auto-init on DOMContentLoaded or immediate if already loaded
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initNotifications);
  } else {
    initNotifications();
  }

})(window);
