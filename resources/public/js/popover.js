/*
 * Generic popover shell. Uses HTML popover API; browser gives us light-dismiss
 * (click outside, Escape) and top-layer rendering.
 *
 * Expects in the DOM: #popover (popover="auto") containing #popover-content
 * and #popover-arrow. Content is rendered in by the caller.
 *
 * API:
 *   openPopover(anchor)     open at anchor rect; sets aria-expanded on anchor
 *   repositionPopover()     recompute position after content swap
 *
 * Auto-close:
 *   - On open, arm timer (IDLE_OPEN_MS, or data-dismiss on content root if set).
 *   - Pointer/focus inside popover cancels the timer.
 *   - Pointer leave or focus out arms a short timer (IDLE_LEAVE_MS).
 *   - On touch devices (hover:none) no idle timer arms; data-dismiss still does.
 */

(function () {
  var POPOVER_ID = "popover";
  var CONTENT_ID = "popover-content";
  var ARROW_ID = "popover-arrow";
  var PAD = 8;
  var OFFSET = 10;
  var IDLE_OPEN_MS = 3000;
  var IDLE_LEAVE_MS = 600;

  var currentAnchor = null;
  var autoTimer = null;

  function el(id) { return document.getElementById(id); }

  function position() {
    var pop = el(POPOVER_ID);
    var arrow = el(ARROW_ID);
    if (!pop || !currentAnchor || !pop.matches(":popover-open")) return;
    var a = currentAnchor.getBoundingClientRect();
    var pw = pop.offsetWidth;
    var ph = pop.offsetHeight;

    var centerX = a.left + a.width / 2;
    var left = Math.max(PAD, Math.min(window.innerWidth - pw - PAD, centerX - pw / 2));
    var above = a.top - ph - OFFSET;
    var flip = above < PAD && a.bottom + ph + OFFSET + PAD <= window.innerHeight;
    var top = flip ? a.bottom + OFFSET : above;

    pop.style.left = left + "px";
    pop.style.top = top + "px";
    pop.dataset.side = flip ? "bottom" : "top";
    arrow.style.left = Math.max(12, Math.min(pw - 12, centerX - left)) + "px";
  }

  function isActive() {
    var pop = el(POPOVER_ID);
    return !!pop && (pop.matches(":hover") || pop.contains(document.activeElement));
  }

  function clearAutoTimer() {
    if (autoTimer) { clearTimeout(autoTimer); autoTimer = null; }
  }

  function scheduleAutoClose(ms) {
    clearAutoTimer();
    if (!ms) return;
    var pop = el(POPOVER_ID);
    if (!pop) return;
    autoTimer = setTimeout(function () {
      if (pop.matches(":popover-open") && !isActive()) pop.hidePopover();
    }, ms);
  }

  function initialCloseMs() {
    var first = el(CONTENT_ID) && el(CONTENT_ID).firstElementChild;
    var forced = first && parseInt(first.getAttribute("data-dismiss") || "0", 10);
    if (forced > 0) return forced;
    var hoverCapable = window.matchMedia && window.matchMedia("(hover: hover)").matches;
    return hoverCapable ? IDLE_OPEN_MS : 0;
  }

  window.openPopover = function (anchor) {
    var pop = el(POPOVER_ID);
    if (!pop) return;
    if (currentAnchor) currentAnchor.setAttribute("aria-expanded", "false");
    currentAnchor = anchor;
    if (anchor) anchor.setAttribute("aria-expanded", "true");
    if (!pop.matches(":popover-open")) pop.showPopover();
    requestAnimationFrame(function () {
      position();
      scheduleAutoClose(isActive() ? 0 : initialCloseMs());
    });
  };

  window.repositionPopover = function () {
    requestAnimationFrame(function () {
      position();
      scheduleAutoClose(isActive() ? 0 : initialCloseMs());
    });
  };

  // Always-on; position() bails when popover is closed, so this is a cheap no-op.
  window.addEventListener("resize", position, { passive: true });
  window.addEventListener("scroll", position, { passive: true, capture: true });

  document.addEventListener("DOMContentLoaded", function () {
    var pop = el(POPOVER_ID);
    if (!pop) return;
    pop.addEventListener("toggle", function (e) {
      if (e.newState !== "closed") return;
      clearAutoTimer();
      if (currentAnchor) currentAnchor.setAttribute("aria-expanded", "false");
      currentAnchor = null;
      var content = el(CONTENT_ID);
      if (content) content.innerHTML = "";
    });
    pop.addEventListener("pointerenter", clearAutoTimer);
    pop.addEventListener("pointerleave", function () { scheduleAutoClose(IDLE_LEAVE_MS); });
    pop.addEventListener("focusin", clearAutoTimer);
    pop.addEventListener("focusout", function () {
      setTimeout(function () { if (!isActive()) scheduleAutoClose(IDLE_LEAVE_MS); }, 0);
    });
  });
})();
