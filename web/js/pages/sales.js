// ===== Sales page: record sales, totals, category split, daily bars and history =====
(function () {
  const DONUT_COLORS = ['#9ad9cd', '#f2b3b3', '#d8eba6', '#bdbcf0', '#f6cba0', '#a9d4ee'];
  const CARD_COLORS = ['pink', 'mint', 'lime'];
  const BAR_COLORS = ['peach', 'lilac', 'mint', 'pink', 'sky', 'lime'];

  const state = { days: 7, summary: null, sales: [], products: [] };
  let view = null;

  async function load() {
    const [summary, sales, products] = await Promise.all([
      api('/api/sales/summary?days=' + state.days),
      api('/api/sales?limit=100'),
      api('/api/products')
    ]);
    state.summary = summary;
    state.sales = sales.items;
    state.products = products.items;
  }

  async function refresh() {
    await load();
    if (parseHash().id !== 'sales') return;
    drawAll();
    refreshStatus();
  }

  // ---------- button at the top right ----------

  function drawActions() {
    const box = document.getElementById('pageActions');
    box.innerHTML = '<button class="btn btn-dark" id="recordBtn">' + icon('plus', 16) + 'Record Sale</button>';
    document.getElementById('recordBtn').addEventListener('click', openRecord);
  }

  // ---------- record a sale ----------

  function localToday() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function openRecord() {
    if (state.products.length === 0) {
      UI.toast('Add a product first on the Products page.', true);
      return;
    }
    const options = state.products.map(p =>
      '<option value="' + p.id + '"' + (p.stock <= 0 ? ' disabled' : '') + '>' + escapeHtml(p.name) +
      ' (' + (p.stock > 0 ? 'stock ' + p.stock : 'out of stock') + ')</option>').join('');
    const today = localToday();

    UI.modal({
      title: 'Record sale',
      bodyHtml:
        '<label class="field"><span>Product</span><select name="productId">' + options + '</select></label>' +
        '<div class="field-row">' +
          '<label class="field"><span>Date</span><input name="date" type="date" value="' + today + '" max="' + today + '"></label>' +
          '<label class="field"><span>Quantity</span><input name="quantity" type="number" min="1" step="1" value="1"></label>' +
        '</div>',
      confirmText: 'Record sale',
      onSubmit: async form => {
        const data = new FormData(form);
        const result = await apiPost('/api/sales/record', {
          productId: data.get('productId'), date: data.get('date'), quantity: data.get('quantity')
        });
        UI.toast(result.message);
        await refresh();
      }
    });
  }

  // ---------- the page ----------

  function dayLabel(d) {
    return state.days <= 7 ? d.label : String(Number(d.date.slice(8)));
  }

  function drawAll() {
    const s = state.summary;
    const days = state.days;
    const maxDaily = Math.max(...s.daily.map(d => d.units), 0);

    // the three colored category cards
    const miniCards = s.byCategory.length === 0
      ? '<div class="empty">No sales in this period.</div>'
      : s.byCategory.slice(0, 3).map((c, i) =>
          '<div class="cat-card" style="--c: var(--' + CARD_COLORS[i] + ')"><span>' + escapeHtml(c.category) + '</span>' +
          '<strong>' + c.units + ' units</strong><small>' + c.sharePct.toFixed(0) + '% of sales</small></div>').join('');

    // line chart and pill bars
    const lineHtml = maxDaily === 0
      ? '<div class="empty">No sales in the last ' + days + ' days.</div>'
      : Charts.line(s.daily.map(d => ({ label: dayLabel(d), value: d.units })));
    const barsHtml = maxDaily === 0
      ? '<div class="empty">No sales in the last ' + days + ' days.</div>'
      : Charts.pillBars(s.daily.map((d, i) => ({
          label: dayLabel(d),
          value: d.units,
          color: BAR_COLORS[i % BAR_COLORS.length],
          tipTitle: UI.formatDay(d.date),
          tipValue: d.units + (d.units === 1 ? ' unit' : ' units')
        })));

    // donut: units by category
    const segments = s.byCategory.map((c, i) => ({
      label: c.category, value: c.units, color: DONUT_COLORS[i % DONUT_COLORS.length]
    }));
    const legend = segments.length === 0 ? '' : segments.map(seg =>
      '<li><span class="dot" style="background:' + seg.color + '"></span>' + escapeHtml(seg.label) +
      '<span class="count">' + seg.value + '</span></li>').join('');

    // history table
    const rows = state.sales.length === 0
      ? '<tr><td colspan="4" class="empty">No sales recorded yet. Click "Record Sale" to add the first one.</td></tr>'
      : state.sales.map(r =>
          '<tr><td>' + UI.formatDay(r.date) + '</td>' +
          '<td><div class="prod-cell">' + UI.tile(r.product) + '<span>' + escapeHtml(r.product) + '</span></div></td>' +
          '<td>' + escapeHtml(r.category) + '</td><td class="num">' + r.quantity + '</td></tr>').join('');

    view.innerHTML =
      '<div class="dash">' +
        '<div class="sales-top">' +
          '<section class="card">' +
            '<div class="card-head"><h3>Total Units Sold</h3><div class="seg" id="segDays">' +
              [7, 14].map(n => '<button type="button" class="seg-btn' + (n === days ? ' active' : '') +
                '" data-days="' + n + '">Last ' + n + ' days</button>').join('') +
            '</div></div>' +
            '<div class="big-line"><span class="stat-big">' + s.total + '</span>' + UI.deltaChip(s.changePct, true) + '</div>' +
            '<p class="muted small">units sold in the last ' + days + ' days, compared with ' + s.previousTotal +
              ' in the ' + days + ' days before</p>' +
            '<div class="cat-cards">' + miniCards + '</div>' +
          '</section>' +
          '<section class="card"><div class="card-head"><h3>Units per day</h3>' +
            '<span class="muted">' + s.totalRecords + ' sales records</span></div>' + lineHtml + '</section>' +
        '</div>' +

        '<div class="sales-mid">' +
          '<section class="card"><div class="card-head"><h3>Units by category</h3></div>' +
            '<div class="donut-wrap">' + Charts.donut(segments, s.total, 'units sold') +
            '<ul class="legend">' + legend + '</ul></div></section>' +
          '<section class="card"><div class="card-head"><h3>Sales performance</h3>' +
            '<span class="muted">last ' + days + ' days</span></div>' + barsHtml + '</section>' +
        '</div>' +

        '<section class="card">' +
          '<div class="card-head"><h3>Sales history</h3><span class="muted">latest 100</span></div>' +
          '<div class="table-wrap scroll-y"><table class="table"><thead><tr>' +
            '<th>Date</th><th>Product</th><th>Category</th><th class="num">Qty</th></tr></thead>' +
            '<tbody>' + rows + '</tbody></table></div>' +
        '</section>' +
      '</div>';

    view.querySelector('#segDays').addEventListener('click', async event => {
      const button = event.target.closest('[data-days]');
      if (!button) return;
      state.days = Number(button.dataset.days);
      try { await refresh(); } catch (error) { UI.toast(error.message, true); }
    });

    if (maxDaily > 0) {
      Charts.wirePillBars(view.querySelector('.pc'), s.daily.map(d => d.units).indexOf(maxDaily));
    }
  }

  Pages.sales = {
    title: 'Sales Overview',
    sub: 'Record sales and track how fast products sell',

    async render(v) {
      view = v;
      await load();
      if (parseHash().id !== 'sales') return;
      drawActions();
      drawAll();
    }
  };
})();