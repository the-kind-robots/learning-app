(function () {
    "use strict";

    if (!("virtualKeyboard" in navigator)) return;
    if (!navigator.virtualKeyboard) return;

    function sync() {
        var managed = !!document.querySelector("[data-vk-overlay]");
        try {
            navigator.virtualKeyboard.overlaysContent = managed;
        } catch (_err) {
            // Ignore failures (unsupported, non-secure context, etc.).
        }
    }

    sync();
    document.body.addEventListener("htmx:afterSwap", sync);
    window.addEventListener("pageshow", sync);
})();
