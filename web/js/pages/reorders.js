// ===== Reorders page: what to order, how much, and by when =====
(function () {
  const state = { window: 7, method: 'simple', safety: 2, cover: 14, data: null, deselected: new Set() };
  let view = null;
  let requestId = 0;

  const pad = id => '#' + String(id).padStart(4, '0');

  function isoDate(d) {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  // the date n days from today, as 2026-11-27
  function addDays(n) {
    const d = new Date();
    d.setDate(d.getDate() + n);
    return isoDate(d);
  }

  function queryUrl() {
    return '/api/reorders?window=' + state.window + '&method=' + state.method +
           '&safety=' + state.safety + '&cover=' + state.cover;
  }

  function segHtml(id, options, current) {
    return '<div class="seg" id="' + id + '">' + options.map(o =>
      '<button type="button" class="seg-btn' + (String(o[0]) === String(current) ? ' active' : '') +
      '" data-v="' + o[0] + '">' + o[1] + '</button>').join('') + '</div>';
  }

  function statCard(color, iconName, title, big, sub, bigClass) {
    return '<div class="stat" style="--c: var(--' + color + ')">' +
             '<div class="stat-top"><span class="stat-ico">' + icon(iconName, 16) + '</span>' + title + '</div>' +
             '<div><div class="stat-big ' + (bigClass || '') + '">' + big + '</div><div class="stat-sub">' + sub + '</div></div>' +
           '</div>';
  }

  // The latest day to place the order so it arrives before the shelf is empty
  function orderBy(p) {
    if (p.daysLeft === null) return { now: false, text: '-' };
    const slack = p.daysLeft - p.leadTimeDays;
    if (slack <= 0) return { now: true, text: 'Order now' };
    const n = Math.floor(slack);
    return { now: false, text: n === 0 ? 'Today' : UI.formatDay(addDays(n)) };
  }

  function selectedItems() {
    return state.data.items.filter(p => !state.deselected.has(p.id));
  }

  function csvCell(value) {
    const s = String(value);
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  }

  // ---------- copy and download ----------

  async function copyList() {
    const items = selectedItems();
    if (items.length === 0) { UI.toast('Select at least one product.', true); return; }
    const lines = ['Reorder list - ' + isoDate(new Date())];
    items.forEach(p => lines.push(p.name + ' - ' + p.reorderQty + ' units'));
    lines.push('Total: ' + items.reduce((sum, p) => sum + p.reorderQty, 0) + ' units');
    try {
      await navigator.clipboard.writeText(lines.join('\n'));
      UI.toast('List copied to the clipboard');
    } catch (error) {
      UI.toast('Could not copy. Use "Download CSV" instead.', true);
    }
  }

  function downloadCsv() {
    const items = selectedItems();
    if (items.length === 0) { UI.toast('Select at least one product.', true); return; }
    const header = ['Product', 'Category', 'Stock', 'Avg per day', 'Days left', 'Lead time (days)', 'Order quantity', 'Order by'];
    const rows = items.map(p => [p.name, p.category, p.stock, p.avgDaily, p.daysLeft, p.leadTimeDays, p.reorderQty, orderBy(p).text]);
    const text = [header].concat(rows).map(r => r.map(csvCell).join(',')).join('\r\n');

    const blob = new Blob(['\ufeff' + text], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'reorder-list-' + isoDate(new Date()) + '.csv';
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    UI.toast('Downloaded ' + link.download);
  }

  // ---------- an order arrived: add it to the stock ----------

  function openReceive(p) {
    UI.modal({
      title: 'Order received: ' + p.name,
      bodyHtml: '<p class="modal-note">Current stock: <strong>' + p.stock + ' units</strong>. ' +
                'Enter how many units were delivered, and they are added to the stock.</p>' +
                '<label class="field"><span>Units received</span>' +
                '<input name="quantity" type="number" min="1" step="1" value="' + p.reorderQty + '"></label>',
      confirmText: 'Add to stock',
      onSubmit: async form => {
        const result = await apiPost('/api/products/restock',
          { id: p.id, quantity: new FormData(form).get('quantity') });
        UI.toast(result.message);
        await update();
        refreshStatus();
      }
    });
  }

  // ---------- cards and table ----------

  function drawResults() {
    const d = state.data;
    const box = view.querySelector('#reResults');

    if (d.items.length === 0) {
      box.innerHTML = '<div class="card"><div class="empty">Nothing needs reordering right now.<br>' +
        'Every product has enough stock for its lead time + ' + state.safety + ' safety days.<br>' +
        '<a class="see-all" href="#/predictions">See all predictions</a></div></div>';
      return;
    }

    const orderNow = d.items.filter(p => orderBy(p).now).length;
    const first = d.items.slice().sort((a, b) => a.daysLeft - b.daysLeft)[0];
    const firstText = first.daysLeft < 1 ? 'Today' : UI.formatDay(addDays(Math.floor(first.daysLeft)));

    const cards =
      statCard('lilac', 'truck', 'Items to order', d.count, 'products below their reorder point') +
      statCard('mint', 'box', 'Total units', d.totalUnits, 'to cover ' + state.cover + ' days of sales') +
      statCard('pink', 'bell', 'Order now', orderNow, 'cannot arrive before they run out') +
      statCard('peach', 'trend', 'Runs out first', firstText, escapeHtml(first.name), 'sm');

    const selected = selectedItems();
    const selectedUnits = selected.reduce((sum, p) => sum + p.reorderQty, 0);

    const rows = d.items.map(p => {
      const by = orderBy(p);
      const checked = !state.deselected.has(p.id);
      return '<tr class="' + (checked ? '' : 'dim') + '">' +
        '<td><input class="chk" type="checkbox" data-id="' + p.id + '"' + (checked ? ' checked' : '') + '></td>' +
        '<td><div class="prod-cell">' + UI.tile(p.name) + '<div><div>' + escapeHtml(p.name) +
          '</div><small class="sub-line">' + escapeHtml(p.category) + ' &middot; ' + pad(p.id) + '</small></div></div></td>' +
        '<td class="num"><strong>' + p.stock + '</strong></td>' +
        '<td class="num">' + p.avgDaily.toFixed(2) + '</td>' +
        '<td class="num">' + UI.daysText(p) + '</td>' +
        '<td class="num">' + p.leadTimeDays + '</td>' +
        '<td class="num"><span class="qty-badge">' + p.reorderQty + '</span></td>' +
        '<td><span class="order-by' + (by.now ? ' now' : '') + '">' + by.text + '</span></td>' +
        '<td>' + UI.pill(p.status) + '</td>' +
        '<td><div class="row-actions"><button class="icon-btn" data-act="receive" data-id="' + p.id +
          '" title="Order received">' + icon('plus', 16) + '</button></div></td></tr>';
    }).join('');

    box.innerHTML =
      '<div class="stat-row" style="margin:0">' + cards + '</div>' +
      '<section class="card">' +
        '<div class="card-head"><h3>Reorder list <span class="muted">(' + d.count + ')</span></h3>' +
          '<span class="muted">sorted by urgency</span></div>' +
        '<div class="table-wrap"><table class="table"><thead><tr>' +
          '<th><input class="chk" type="checkbox" id="reAll" title="Select all"></th>' +
          '<th>Product</th><th class="num">Stock</th><th class="num">Avg/day</th><th class="num">Days left</th>' +
          '<th class="num">Lead</th><th class="num">Order qty</th><th>Order by</th><th>Status</th><th></th>' +
        '</tr></thead><tbody id="reBody">' + rows + '</tbody></table></div>' +
        '<div class="re-bar">' +
          '<span class="sel"><strong>' + selected.length + '</strong> of ' + d.count + ' selected &middot; <strong>' +
            selectedUnits + '</strong> units</span>' +
          '<div class="re-actions">' +
            '<button class="btn btn-light" id="reCopy">Copy list</button>' +
            '<button class="btn btn-dark" id="reCsv">Download CSV</button>' +
          '</div>' +
        '</div>' +
      '</section>';

    const all = box.querySelector('#reAll');
    all.checked = selected.length === d.items.length;
    all.indeterminate = selected.length > 0 && selected.length < d.items.length;

    all.addEventListener('change', () => {
      if (all.checked) state.deselected.clear();
      else d.items.forEach(p => state.deselected.add(p.id));
      drawResults();
    });
    box.querySelector('#reBody').addEventListener('change', event => {
      const box1 = event.target.closest('input[data-id]');
      if (!box1) return;
      const id = Number(box1.dataset.id);
      if (box1.checked) state.deselected.delete(id); else state.deselected.add(id);
      drawResults();
    });
    box.querySelector('#reBody').addEventListener('click', event => {
      const button = event.target.closest('button[data-act="receive"]');
      if (!button) return;
      const product = d.items.find(p => p.id === Number(button.dataset.id));
      if (product) openReceive(product);
    });
    box.querySelector('#reCopy').addEventListener('click', copyList);
    box.querySelector('#reCsv').addEventListener('click', downloadCsv);
  }

  // ---------- loading ----------

  async function update() {
    const id = ++requestId;      // an older, slower answer must never overwrite a newer one
    try {
      const data = await api(queryUrl());
      if (id !== requestId || parseHash().id !== 'reorders') return;
      state.data = data;
      drawResults();
    } catch (error) {
      if (id !== requestId) return;
      view.querySelector('#reResults').innerHTML =
        '<div class="card error-card"><div class="card-head"><h3>Something went wrong</h3></div>' +
        '<p class="muted">' + escapeHtml(error.message) + '</p></div>';
    }
  }

  function updateRule() {
    view.querySelector('#reRule').textContent =
      'A product is listed when its days left are at most its lead time + ' + state.safety +
      ' safety days. The quantity covers ' + state.cover + ' days of sales after the lead time.';
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

  Pages.reorders = {
    title: 'Reorders',
    sub: 'What to order, how much, and by when',

        async render(v, params, ctx) {
      view = v;
      if (state.defaultsVersion !== UserDefaults.version) {
        Object.assign(state, UserDefaults.get());
        state.defaultsVersion = UserDefaults.version;
      }
      ctx.actions.innerHTML = '<a class="btn btn-light" href="#/predictions">' + icon('trend', 16) + 'All predictions</a>';

      view.innerHTML =
        '<section class="card">' +
          '<div class="pred-controls">' +
            '<div class="ctl"><span class="ctl-label">Analysis window</span>' +
              segHtml('reWindow', [[7, 'Last 7 days'], [14, 'Last 14 days'], [0, 'All history']], state.window) + '</div>' +
            '<div class="ctl"><span class="ctl-label">Method</span>' +
              segHtml('reMethod', [['simple', 'Simple average'], ['weighted', 'Weighted average']], state.method) + '</div>' +
            '<label class="ctl"><span class="ctl-label">Safety days</span>' +
              '<input class="input num-input" id="reSafety" type="number" min="0" max="60" step="1" value="' + state.safety + '"></label>' +
            '<label class="ctl"><span class="ctl-label">Cover days</span>' +
              '<input class="input num-input" id="reCover" type="number" min="1" max="120" step="1" value="' + state.cover + '"></label>' +
          '</div>' +
          '<p class="muted pred-note" id="reRule"></p>' +
        '</section>' +
        '<div class="pred-results" id="reResults"><div class="card"><div class="empty">Calculating...</div></div></div>';

      updateRule();

      view.querySelector('#reWindow').addEventListener('click', event => {
        const button = event.target.closest('[data-v]');
        if (!button) return;
        state.window = Number(button.dataset.v);
        setActive('#reWindow', button);
        update();
      });
      view.querySelector('#reMethod').addEventListener('click', event => {
        const button = event.target.closest('[data-v]');
        if (!button) return;
        state.method = button.dataset.v;
        setActive('#reMethod', button);
        update();
      });
      wireNumber('#reSafety', 'safety', 0, 60);
      wireNumber('#reCover', 'cover', 1, 120);

      await update();
    }
  };
})();