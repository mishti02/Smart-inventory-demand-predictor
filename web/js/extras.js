// ===== Extra helpers: POST requests, pop-up forms, messages, radar chart, line chart =====

// Sends data to the Java server with POST (every button that changes data uses this).
// data is an object such as { name: 'Milk', stock: 10 }. Throws an Error with the server's message.
async function apiPost(path, data) {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(data)
  });
  let body = {};
  try { body = await response.json(); } catch (e) { /* the answer was not JSON */ }
  if (!response.ok) throw new Error(body.error || 'Request failed (' + response.status + ')');
  return body;
}

// two more icons for the row buttons
ICONS.edit = '<path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>';
ICONS.trash = '<polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>' +
              '<path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>';

// A small message in the bottom right corner that fades away
UI.toast = function (message, isError) {
  let box = document.getElementById('toasts');
  if (!box) {
    box = document.createElement('div');
    box.id = 'toasts';
    document.body.appendChild(box);
  }
  const toast = document.createElement('div');
  toast.className = 'toast' + (isError ? ' error' : '');
  toast.textContent = message;
  box.appendChild(toast);
  setTimeout(() => toast.classList.add('show'), 10);
  setTimeout(() => {
    toast.classList.remove('show');
    setTimeout(() => toast.remove(), 300);
  }, 3200);
};

// A pop-up form.
// options = { title, bodyHtml, confirmText, danger, onSubmit(form) }
// onSubmit may be async. If it throws, the message is shown inside the pop-up and it stays open.
UI.modal = function (options) {
  const overlay = document.createElement('div');
  overlay.className = 'overlay';
  overlay.innerHTML =
    '<form class="modal" novalidate>' +
      '<h3>' + escapeHtml(options.title) + '</h3>' +
      '<div>' + options.bodyHtml + '</div>' +
      '<p class="form-error" hidden></p>' +
      '<div class="modal-actions">' +
        '<button type="button" class="btn btn-light" data-close>Cancel</button>' +
        '<button type="submit" class="btn ' + (options.danger ? 'btn-danger' : 'btn-dark') + '">' +
          escapeHtml(options.confirmText || 'Save') + '</button>' +
      '</div>' +
    '</form>';
  document.body.appendChild(overlay);

  const form = overlay.querySelector('form');
  const errorBox = overlay.querySelector('.form-error');

  const onKey = event => { if (event.key === 'Escape') close(); };
  function close() {
    overlay.remove();
    document.removeEventListener('keydown', onKey);
  }
  document.addEventListener('keydown', onKey);
  overlay.addEventListener('mousedown', event => { if (event.target === overlay) close(); });
  overlay.querySelector('[data-close]').addEventListener('click', close);

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const submit = form.querySelector('[type=submit]');
    submit.disabled = true;           // no double clicks
    errorBox.hidden = true;
    try {
      await options.onSubmit(form);
      close();
    } catch (error) {
      errorBox.textContent = error.message;
      errorBox.hidden = false;
      submit.disabled = false;
    }
  });

  const first = form.querySelector('input');
  if (first) first.focus();
  return { close: close };
};

// Horizontal bars, used when there are too few categories for a radar chart
Charts.barList = function (labels, values) {
  if (!labels.length) return '<div class="empty">No data yet.</div>';
  const max = Math.max(...values, 1);
  return '<div class="bar-list">' + labels.map((label, i) =>
    '<div class="bar-row"><span>' + escapeHtml(label) + '</span>' +
    '<div class="bar-track"><i style="width:' + Math.round(values[i] / max * 100) + '%"></i></div>' +
    '<strong>' + values[i] + '</strong></div>').join('') + '</div>';
};

// A radar (spider) chart: one axis per label, the shape shows how big each value is
Charts.radar = function (labels, values) {
  const n = labels.length;
  const w = 380, h = 300, cx = w / 2, cy = h / 2 + 4, radius = 95;
  const max = Math.max(...values, 1);

  // the point on axis i that sits at the given fraction (0 to 1) of the radius
  const point = (i, fraction) => {
    const angle = -Math.PI / 2 + i * 2 * Math.PI / n;      // the first axis points straight up
    return [cx + Math.cos(angle) * radius * fraction, cy + Math.sin(angle) * radius * fraction];
  };
  const toText = pts => pts.map(p => p[0].toFixed(1) + ',' + p[1].toFixed(1)).join(' ');

  let svg = '<svg class="radar" viewBox="0 0 ' + w + ' ' + h + '">';
  [1 / 3, 2 / 3, 1].forEach(fraction => {
    svg += '<polygon points="' + toText(labels.map((_, i) => point(i, fraction))) +
           '" fill="none" stroke="rgba(15,61,58,.15)"/>';
  });
  labels.forEach((_, i) => {
    const p = point(i, 1);
    svg += '<line x1="' + cx + '" y1="' + cy + '" x2="' + p[0].toFixed(1) + '" y2="' + p[1].toFixed(1) +
           '" stroke="rgba(15,61,58,.10)"/>';
  });
  svg += '<polygon points="' + toText(values.map((v, i) => point(i, v / max))) +
         '" fill="rgba(18,165,143,.22)" stroke="#12a58f" stroke-width="2.5" stroke-linejoin="round"/>';
  values.forEach((v, i) => {
    const p = point(i, v / max);
    svg += '<circle cx="' + p[0].toFixed(1) + '" cy="' + p[1].toFixed(1) + '" r="4.5" fill="#12a58f"/>';
  });
  labels.forEach((label, i) => {
    const p = point(i, 1.2);
    const anchor = Math.abs(p[0] - cx) < 8 ? 'middle' : (p[0] > cx ? 'start' : 'end');
    const text = label.length > 11 ? label.slice(0, 10) + '..' : label;
    svg += '<text x="' + p[0].toFixed(1) + '" y="' + (p[1] + 4).toFixed(1) + '" text-anchor="' + anchor +
           '" font-size="12" fill="#4d6662">' + escapeHtml(text) +
           '<title>' + escapeHtml(label) + ': ' + values[i] + ' units</title></text>';
  });
  return svg + '</svg>';
};

