/* global window, fetch */
(function () {
  var m = window.location.pathname.match(/^\/annonce\/([a-f0-9-]+)/i);
  if (m) {
    var id = m[1];
    window.__PREFETCHED_ANNONCE_ID__ = id;
    window.__PREFETCHED_ANNONCE_PROMISE__ = fetch('/api/v1/annonces/' + id, {
      headers: { Accept: 'application/json' },
    })
      .then(function (r) {
        if (r.ok) {
          return r.json().then(function (data) {
            return { status: r.status, ok: true, data: data };
          });
        }
        return { status: r.status, ok: false, data: null };
      })
      .catch(function (err) {
        return {
          status: 0,
          ok: false,
          error: err ? err.message || 'NetworkError' : 'NetworkError',
        };
      });
  }
})();
