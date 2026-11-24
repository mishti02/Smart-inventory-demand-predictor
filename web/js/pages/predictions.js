// ===== Predictions page =====
(function () {
  const state = { window: 7, method: 'simple', safety: 2, cover: 14, filter: 'ALL', data: null };
  let view = null;
  let requestId = 0;

  const pad = id => '#' + String(id).padStart(4, '0');

  function queryUrl() {
    return '/api/predictions?window=' + state.window + '&method=' + state.method +
           '&safety=' + state.safety + '&cover=' + state.cover;
  }

  function segHtml(id, options, current) {
    return '<div class="seg" id="' + id + '">' + options.map(o =>
      '<button type="button" class="seg-btn' + (String(o[0]) === String(current) ? ' active' : '') +
      '" data-v="' + o[0] + '">' + o[1] + '</button>').join('') + '</div>';
  }

  function statCard(color, iconName, title, big, sub) {
    return '<div class="stat" style="--c: var(--' + color + ')">' +
             '<div class="stat-top"><span class="stat-ico">' + icon(iconName, 16) + '</span>' + title + '</div>' +
             '<div><div class="stat-big">' + big + '</div><div class="stat-sub">' + sub + '</div></div>' +
           '</div>';
  }

  // How full the shelf is compared with the target level; the dark tick is the reorder point
  function shelf(p) {
    if (!p.avgDaily) return '<span class="muted">-</span>';
    const target = p.avgDaily * (p.leadTimeDays + state.cover);
    const trigger = p.avgDaily * (p.leadTimeDays + state.safety);
    const fill = Math.min(100, Math.round(p.stock / target * 100));
    const tick = Math.min(100, Math.round(trigger / target * 100));
    return '<div class="shelf shelf-wide" title="Stock vs target level. The tick marks the reorder point.">' +
           '<span class="shelf-fill ' + p.status.toLowerCase() + '" style="width:' + fill + '%"></span>' +
           '<span class="shelf-tick" style="left:' + tick + '%"></span></div>';
  }

  function updateRule() {
    view.querySelector('#predRule').textContent =
      'A product needs a reorder when its days left are at most its lead time + ' + state.safety +
      ' safety days. The order covers ' + state.cover + ' days of sales after the lead time.';
  }

  // ---------- cards and table ----------

  function drawResults() {
    const d = state.data;
    const s = d.summary;
    const box = view.querySelector('#predResults');

    if (s.total === 0) {
      box.innerHTML = '<div class="card"><div class="empty">No products yet.<br>' +
        '<a class="see-all" href="#/products">Add products on the Products page</a></div></div>';
      return;
    }

    const reorderItems = d.items.filter(p => p.reorderQty > 0);
    const reorderUnits = reorderItems.reduce((sum, p) => sum + p.reorderQty, 0);

    const cards =
      statCard('pink', 'bell', 'Critical', s.critical, 'run out within 3 days') +
      statCard('peach', 'trend', 'Warning', s.warning, 'run out within 7 days') +
      statCard('lime', 'box', 'Safe', s.safe, 'more than 7 days of stock') +
      statCard('lilac', 'truck', 'To reorder', reorderUnits,
               reorderItems.length + (reorderItems.length === 1 ? ' item' : ' items') + ' to order');

    const shown = d.items.filter(p => state.filter === 'ALL' || p.status === state.filter);
    const chips = [['ALL', 'All', s.total], ['CRITICAL', 'Critical', s.critical],
                   ['WARNING', 'Warning', s.warning], ['SAFE', 'Safe', s.safe]]
      .map(c => '<button type="button" class="fchip' + (state.filter === c[0] ? ' active' : '') +
                '" data-filter="' + c[0] + '">' + c[1] + '<small>' + c[2] + '</small></button>').join('');

    const rows = shown.length === 0
      ? '<tr><td colspan="9" class="empty">No products in this group.</td></tr>'
      : shown.map(p =>
          '<tr class="row-' + p.status.toLowerCase() + '">' +
          '<td><span class="rank-badge">' + p.rank + '</span></td>' +
          '<td><div class="prod-cell">' + UI.tile(p.name) + '<div><div>' + escapeHtml(p.name) +
            '</div><small class="sub-line">' + escapeHtml(p.category) + ' &middot; ' + pad(p.id) + '</small></div></div></td>' +
          '<td class="num"><strong>' + p.stock + '</strong></td>' +
          '<td class="num">' + p.avgDaily.toFixed(2) + '</td>' +
          '<td class="num">' + UI.daysText(p) + '</td>' +
          '<td class="num">' + p.leadTimeDays + '</td>' +
          '<td>' + shelf(p) + '</td>' +
          '<td>' + UI.pill(p.status) + '</td>' +
          '<td class="num">' + (p.reorderQty > 0 ? '<strong>' + p.reorderQty + '</strong>' : '<span class="muted">-</span>') + '</td>' +
          '</tr>').join('');

    box.innerHTML =
      '<div class="stat-row" style="margin:0">' + cards + '</div>' +
      '<section class="card">' +
        '<div class="card-head"><h3>Ranking <span class="muted">(most urgent first)</span></h3>' +
          '<span class="muted">' + escapeHtml(d.method) + '</span></div>' +
        '<div class="toolbar"><div class="fchips" id="predChips">' + chips + '</div></div>' +
        '<div class="table-wrap"><table class="table"><thead><tr>' +
          '<th>#</th><th>Product</th><th class="num">Stock</th><th class="num">Avg/day</th>' +
          '<th class="num">Days left</th><th class="num">Lead</th><th>Stock level</th><th>Status</th>' +
          '<th class="num">Reorder</th></tr></thead><tbody>' + rows + '</tbody></table></div>' +
      '</section>';

    box.querySelector('#predChips').addEventListener('click', event => {
      const chip = event.target.closest('[data-filter]');
      if (chip) { state.filter = chip.dataset.filter; drawResults(); }
    });
  }

  // ---------- loading ----------

  async function update() {
    const id = ++requestId;      // an older, slower answer must never overwrite a newer one
    try {
      const data = await api(queryUrl());
      if (id !== requestId || parseHash().id !== 'predictions') return;
      state.data = data;
      drawResults();
      refreshStatus();
    } catch (error) {
      if (id !== requestId) return;
      view.querySelector('#predResults').innerHTML =
        '<div class="card error-card"><div class="card-head"><h3>Something went wrong</h3></div>' +
        '<p class="muted">' + escapeHtml(error.message) + '</p></div>';
    }
  }

  function setActive(selector, button) {
    view.querySelectorAll(selector + ' .seg-btn').forEach(b => b.classList.toggle('active', b === button));
  }

  function wireNumber(selector, key, min, max) {
    const input = view.querySelector(selector);
    input.addEventListener('change', () => {
      let value = parseInt(input.value, 10);
      if (isNaN(value)) value = state[key];
      value = Math.max(min, Math.min(max, value));
      input.value = value;
      state[key] = value;
      updateRule();
      update();
    });
  }

  // ---------- the page ----------

  Pages.predictions = {
    title: 'Predictions',
    sub: 'When will each product run out, and what should be ordered?',

    async render(v, params, ctx) {
      view = v;
      ctx.actions.innerHTML = '<a class="btn btn-dark" href="#/reorders">' + icon('truck', 16) + 'Reorder list</a>';

      view.innerHTML =
        '<section class="card">' +
          '<div class="pred-controls">' +
            '<div class="ctl"><span class="ctl-label">Analysis window</span>' +
              segHtml('segWindow', [[7, 'Last 7 days'], [14, 'Last 14 days'], [0, 'All history']], state.window) + '</div>' +
            '<div class="ctl"><span class="ctl-label">Method</span>' +
              segHtml('segMethod', [['simple', 'Simple average'], ['weighted', 'Weighted average']], state.method) + '</div>' +
            '<label class="ctl"><span class="ctl-label">Safety days</span>' +
              '<input class="input num-input" id="predSafety" type="number" min="0" max="60" step="1" value="' + state.safety + '"></label>' +
            '<label class="ctl"><span class="ctl-label">Cover days</span>' +
              '<input class="input num-input" id="predCover" type="number" min="1" max="120" step="1" value="' + state.cover + '"></label>' +
          '</div>' +
          '<p class="muted pred-note" id="predRule"></p>' +
        '</section>' +
        '<div class="pred-results" id="predResults"><div class="card"><div class="empty">Calculating...</div></div></div>';

      updateRule();

      view.querySelector('#segWindow').addEventListener('click', event => {
        const button = event.target.closest('[data-v]');
        if (!button) return;
        state.window = Number(button.dataset.v);
        setActive('#segWindow', button);
        update();
      });
      view.querySelector('#segMethod').addEventListener('click', event => {
        const button = event.target.closest('[data-v]');
        if (!button) return;
        state.method = button.dataset.v;
        setActive('#segMethod', button);
        update();
      });
      wireNumber('#predSafety', 'safety', 0, 60);
      wireNumber('#predCover', 'cover', 1, 120);

      await update();
    }
  };
})();