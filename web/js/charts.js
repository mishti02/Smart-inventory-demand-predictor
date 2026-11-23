// ===== Chart and small UI helpers shared by the pages =====

const Charts = {

  // A row of tiny bars drawn inside the colored stat cards. The tallest bar is highlighted.
  miniBars(values) {
    const max = Math.max(...values, 1);
    const peak = values.indexOf(Math.max(...values));
    return '<div class="mini-bars">' + values.map((v, i) => {
      const height = Math.max(10, Math.round(v / max * 100));
      const cls = (i === peak && v > 0) ? 'mb peak' : 'mb';
      return '<span class="' + cls + '" style="height:' + height + '%"></span>';
    }).join('') + '</div>';
  },

  // A ring chart with rounded ends. segments = [{ value, color }]
  donut(segments, centerValue, centerLabel) {
    const size = 200, stroke = 26, gap = 6;
    const mid = size / 2;
    const radius = (size - stroke) / 2;
    const circumference = 2 * Math.PI * radius;
    const total = segments.reduce((sum, s) => sum + s.value, 0);

    const circle = (color, extra) =>
      '<circle cx="' + mid + '" cy="' + mid + '" r="' + radius + '" fill="none" stroke="' + color +
      '" stroke-width="' + stroke + '"' + extra + '/>';

    let rings = circle('rgba(15,61,58,.07)', '');   // the pale track behind the segments
    let start = 0;                                    // where the next segment begins (in pixels along the ring)

    segments.filter(s => s.value > 0).forEach(seg => {
      const length = seg.value / total * circumference;
      if (length >= circumference - 0.5) {
        rings += circle(seg.color, '');               // one segment fills the whole ring
      } else {
        // round line caps add half the stroke width at each end, so the dash is shortened to make room
        const dash = Math.max(0.01, length - gap - stroke);
        const offset = -(start + stroke / 2 + gap / 2);
        rings += circle(seg.color, ' stroke-linecap="round" stroke-dasharray="' + dash + ' ' +
                        circumference + '" stroke-dashoffset="' + offset + '"');
      }
      start += length;
    });

    return '<div class="donut"><svg viewBox="0 0 ' + size + ' ' + size + '">' +
           '<g transform="rotate(-90 ' + mid + ' ' + mid + ')">' + rings + '</g></svg>' +
           '<div class="donut-center"><strong>' + centerValue + '</strong><span>' + centerLabel + '</span></div></div>';
  },

  // A step for the chart's side scale that is a "nice" whole number (1, 2, 5, 10, 20, 50, ...)
  niceStep(x) {
    if (x <= 1) return 1;
    const magnitude = Math.pow(10, Math.floor(Math.log10(x)));
    const n = x / magnitude;
    const nice = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
    return nice * magnitude;
  },

  // Striped pill-shaped bars with a side scale and a tooltip.
  // items = [{ label, value, color, tipTitle, tipValue }]
  pillBars(items) {
    const max = Math.max(...items.map(i => i.value), 0);
    const step = Charts.niceStep(max / 4);
    const top = step * 4;
    const ticks = [0, 1, 2, 3, 4].map(k => k * step);   // bottom to top
    const topPct = t => 100 - (t / top * 100);

    const axis = ticks.map(t => '<span style="top:' + topPct(t) + '%">' + t + '</span>').join('');
    const lines = ticks.map(t => '<i style="top:' + topPct(t) + '%"></i>').join('');

    const cols = items.map(item => {
      const level = item.value > 0 ? Math.max(item.value / top * 100, 9) : 0;   // at least a small pill
      const fill = item.value > 0
        ? '<div class="pc-fill" style="height:' + level + '%; --c: var(--' + item.color + ')"><b class="pc-dot"></b></div>'
        : '';
      return '<div class="pc-col"><div class="pc-track">' + fill +
             '<div class="pc-tip" style="bottom:calc(' + level + '% + 16px)"><small>' + escapeHtml(item.tipTitle) +
             '</small><strong>' + escapeHtml(item.tipValue) + '</strong></div></div>' +
             '<div class="pc-label">' + escapeHtml(item.label) + '</div></div>';
    }).join('');

    return '<div class="pc"><div class="pc-axis">' + axis + '</div><div class="pc-body">' +
           '<div class="pc-grid">' + lines + '</div><div class="pc-cols">' + cols + '</div></div></div>';
  },

  // Shows the tooltip of the hovered bar; when the mouse leaves, it returns to the default bar
  wirePillBars(root, defaultIndex) {
    const cols = root.querySelectorAll('.pc-col');
    const activate = index => cols.forEach((col, k) => col.classList.toggle('active', k === index));
    cols.forEach((col, i) => col.addEventListener('mouseenter', () => activate(i)));
    root.addEventListener('mouseleave', () => activate(defaultIndex));
    activate(defaultIndex);
  }
};

// Small pieces of HTML used on several pages
const UI = {
  TILE_COLORS: ['lime', 'mint', 'pink', 'lilac', 'peach', 'sky'],

  // The same product name always gets the same pastel color
  tileColor(name) {
    let sum = 0;
    for (let i = 0; i < name.length; i++) sum += name.charCodeAt(i);
    return UI.TILE_COLORS[sum % UI.TILE_COLORS.length];
  },

  // A rounded square with the first letter of the name
  tile(name) {
    return '<span class="tile" style="--c: var(--' + UI.tileColor(name) + ')">' +
           escapeHtml(name.trim().charAt(0).toUpperCase()) + '</span>';
  },

  // A change chip such as +8.4% (green) or -3.1% (red).
  // upIsGood = false for numbers where an increase is bad.
  deltaChip(pct, upIsGood) {
    if (Math.abs(pct) < 0.05) return '<span class="delta flat">0.0%</span>';
    const good = (pct > 0) === upIsGood;
    return '<span class="delta ' + (good ? 'good' : 'bad') + '">' + (pct > 0 ? '+' : '') + pct.toFixed(1) + '%</span>';
  },

  // CRITICAL, WARNING or SAFE
  pill(status) {
    return '<span class="pill ' + status.toLowerCase() + '">' + status + '</span>';
  },

  daysText(p) {
    return p.daysLeft === null ? 'No sales' : p.daysLeft.toFixed(1) + ' days';
  },

  // How full the shelf is compared with the target level. The dark tick is the reorder point.
  // 2 and 14 are the default safety and cover days used by the API.
  shelfBar(p) {
    if (!p.avgDaily) return '';                                  // nothing sells, so there is no target
    const target = p.avgDaily * (p.leadTimeDays + 14);           // the stock level a reorder aims for
    const trigger = p.avgDaily * (p.leadTimeDays + 2);           // reorder when stock falls to this
    const fill = Math.min(100, Math.round(p.stock / target * 100));
    const tick = Math.min(100, Math.round(trigger / target * 100));
    return '<div class="shelf" title="Stock vs target level. The tick marks the reorder point.">' +
           '<span class="shelf-fill ' + p.status.toLowerCase() + '" style="width:' + fill + '%"></span>' +
           '<span class="shelf-tick" style="left:' + tick + '%"></span></div>';
  },

  // "2026-11-23" becomes "Mon, 23 Nov"
  formatDay(isoDate) {
    return new Date(isoDate + 'T00:00:00').toLocaleDateString('en-GB',
      { weekday: 'short', day: 'numeric', month: 'short' });
  }
};