(function () {
    if (window.nitteriumReadingPosition) return;
    window.nitteriumReadingPosition = true;
    var prefix = 'nitterium-position-v1:';
    var entry = history.state && history.state.nitteriumEntry;
    if (!entry) {
        entry = Date.now().toString(36) + Math.random().toString(36).slice(2);
        history.replaceState(Object.assign({}, history.state, {nitteriumEntry: entry}), '');
    }
    var key = prefix + entry;
    var restoring = false;
    var timer;
    function timeline() { return document.querySelector('.timeline'); }
    function save(snapshot) {
        if (restoring) return;
        try {
            var old = JSON.parse(sessionStorage.getItem(key) || '{}');
            var rows = document.querySelectorAll('.timeline-item');
            var anchor = null;
            for (var i = 0; i < rows.length; i++) {
                if (rows[i].getBoundingClientRect().bottom > 0) {
                    var link = rows[i].querySelector('a.tweet-link, .tweet-date a[href*="/status/"]');
                    if (link) anchor = {href: link.getAttribute('href'), offset: rows[i].getBoundingClientRect().top};
                    break;
                }
            }
            var list = timeline();
            var html = snapshot && list ? list.innerHTML : old.html;
            if (html && html.length > 1500000) html = null;
            sessionStorage.setItem(key, JSON.stringify({x: scrollX, y: scrollY, anchor: anchor, html: html}));
            var keys = Object.keys(sessionStorage).filter(function(k) { return k.indexOf(prefix) === 0; });
            while (keys.length > 16) sessionStorage.removeItem(keys.shift());
        } catch (_) { /* Storage may be disabled or full; native history still works. */ }
    }
    function restore() {
        var saved;
        try { saved = JSON.parse(sessionStorage.getItem(key) || 'null'); } catch (_) {}
        if (!saved) return;
        restoring = true;
        var list = timeline();
        // Preserve posts loaded by infinite scrolling, including their pagination link.
        if (saved.html && list) list.innerHTML = saved.html;
        var deadline = Date.now() + 6000;
        function apply() {
            if (!restoring) return;
            var target = null;
            if (saved.anchor) {
                var links = document.querySelectorAll('a.tweet-link, .tweet-date a[href*="/status/"]');
                for (var i = 0; i < links.length; i++) {
                    if (links[i].getAttribute('href') === saved.anchor.href) {
                        target = links[i].closest('.timeline-item');
                        if (target) break;
                    }
                }
            }
            scrollTo(saved.x || 0, target ? scrollY + target.getBoundingClientRect().top - saved.anchor.offset : saved.y);
            if (Date.now() < deadline) timer = setTimeout(apply, 150);
            else restoring = false;
        }
        requestAnimationFrame(apply);
    }
    function stopRestoring() { restoring = false; clearTimeout(timer); }
    ['touchstart', 'wheel', 'keydown'].forEach(function(event) {
        addEventListener(event, stopRestoring, {passive: true});
    });
    document.addEventListener('click', function(event) {
        var link = event.target.closest && event.target.closest('a[href]');
        if (link) { stopRestoring(); save(true); }
    }, true);
    addEventListener('pagehide', function() { save(true); });
    addEventListener('pageshow', restore);
    var scrollTimer;
    addEventListener('scroll', function() {
        clearTimeout(scrollTimer);
        scrollTimer = setTimeout(function() { save(false); }, 200);
    }, {passive: true});
    restore();
})();
