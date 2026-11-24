// ===== Categories page: stock and sales by category =====
(function () {
  const COLORS = ['#9ad9cd', '#f2b3b3', '#d8eba6', '#bdbcf0', '#f6cba0', '#a9d4ee', '#e5d29b', '#c4c9cf'];
  const state = { days: 14, data: null, products: [], selected: null };
  let view = null;

  const colorOf = index => COLORS[index % COLORS.length];

  async function load() {
    const [cats, prods] = await Promise.all([
      api('/api/categories?days=' + state.days),
      api('/api/products')
    ]);
    state.data = cats;
    state.products = prods.items;
    if (state.selected && !cats.items.some(c => c.category === state.selected)) state.selected = null;
  }

  function legendHtml(segments) {
    const total = segments.reduce((sum, s) => sum + s.value, 0);
    return segments.filter(s => s.value > 0).map(s =>
      '<li><span class="dot" style="background:' + s.color + '"></span>' + escapeHtml(s.label) +
      '<span class="count">' + s.value + ' <small>(' + Math.round(s.value * 100 / total) + '%)</small></span></li>'
    ).join('');
  }

  function categoryTile(name, index) {
    return '<span class="tile" style="--c:' + colorOf(index) + '">' +
           escapeHtml(name.trim().charAt(0).toUpperCase()) + '</span>';
  }

  // ---------- the whole page ----------

  function drawAll() {
    const d = state.data;
    const items = d.items;

    if (items.length === 0) {
      view.innerHTML = '<div class="card"><div class="empty">No categories yet.<br>' +
        '<a class="see-all" href="#/products">Add products on the Products page</a></div></div>';
      return;
    }

    const stockSegs = items.map((c, i) => ({ label: c.category, value: c.stock, color: colorOf(i) }));
    const soldSegs = items.map((c, i) => ({ label: c.category, value: c.unitsSold, color: colorOf(i) }));

    const stockBlock = d.totalStock === 0
      ? '<div class="empty">No stock yet.</div>'
      : '<div class="donut-col">' + Charts.donut(stockSegs, d.totalStock, 'units in stock') +
        '<ul class="legend">' + legendHtml(stockSegs) + '</ul></div>';

    const soldBlock = d.totalUnitsSold === 0
      ? '<div class="empty">No sales in the last ' + d.days + ' days.</div>'
      : '<div class="donut-col">' + Charts.donut(soldSegs, d.totalUnitsSold, 'units sold') +
        '<ul class="legend">' + legendHtml(soldSegs) + '</ul></div>';

    // the five best selling categories
    const ranked = items.map((c, i) => ({ c: c, i: i }))
      .filter(r => r.c.unitsSold > 0)
      .sort((a, b) => b.c.unitsSold - a.c.unitsSold)
      .slice(0, 5);
    const maxSold = Math.max(...ranked.map(r => r.c.unitsSold), 1);
    const rankBlock = ranked.length === 0
      ? '<div class="empty">No sales in the last ' + d.days + ' days.</div>'
      : '<div class="rank-list">' + ranked.map((r, n) =>
          '<div class="rank-row"><span class="rank-no">' + (n + 1) + '</span>' + categoryTile(r.c.category, r.i) +
          '<div><div class="rank-name">' + escapeHtml(r.c.category) + '</div>' +
          '<div class="bar-track"><i style="width:' + Math.round(r.c.unitsSold / maxSold * 100) +
          '%; background:' + colorOf(r.i) + '"></i></div></div>' +
          '<strong>' + r.c.unitsSold + '</strong></div>').join('') + '</div>';

    // the details table
    const rows = items.map((c, i) => {
      const avg = c.unitsSold / d.days;
      const cover = avg > 0 ? c.stock / avg : null;
      const cls = cover === null ? '' : (cover <= 3 ? 'critical' : cover <= 7 ? 'warning' : 'safe');
      return '<tr class="click-row" data-i="' + i + '">' +
        '<td><div class="prod-cell"><span class="dot big" style="background:' + colorOf(i) + '"></span>' +
          escapeHtml(c.category) + '</div></td>' +
        '<td class="num">' + c.products + '</td>' +
        '<td class="num"><strong>' + c.stock + '</strong></td>' +
        '<td><div class="share-cell"><div class="bar-track"><i style="width:' + c.stockSharePct +
          '%; background:' + colorOf(i) + '"></i></div><span>' + c.stockSharePct.toFixed(0) + '%</span></div></td>' +
        '<td class="num">' + c.unitsSold + '</td>' +
        '<td class="num">' + c.salesSharePct.toFixed(0) + '%</td>' +
        '<td class="num">' + (cover === null
          ? '<span class="muted">No sales</span>'
          : '<span class="due ' + cls + '">' + cover.toFixed(1) + ' days</span>') + '</td></tr>';
    }).join('');

    const totalProducts = items.reduce((sum, c) => sum + c.products, 0);

    view.innerHTML =
      '<div class="cat-grid">' +
        '<section class="card"><div class="card-head"><h3>Stock by category</h3>' +
          '<span class="muted">' + d.totalStock + ' units</span></div>' + stockBlock + '</section>' +
        '<section class="card"><div class="card-head"><h3>Sales by category</h3>' +
          '<span class="muted">last ' + d.days + ' days</span></div>' + soldBlock + '</section>' +
        '<section class="card"><div class="card-head"><h3>Top categories</h3>' +
          '<span class="muted">by units sold</span></div>' + rankBlock + '</section>' +
      '</div>' +

      '<section class="card" style="margin-bottom:18px">' +
        '<div class="card-head"><h3>Category details</h3><div class="seg" id="catDays">' +
          [7, 14, 30].map(n => '<button type="button" class="seg-btn' + (n === d.days ? ' active' : '') +
            '" data-days="' + n + '">Last ' + n + ' days</button>').join('') +
        '</div></div>' +
        '<div class="table-wrap"><table class="table" id="catTable"><thead><tr>' +
          '<th>Category</th><th class="num">Products</th><th class="num">Stock</th><th>Stock share</th>' +
          '<th class="num">Units sold</th><th class="num">Sales share</th>' +
          '<th class="num" title="How many days the current stock lasts at the recent sales speed">Stock lasts</th>' +
        '</tr></thead><tbody>' + rows + '</tbody>' +
        '<tfoot><tr><td>Total</td><td class="num">' + totalProducts + '</td><td class="num">' + d.totalStock +
          '</td><td></td><td class="num">' + d.totalUnitsSold + '</td><td class="num">100%</td><td></td></tr></tfoot>' +
        '</table></div>' +
        '<p class="muted small" style="margin:12px 0 0">Click a category to see its products.</p>' +
      '</section>' +

      '<section class="card" id="catDetail"></section>';

    view.querySelector('#catTable').addEventListener('click', event => {
      const row = event.target.closest('.click-row');
      if (!row) return;
      const category = state.data.items[Number(row.dataset.i)].category;
      state.selected = (state.selected === category) ? null : category;
      drawDetail();
    });

    view.querySelector('#catDays').addEventListener('click', async event => {
      const button = event.target.closest('[data-days]');
      if (!button) return;
      state.days = Number(button.dataset.days);
      try {
        await load();
        if (parseHash().id !== 'categories') return;
        drawAll();
      } catch (error) {
        UI.toast(error.message, true);
      }
    });

    drawDetail();
  }

  // ---------- the products of the selected category ----------

  function drawDetail() {
    const box = view.querySelector('#catDetail');
    if (!box) return;

    view.querySelectorAll('.click-row').forEach(row => {
      row.classList.toggle('selected', state.data.items[Number(row.dataset.i)].category === state.selected);
    });

    if (!state.selected) {
      box.innerHTML = '<div class="empty">Select a category in the table above to see its products.</div>';
      return;
    }

    // most urgent first; products with no sales go last
    const list = state.products
      .filter(p => p.category === state.selected)
      .sort((a, b) => ((a.daysLeft === null) - (b.daysLeft === null)) || (a.daysLeft - b.daysLeft));

    const rows = list.map(p =>
      '<tr><td><div class="prod-cell">' + UI.tile(p.name) + '<span>' + escapeHtml(p.name) + '</span></div></td>' +
      '<td class="num"><strong>' + p.stock + '</strong></td>' +
      '<td class="num">' + (p.avgDaily || 0).toFixed(2) + '</td>' +
      '<td class="num">' + UI.daysText(p) + '</td>' +
      '<td>' + UI.pill(p.status) + '</td>' +
      '<td class="num">' + (p.reorderQty > 0 ? '<strong>' + p.reorderQty + '</strong>' : '<span class="muted">-</span>') + '</td></tr>'
    ).join('');

    box.innerHTML =
      '<div class="card-head"><h3>Products in ' + escapeHtml(state.selected) + ' <span class="muted">(' + list.length + ')</span></h3>' +
        '<a class="see-all" href="#/products?q=' + encodeURIComponent(state.selected) + '">Open in Products</a></div>' +
      '<div class="table-wrap"><table class="table"><thead><tr>' +
        '<th>Product</th><th class="num">Stock</th><th class="num">Avg/day</th><th class="num">Days left</th>' +
        '<th>Status</th><th class="num">Reorder</th></tr></thead><tbody>' + rows + '</tbody></table></div>';
  }

  Pages.categories = {
    title: 'Categories',
    sub: 'Stock and sales by category',

    async render(v, params, ctx) {
      view = v;
      await load();
      if (parseHash().id !== 'categories') return;
      ctx.actions.innerHTML = '<a class="btn btn-dark" href="#/products">' + icon('box', 16) + 'Manage products</a>';
      drawAll();
    }
  };
})();