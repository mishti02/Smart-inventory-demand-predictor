// ===== Dashboard page =====
(function () {
  const STATUS_COLORS = { critical: '#e04a4a', warning: '#f0a30a', safe: '#2f9e63', none: '#b9b8ec' };
  const BAR_COLORS = ['peach', 'lilac', 'mint', 'pink', 'sky', 'lime', 'peach'];

  function greeting() {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }

  function statCard(color, iconName, title, big, extraHtml, barsHtml) {
    return '<div class="stat" style="--c: var(--' + color + ')">' +
             '<div class="stat-top"><span class="stat-ico">' + icon(iconName, 16) + '</span>' + title + '</div>' +
             '<div class="stat-body">' +
               '<div><div class="stat-big">' + big + '</div><div class="stat-sub">' + extraHtml + '</div></div>' +
               barsHtml +
             '</div></div>';
  }

  Pages.dashboard = {
    // a getter, so the greeting is worked out again each time the page is shown
    get title() { return greeting() + ', Shop Owner!'; },
    sub: '',

    async render(view, params, ctx) {
      // two requests at the same time
      const [dash, prod] = await Promise.all([api('/api/dashboard'), api('/api/products')]);
      if (parseHash().id !== 'dashboard') return;     // the user already moved to another page

      const c = dash.cards;
      const products = prod.items;

      // summary line and the button at the top right
      let summary;
      if (c.totalProducts === 0) {
        summary = 'Add your first product to get started.';
      } else if (c.runningOutIn3Days === 0) {
        summary = 'No products will run out within 3 days.';
      } else {
        summary = c.runningOutIn3Days + (c.runningOutIn3Days === 1 ? ' product' : ' products') +
                  ' will run out within 3 days.';
      }
      document.getElementById('pageSub').textContent = summary;
      ctx.actions.innerHTML = '<a class="btn btn-dark" href="#/reorders">' + icon('truck', 16) +
                              'Reorder list (' + c.reorderItems + ')</a>';

      if (c.totalProducts === 0) {
        view.innerHTML = '<div class="card"><div class="empty">No products yet.<br>' +
                         '<a class="see-all" href="#/products">Add your first product</a></div></div>';
        return;
      }

      // ----- data for the four stat cards -----
      const stockBars = products.map(p => p.stock).sort((a, b) => b - a).slice(0, 8);
      const dailyBars = dash.daily.map(d => d.units);
      const statusBars = [c.critical, c.warning, c.safe];
      const reorderBars = products.map(p => p.reorderQty).filter(q => q > 0).sort((a, b) => b - a).slice(0, 8);

      const cards =
        statCard('lime', 'box', 'Total Products', c.totalProducts,
                 '<span>' + c.categories + (c.categories === 1 ? ' category' : ' categories') +
                 ' &middot; ' + c.totalStock + ' units</span>',
                 Charts.miniBars(stockBars)) +
        statCard('mint', 'cart', 'Units Sold (7 days)', c.unitsSold7,
                 UI.deltaChip(c.soldChangePct, true) + '<span>vs previous 7 days</span>',
                 Charts.miniBars(dailyBars)) +
        statCard('pink', 'trend', 'Needs Attention', c.needAttention,
                 '<span>' + c.critical + ' critical &middot; ' + c.warning + ' warning</span>',
                 Charts.miniBars(statusBars)) +
        statCard('lilac', 'truck', 'Reorder Units', c.reorderUnits,
                 '<span>' + c.reorderItems + (c.reorderItems === 1 ? ' item' : ' items') + ' to order</span>',
                 Charts.miniBars(reorderBars));

      // ----- donut: stock by status -----
      const noSales = products.filter(p => p.status === 'SAFE' && !p.avgDaily).length;
      const segments = [
        { label: 'Critical',               value: c.critical,          color: STATUS_COLORS.critical },
        { label: 'Warning',                value: c.warning,           color: STATUS_COLORS.warning },
        { label: 'Selling normally',       value: c.safe - noSales,    color: STATUS_COLORS.safe },
        { label: 'No sales in 7 days',     value: noSales,             color: STATUS_COLORS.none }
      ];
      const legend = segments.map(s =>
        '<li><span class="dot" style="background:' + s.color + '"></span>' + s.label +
        '<span class="count">' + s.value + ' <small>(' + Math.round(s.value / c.totalProducts * 100) + '%)</small></span></li>'
      ).join('');

      // ----- pill bars: units sold per day -----
      const maxDaily = Math.max(...dailyBars);
      let chartHtml;
      if (maxDaily === 0) {
        chartHtml = '<div class="empty">No sales in the last 7 days.</div>';
      } else {
        chartHtml = Charts.pillBars(dash.daily.map((d, i) => ({
          label: d.label,
          value: d.units,
          color: BAR_COLORS[i % BAR_COLORS.length],
          tipTitle: UI.formatDay(d.date),
          tipValue: d.units + (d.units === 1 ? ' unit' : ' units')
        })));
      }

      // ----- recent sales table -----
      const salesRows = dash.recentSales.length === 0
        ? '<tr><td colspan="4" class="muted">No sales recorded yet.</td></tr>'
        : dash.recentSales.map(s =>
            '<tr><td><div class="prod-cell">' + UI.tile(s.product) + '<span>' + escapeHtml(s.product) + '</span></div></td>' +
            '<td>' + escapeHtml(s.category) + '</td>' +
            '<td class="num">' + s.quantity + '</td>' +
            '<td>' + UI.formatDay(s.date) + '</td></tr>').join('');

      // ----- running out soon -----
      const urgentHtml = dash.topUrgent.map(p =>
        '<div class="urgent-item">' + UI.tile(p.name) +
        '<div><div class="urgent-name">' + escapeHtml(p.name) + '</div>' +
        '<div class="urgent-meta">Stock ' + p.stock + ' &middot; lead ' + p.leadTimeDays + ' days</div></div>' +
        '<div class="urgent-right">' + UI.pill(p.status) + '<span>' + UI.daysText(p) + '</span></div>' +
        UI.shelfBar(p) + '</div>').join('');

      // ----- put it all on the page -----
      view.innerHTML =
        '<div class="dash">' +
          '<section class="card">' +
            '<div class="card-head"><h3>Stock Results</h3><span class="chip">Last 7 days</span></div>' +
            '<div class="stat-row">' + cards + '</div>' +
          '</section>' +

          '<div class="row-2">' +
            '<section class="card"><div class="card-head"><h3>Stock by status</h3></div>' +
              '<div class="donut-wrap">' + Charts.donut(segments, c.totalProducts, 'Products') +
              '<ul class="legend">' + legend + '</ul></div></section>' +
            '<section class="card"><div class="card-head"><h3>Units sold per day</h3>' +
              '<span class="muted">' + c.unitsSold7 + ' units in 7 days</span></div>' + chartHtml + '</section>' +
          '</div>' +

          '<div class="row-3">' +
            '<section class="card"><div class="card-head"><h3>Recent sales</h3>' +
              '<a class="see-all" href="#/sales">See all</a></div>' +
              '<table class="table"><thead><tr><th>Product</th><th>Category</th><th class="num">Qty</th><th>Date</th></tr></thead>' +
              '<tbody>' + salesRows + '</tbody></table></section>' +
            '<section class="card"><div class="card-head"><h3>Running out soon</h3>' +
              '<a class="see-all" href="#/predictions">All predictions</a></div>' +
              '<div class="urgent">' + urgentHtml + '</div></section>' +
          '</div>' +
        '</div>';

      if (maxDaily > 0) {
        Charts.wirePillBars(view.querySelector('.pc'), dailyBars.indexOf(maxDaily));
      }
    }
  };
})();