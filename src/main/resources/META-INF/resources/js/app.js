// Doorman – kleine Helfer für die Verwaltungsoberfläche (ohne Framework).
(function () {
  'use strict';

  // Formulare mit data-confirm erst nach Rückfrage absenden
  document.addEventListener('submit', function (e) {
    var msg = e.target.getAttribute('data-confirm');
    if (msg && !window.confirm(msg)) e.preventDefault();
  });

  function flash(btn, text, ok) {
    var old = btn.dataset.label || btn.textContent;
    btn.dataset.label = old;
    btn.textContent = text;
    btn.classList.toggle('copied', ok);
    clearTimeout(btn._t);
    btn._t = setTimeout(function () {
      btn.textContent = old;
      btn.classList.remove('copied');
    }, 1800);
  }

  function markSent(url, sent) {
    var body = new URLSearchParams({ sent: String(sent) });
    return fetch(url, { method: 'POST', body: body, credentials: 'same-origin' }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      document.querySelectorAll('.sent-toggle[data-sent-url="' + url + '"]').forEach(function (cb) {
        cb.checked = sent;
        var tr = cb.closest('tr');
        if (tr) tr.dataset.sent = sent ? '1' : '0';
      });
    });
  }

  document.addEventListener('click', function (e) {
    // QR-Code als Bild in die Zwischenablage (zum Einfügen in Outlook)
    var qrBtn = e.target.closest('[data-copy-qr]');
    if (qrBtn) {
      var url = qrBtn.getAttribute('data-copy-qr');
      if (!navigator.clipboard || !window.ClipboardItem) {
        window.open(url, '_blank');
        return;
      }
      // Safari verlangt, dass das Promise direkt im Klick übergeben wird
      var item = new ClipboardItem({
        'image/png': fetch(url, { credentials: 'same-origin' }).then(function (r) {
          if (!r.ok) throw new Error('HTTP ' + r.status);
          return r.blob();
        })
      });
      navigator.clipboard.write([item]).then(function () {
        flash(qrBtn, '✓ Kopiert', true);
        var sentUrl = qrBtn.getAttribute('data-sent-url');
        if (sentUrl) markSent(sentUrl, true).catch(function () {});
      }).catch(function (err) {
        console.error(err);
        flash(qrBtn, 'Fehler', false);
        window.open(url, '_blank');
      });
      return;
    }

    // Text (z. B. E-Mail-Adresse) kopieren
    var textBtn = e.target.closest('[data-copy-text]');
    if (textBtn && navigator.clipboard) {
      navigator.clipboard.writeText(textBtn.getAttribute('data-copy-text')).then(function () {
        flash(textBtn, '✓ Kopiert', true);
      });
      return;
    }

    // Formularfeld mit Vorlage füllen
    var fillBtn = e.target.closest('[data-fill]');
    if (fillBtn) {
      var field = fillBtn.form.elements[fillBtn.getAttribute('data-fill')];
      if (field) field.value = fillBtn.getAttribute('data-value');
    }
  });

  // Häkchen "versendet"
  document.addEventListener('change', function (e) {
    var cb = e.target.closest('.sent-toggle');
    if (!cb) return;
    var wanted = cb.checked;
    markSent(cb.getAttribute('data-sent-url'), wanted).catch(function () {
      cb.checked = !wanted;
      alert('Konnte nicht gespeichert werden.');
    });
  });

  // Teilnehmerliste filtern
  var filter = document.getElementById('filter');
  var status = document.getElementById('filter-status');
  var table = document.getElementById('participants');
  function applyFilter() {
    if (!table) return;
    var q = (filter.value || '').trim().toLowerCase();
    var s = status.value;
    table.querySelectorAll('tbody tr').forEach(function (tr) {
      var show = !q || tr.dataset.search.toLowerCase().indexOf(q) >= 0;
      if (show && s === 'unsent') show = tr.dataset.sent === '0';
      if (show && s.indexOf('missing-') === 0) show = (' ' + tr.dataset.missing).indexOf(' ' + s + ' ') >= 0;
      tr.hidden = !show;
    });
  }
  if (filter) filter.addEventListener('input', applyFilter);
  if (status) status.addEventListener('change', applyFilter);
})();