// A smooth line chart. points = [{ label, value }]. The highest point is marked.
Charts.line = function (points) {
  const w = 520, h = 230, left = 40, right = 14, padTop = 26, bottom = 30;
  const n = points.length;
  const plotW = w - left - right;
  const plotH = h - padTop - bottom;
  const baseline = padTop + plotH;
  const maxValue = Math.max(...points.map(p => p.value), 0);
  const step = Charts.niceStep(maxValue / 4);
  const yMax = step * 4;
  const x = i => (n === 1 ? left + plotW / 2 : left + i * plotW / (n - 1));
  const y = v => padTop + (1 - v / yMax) * plotH;
  const f = v => v.toFixed(1);

  let svg = '<svg class="linechart" viewBox="0 0 ' + w + ' ' + h + '">';

  // dashed grid lines with the side scale
  for (let k = 0; k <= 4; k++) {
    const gy = y(k * step);
    svg += '<line x1="' + left + '" x2="' + (w - right) + '" y1="' + f(gy) + '" y2="' + f(gy) +
           '" stroke="rgba(15,61,58,.14)" stroke-dasharray="4 5"/>' +
           '<text x="' + (left - 8) + '" y="' + f(gy + 4) + '" text-anchor="end" font-size="11" fill="#6f8783">' +
           (k * step) + '</text>';
  }

  // the curve: each piece is a cubic curve whose control points come from the neighbouring points
  const pts = points.map((p, i) => [x(i), y(p.value)]);
  if (n >= 2) {
    const clamp = v => Math.max(padTop, Math.min(baseline, v));   // the curve never dips below the axis
    let d = 'M' + f(pts[0][0]) + ',' + f(pts[0][1]);
    for (let i = 0; i < n - 1; i++) {
      const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2;
      const c1x = p1[0] + (p2[0] - p0[0]) / 6, c1y = clamp(p1[1] + (p2[1] - p0[1]) / 6);
      const c2x = p2[0] - (p3[0] - p1[0]) / 6, c2y = clamp(p2[1] - (p3[1] - p1[1]) / 6);
      d += ' C' + f(c1x) + ',' + f(c1y) + ' ' + f(c2x) + ',' + f(c2y) + ' ' + f(p2[0]) + ',' + f(p2[1]);
    }
    svg += '<path d="' + d + ' L' + f(pts[n - 1][0]) + ',' + f(baseline) + ' L' + f(pts[0][0]) + ',' + f(baseline) +
           ' Z" fill="rgba(18,165,143,.10)"/>';
    svg += '<path d="' + d + '" fill="none" stroke="#12a58f" stroke-width="3" stroke-linecap="round"/>';
  }

  // labels under the chart, and the highest point
  const peak = points.reduce((best, p, i) => (p.value > points[best].value ? i : best), 0);
  points.forEach((p, i) => {
    svg += '<text x="' + f(x(i)) + '" y="' + (h - 8) + '" text-anchor="middle" font-size="12" fill="' +
           (i === peak ? '#0f2f2c' : '#6f8783') + '"' + (i === peak ? ' font-weight="700"' : '') + '>' +
           escapeHtml(p.label) + '</text>';
  });
  if (maxValue > 0) {
    const px = pts[peak][0], py = pts[peak][1];
    svg += '<line x1="' + f(px) + '" x2="' + f(px) + '" y1="' + f(py) + '" y2="' + f(baseline) +
           '" stroke="#0f3d3a" stroke-width="1.5"/>' +
           '<circle cx="' + f(px) + '" cy="' + f(py) + '" r="6" fill="#fff" stroke="#12a58f" stroke-width="3"/>' +
           '<text x="' + f(px) + '" y="' + f(py - 14) + '" text-anchor="middle" font-size="13" font-weight="700" fill="#0f2f2c">' +
           points[peak].value + '</text>';
  }
  return svg + '</svg>';
};