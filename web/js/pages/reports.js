// ===== Reports page: key numbers, charts, top sellers, dead stock and exports =====
(function () {
  const state = { days: 14, dead: 30, data: null };
  const RANK_COLORS = ['mint', 'pink', 'lime', 'lilac', 'peach', 'sky'];
  let view = null;
  let requestId = 0;

  function isoDate(d) {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
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

  // ---------- CSV downloads (built in the browser from the API data) ----------

  function csvCell(value) {
    const s = String(value);
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  }

  function downloadCsv(filename, header, rows) {
    const text = [header].concat(rows).map(r => r.map(csvCell).join(',')).join('\r\n');
    const blob = new Blob(['\ufeff' + text], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function exportProducts() {
    try {
      const data = await api('/api/products');
      const rows = data.items.map(p => [
        p.id, p.name, p.category, p.stock, p.leadTimeDays,
        p.avgDaily === undefined ? '' : p.avgDaily,
        p.daysLeft === null || p.daysLeft === undefined ? '' : p.daysLeft,
        p.status || '', p.reorderQty || 0]);
      const name = 'products-' + isoDate(new Date()) + '.csv';
      downloadCsv(name, ['ID', 'Name', 'Category', 'Stock', 'Lead time (days)', 'Avg per day', 'Days left', 'Status', 'Reorder qty'], rows);
      UI.toast('Downloaded ' + name);
    } catch (error) {
      UI.toast(error.message, true);
    }
  }

  async function exportSales() {
    try {
      const data = await api('/api/sales?limit=500');
      const rows = data.items.map(r => [r.date, r.product, r.category, r.quantity]);
      const name = 'sales-' + isoDate(new Date()) + '.csv';
      downloadCsv(name, ['Date', 'Product', 'Category', 'Quantity'], rows);
      UI.toast(data.totalRecords > data.items.length
        ? 'Downloaded the latest ' + data.items.length + ' of ' + data.totalRecords + ' sales'
        : 'Downloaded ' + name);
    } catch (error) {
      UI.toast(error.message, true);
    }
  }

  function updateLink() {
    const link = document.getElementById('repTxt');
    if (link) link.href = '/api/report/download?days=' + state.days + '&dead=' + state.dead;
  }

  // ---------- the results ----------

  function drawResults() {
    const d = state.data;
    const box = view.querySelector('#repResults');

    if (d.totalProducts === 0) {
      box.innerHTML = '<div class="card"><div class="empty">No products yet.<br>' +
        '<a class="see-all" href="#/products">Add products on the Products page</a></div></div>';
      return;
    }

    const cards =
      statCard('mint', 'cart', 'Units sold', d.unitsSold, d.avgPerDay.toFixed(1) + ' per day &middot; last ' + d.days + ' days') +
      statCard('lilac', 'layers', 'Products', d.totalProducts, d.totalStock + ' units in stock') +
      statCard('lime', 'box', 'Inventory health', d.healthPct.toFixed(0) + '%', d.safe + ' of ' + d.totalProducts + ' products are safe') +
      statCard('pink', 'bell', 'Dead stock', d.deadStock.length, d.deadStockUnits + ' units, no sales in ' + d.deadDays + ' days');

    // days left per product, colored by status
    const chart = d.daysLeft.length === 0
      ? '<div class="empty">No sales data yet, so no prediction can be made.</div>'
      : Charts.pillBars(d.daysLeft.map(p => ({
          label: p.name.length > 9 ? p.name.slice(0, 8) + '..' : p.name,
          value: Math.min(p.daysLeft, 30),
          color: p.status.toLowerCase(),
          tipTitle: p.name,
          tipValue: p.daysLeft.toFixed(1) + ' days left'
        })));

    // top sellers
    const maxUnits = Math.max(...d.topSellers.map(t => t.units), 1);
    const top = d.topSellers.length === 0
      ? '<div class="empty">No sales in the last ' + d.days + ' days.</div>'
      : '<div class="rank-list">' + d.topSellers.map((t, i) =>
          '<div class="rank-row"><span class="rank-no">' + (i + 1) + '</span>' + UI.tile(t.name) +
          '<div><div class="rank-name">' + escapeHtml(t.name) + '<small class="muted">' + t.sharePct.toFixed(0) + '%</small></div>' +
          '<div class="bar-track"><i style="width:' + Math.round(t.units / maxUnits * 100) +
          '%; background: var(--' + RANK_COLORS[i % RANK_COLORS.length] + ')"></i></div></div>' +
          '<strong>' + t.units + '</strong></div>').join('') + '</div>';

    // dead stock
    const msPerDay = 86400000;
    const deadRows = d.deadStock.length === 0
      ? '<tr><td colspan="5" class="empty">No dead stock: every product with stock sold something in the last ' + d.deadDays + ' days.</td></tr>'
      : d.deadStock.map(p => {
          const since = p.lastSale
            ? Math.floor((Date.now() - new Date(p.lastSale + 'T00:00:00').getTime()) / msPerDay) + ' days ago'
            : 'Never sold';
          return '<tr><td><div class="prod-cell">' + UI.tile(p.name) + '<span>' + escapeHtml(p.name) + '</span></div></td>' +
                 '<td>' + escapeHtml(p.category) + '</td>' +
                 '<td class="num"><strong>' + p.stock + '</strong></td>' +
                 '<td>' + (p.lastSale ? UI.formatDay(p.lastSale) : '<span class="muted">-</span>') + '</td>' +
                 '<td>' + since + '</td></tr>';
        }).join('');

    box.innerHTML =
      '<div class="stat-row" style="margin:0">' + cards + '</div>' +
      '<div class="rep-row">' +
        '<section class="card"><div class="card-head"><h3>Days left per product</h3>' +
          '<span class="muted">most urgent first, capped at 30</span></div>' + chart + '</section>' +
        '<section class="card"><div class="card-head"><h3>Top sellers</h3>' +
          '<span class="muted">last ' + d.days + ' days</span></div>' + top + '</section>' +
      '</div>' +
      '<section class="card">' +
        '<div class="card-head"><h3>Dead stock <span class="muted">(' + d.deadStock.length + ')</span></h3>' +
          '<span class="muted">no sales in ' + d.deadDays + ' days</span></div>' +
        '<div class="table-wrap"><table class="table"><thead><tr>' +
          '<th>Product</th><th>Category</th><th class="num">Stock</th><th>Last sale</th><th></th>' +
        '</tr></thead><tbody>' + deadRows + '</tbody></table></div>' +
      '</section>';

    if (d.daysLeft.length > 0) Charts.wirePillBars(box.querySelector('.pc'), 0);
  }

  async function update() {
    const id = ++requestId;      // an older, slower answer must never overwrite a newer one
    try {
      const data = await api('/api/reports?days=' + state.days + '&dead=' + state.dead);
      if (id !== requestId || parseHash().id !== 'reports') return;
      state.data = data;
      drawResults();
    } catch (error) {
      if (id !== requestId) return;
      view.querySelector('#repResults').innerHTML =
        '<div class="card error-card"><div class="card-head"><h3>Something went wrong</h3></div>' +
        '<p class="muted">' + escapeHtml(error.message) + '</p></div>';
    }
  }

  function setActive(selector, button) {
    view.querySelectorAll(selector + ' .seg-btn').forEach(b => b.classList.toggle('active', b === button));
  }

  // ---------- the page ----------

  Pages.reports = {
    title: 'Reports',
    sub: 'Key numbers, top sellers, dead stock and exports',

    async render(v, params, ctx) {
      view = v;

      ctx.actions.innerHTML =
        '<button class="btn btn-light" id="repProd">Products CSV</button>' +
        '<button class="btn btn-light" id="repSales">Sales CSV</button>' +
        '<a class="btn btn-dark" id="repTxt" download>Download report</a>';
      document.getElementById('repProd').addEventListener('click', exportProducts);
      document.getElementById('repSales').addEventListener('click', exportSales);
      updateLink();

      view.innerHTML =
        '<section class="card">' +
          '<div class="pred-controls">' +
            '<div class="ctl"><span class="ctl-label">Sales period</span>' +
              segHtml('repDays', [[7, 'Last 7 days'], [14, 'Last 14 days'], [30, 'Last 30 days']], state.days) + '</div>' +
            '<div class="ctl"><span class="ctl-label">Dead stock after</span>' +
              segHtml('repDead', [[14, '14 days'], [30, '30 days'], [60, '60 days']], state.dead) + '</div>' +
          '</div>' +
        '</section>' +
        '<div class="pred-results" id="repResults"><div class="card"><div class="empty">Calculating...</div></div></div>';

      view.querySelector('#repDays').addEventListener('click', event => {
        const button = event.target.closest('[data-v]');
        if (!button) return;
        state.days = Number(button.dataset.v);
        setActive('#repDays', button);
        updateLink();
        update();
      });
      view.querySelector('#repDead').addEventListener('click', event => {
        const button = event.target.closest('[data-v]');
        if (!button) return;
        state.dead = Number(button.dataset.v);
        setActive('#repDead', button);
        updateLink();
        update();
      });

      await update();
    }
  };
})();