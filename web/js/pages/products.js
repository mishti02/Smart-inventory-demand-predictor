// ===== Products page: list, search, filter, sort, add, edit, delete, restock =====
(function () {
  const state = { items: [], daily: [], filter: 'ALL', sort: 'urgent', search: '' };
  let view = null;   // the container of the page, set on every render

  const pad = id => '#' + String(id).padStart(4, '0');

  // days left minus lead time (smaller = more urgent). A product with no sales goes last.
  function urgency(p) { return p.daysLeft === null ? Infinity : p.daysLeft - p.leadTimeDays; }
  function byUrgency(a, b) {
    const x = urgency(a), y = urgency(b);
    return x === y ? 0 : (x < y ? -1 : 1);
  }

  const SORTERS = {
    urgent:    byUrgency,
    name:      (a, b) => a.name.localeCompare(b.name),
    stockAsc:  (a, b) => a.stock - b.stock,
    stockDesc: (a, b) => b.stock - a.stock,
    newest:    (a, b) => b.id - a.id
  };

  // the products that pass the status filter and the search, in the chosen order
  function visibleItems() {
    const text = state.search.trim().toLowerCase();
    return state.items
      .filter(p => state.filter === 'ALL' || p.status === state.filter)
      .filter(p => !text || p.name.toLowerCase().includes(text) || p.category.toLowerCase().includes(text))
      .sort(SORTERS[state.sort]);
  }

  async function load() {
    const [prod, dash] = await Promise.all([api('/api/products'), api('/api/dashboard')]);
    state.items = prod.items;
    state.daily = dash.daily;
  }

  // reload the data after a change and redraw (unless the user already left this page)
  async function refresh() {
    await load();
    if (parseHash().id !== 'products') return;
    drawTop();
    drawTable();
    refreshStatus();   // the bell badge
  }

  // ---------- the three cards on top ----------

  function drawTop() {
    const items = state.items;

    // 1. stock per category
    const stockByCategory = {};
    items.forEach(p => { stockByCategory[p.category] = (stockByCategory[p.category] || 0) + p.stock; });
    const cats = Object.entries(stockByCategory).sort((a, b) => b[1] - a[1]).slice(0, 8);
    let chart1;
    if (cats.length === 0) chart1 = '<div class="empty">No products yet.</div>';
    else if (cats.length < 3) chart1 = Charts.barList(cats.map(c => c[0]), cats.map(c => c[1]));
    else chart1 = Charts.radar(cats.map(c => c[0]), cats.map(c => c[1]));

    // 2. the products that will run out first
    const soon = items.filter(p => p.daysLeft !== null).sort(byUrgency).slice(0, 5);
    const soonRows = soon.length === 0
      ? '<tr><td colspan="3" class="muted">Nothing is selling yet.</td></tr>'
      : soon.map(p =>
          '<tr><td class="muted">' + pad(p.id) + '</td>' +
          '<td><div class="prod-cell">' + UI.tile(p.name) + '<span>' + escapeHtml(p.name) + '</span></div></td>' +
          '<td class="num"><span class="due ' + p.status.toLowerCase() + '">' + p.daysLeft.toFixed(1) + ' days</span></td></tr>'
        ).join('');

    // 3. units sold per day
    const total = state.daily.reduce((sum, d) => sum + d.units, 0);
    const chart3 = state.daily.length
      ? Charts.line(state.daily.map(d => ({ label: d.label, value: d.units })))
      : '<div class="empty">No data yet.</div>';

    view.querySelector('#prodTop').innerHTML =
      '<section class="card"><div class="card-head"><h3>Stock by category</h3><span class="muted">units</span></div>' +
        chart1 + '</section>' +
      '<section class="card"><div class="card-head"><h3>Running out soon</h3>' +
        '<a class="see-all" href="#/predictions">All predictions</a></div>' +
        '<table class="table"><thead><tr><th>ID</th><th>Product</th><th class="num">Runs out in</th></tr></thead>' +
        '<tbody>' + soonRows + '</tbody></table></section>' +
      '<section class="card"><div class="card-head"><h3>Units sold per day</h3>' +
        '<span class="muted">' + total + ' in 7 days</span></div>' + chart3 + '</section>';
  }

  // ---------- the products table ----------

  function rowHtml(p) {
    const status = p.status || 'SAFE';
    const days = p.daysLeft === null ? '<span class="muted">No sales</span>' : p.daysLeft.toFixed(1);
    const reorder = p.reorderQty > 0 ? '<strong>' + p.reorderQty + '</strong>' : '<span class="muted">-</span>';
    return '<tr>' +
      '<td><div class="prod-cell">' + UI.tile(p.name) + '<div><div>' + escapeHtml(p.name) +
        '</div><small class="muted">' + pad(p.id) + '</small></div></div></td>' +
      '<td>' + escapeHtml(p.category) + '</td>' +
      '<td class="num"><strong>' + p.stock + '</strong></td>' +
      '<td class="num">' + (p.avgDaily || 0).toFixed(2) + '</td>' +
      '<td class="num">' + days + '</td>' +
      '<td class="num">' + p.leadTimeDays + '</td>' +
      '<td>' + UI.pill(status) + '</td>' +
      '<td class="num">' + reorder + '</td>' +
      '<td><div class="row-actions">' +
        '<button class="icon-btn" data-act="restock" data-id="' + p.id + '" title="Restock">' + icon('plus', 16) + '</button>' +
        '<button class="icon-btn" data-act="edit" data-id="' + p.id + '" title="Edit">' + icon('edit', 16) + '</button>' +
        '<button class="icon-btn danger" data-act="delete" data-id="' + p.id + '" title="Delete">' + icon('trash', 16) + '</button>' +
      '</div></td></tr>';
  }

  function drawTable() {
    const all = state.items;
    const count = status => all.filter(p => p.status === status).length;

    view.querySelector('#prodChips').innerHTML =
      [['ALL', 'All', all.length], ['CRITICAL', 'Critical', count('CRITICAL')],
       ['WARNING', 'Warning', count('WARNING')], ['SAFE', 'Safe', count('SAFE')]]
        .map(c => '<button type="button" class="fchip' + (state.filter === c[0] ? ' active' : '') +
                  '" data-filter="' + c[0] + '">' + c[1] + '<small>' + c[2] + '</small></button>').join('');

    const shown = visibleItems();
    view.querySelector('#prodCount').textContent =
      shown.length === all.length ? '(' + all.length + ')' : '(' + shown.length + ' of ' + all.length + ')';

    view.querySelector('#prodBody').innerHTML = shown.length === 0
      ? '<tr><td colspan="9" class="empty">' + (all.length === 0
          ? 'No products yet. Click "Add Product" to create the first one.'
          : 'No product matches your search or filter.') + '</td></tr>'
      : shown.map(rowHtml).join('');
  }

  // ---------- pop-up forms ----------

  const findProduct = id => state.items.find(p => p.id === id);

  function formValues(form) {
    const data = new FormData(form);
    return { name: data.get('name'), category: data.get('category'),
             stock: data.get('stock'), leadTimeDays: data.get('leadTimeDays') };
  }

  // the same form is used to add and to edit (p is empty when adding)
  function productForm(p) {
    const categories = [...new Set(state.items.map(x => x.category))].sort();
    const options = categories.map(c => '<option value="' + escapeHtml(c) + '"></option>').join('');
    return '<label class="field"><span>Name</span><input name="name" maxlength="60" placeholder="e.g. Milk 1L" value="' +
             escapeHtml(p ? p.name : '') + '"></label>' +
           '<label class="field"><span>Category</span><input name="category" list="catList" maxlength="40" ' +
             'placeholder="e.g. Dairy" value="' + escapeHtml(p ? p.category : '') + '">' +
             '<datalist id="catList">' + options + '</datalist></label>' +
           '<div class="field-row">' +
             '<label class="field"><span>Stock (units)</span><input name="stock" type="number" min="0" step="1" value="' +
               (p ? p.stock : '') + '"></label>' +
             '<label class="field"><span>Lead time (days)</span><input name="leadTimeDays" type="number" min="0" step="1" value="' +
               (p ? p.leadTimeDays : '') + '"></label>' +
           '</div>';
  }

  function openAdd() {
    UI.modal({
      title: 'Add product',
      bodyHtml: productForm(null),
      confirmText: 'Add product',
      onSubmit: async form => {
        const result = await apiPost('/api/products/add', formValues(form));
        UI.toast(result.message);
        await refresh();
      }
    });
  }

  function openEdit(p) {
    UI.modal({
      title: 'Edit product',
      bodyHtml: productForm(p),
      confirmText: 'Save changes',
      onSubmit: async form => {
        const result = await apiPost('/api/products/update', Object.assign({ id: p.id }, formValues(form)));
        UI.toast(result.message);
        await refresh();
      }
    });
  }

  function openRestock(p) {
    UI.modal({
      title: 'Restock ' + p.name,
      bodyHtml: '<p class="modal-note">Current stock: <strong>' + p.stock + ' units</strong>.</p>' +
                '<label class="field"><span>Units to add</span>' +
                '<input name="quantity" type="number" min="1" step="1" value="10"></label>',
      confirmText: 'Restock',
      onSubmit: async form => {
        const result = await apiPost('/api/products/restock',
          { id: p.id, quantity: new FormData(form).get('quantity') });
        UI.toast(result.message);
        await refresh();
      }
    });
  }

  function openDelete(p) {
    UI.modal({
      title: 'Delete product',
      bodyHtml: '<p class="modal-note">Delete <strong>' + escapeHtml(p.name) +
                '</strong> and all of its sales history? This cannot be undone.</p>',
      confirmText: 'Delete',
      danger: true,
      onSubmit: async () => {
        const result = await apiPost('/api/products/delete', { id: p.id });
        UI.toast(result.message);
        await refresh();
      }
    });
  }

  // ---------- the page ----------

  Pages.products = {
    title: 'Products Inventory',
    sub: 'All products, stock levels and status',

    async render(v, params, ctx) {
      view = v;
      state.search = params.get('q') || '';     // set by the search box in the top bar
      await load();
      if (parseHash().id !== 'products') return;

      ctx.actions.innerHTML = '<button class="btn btn-dark" id="addProduct">' + icon('plus', 16) + 'Add Product</button>';
      document.getElementById('addProduct').addEventListener('click', openAdd);

      view.innerHTML =
        '<div class="prod-top" id="prodTop"></div>' +
        '<section class="card">' +
          '<div class="card-head"><h3>Products list <span class="muted" id="prodCount"></span></h3></div>' +
          '<div class="toolbar">' +
            '<input class="input" id="prodSearch" type="search" placeholder="Search name or category..." autocomplete="off">' +
            '<div class="fchips" id="prodChips"></div>' +
            '<select class="select" id="prodSort">' +
              '<option value="urgent">Sort: most urgent</option>' +
              '<option value="name">Sort: name A-Z</option>' +
              '<option value="stockAsc">Sort: stock, low to high</option>' +
              '<option value="stockDesc">Sort: stock, high to low</option>' +
              '<option value="newest">Sort: newest first</option>' +
            '</select>' +
          '</div>' +
          '<div class="table-wrap"><table class="table"><thead><tr>' +
            '<th>Product</th><th>Category</th><th class="num">Stock</th><th class="num">Avg/day</th>' +
            '<th class="num">Days left</th><th class="num">Lead</th><th>Status</th><th class="num">Reorder</th><th></th>' +
          '</tr></thead><tbody id="prodBody"></tbody></table></div>' +
        '</section>';

      const search = view.querySelector('#prodSearch');
      search.value = state.search;
      search.addEventListener('input', () => { state.search = search.value; drawTable(); });

      const sort = view.querySelector('#prodSort');
      sort.value = state.sort;
      sort.addEventListener('change', () => { state.sort = sort.value; drawTable(); });

      view.querySelector('#prodChips').addEventListener('click', event => {
        const chip = event.target.closest('[data-filter]');
        if (chip) { state.filter = chip.dataset.filter; drawTable(); }
      });

      // one listener for all the row buttons (event delegation)
      view.querySelector('#prodBody').addEventListener('click', event => {
        const button = event.target.closest('button[data-act]');
        if (!button) return;
        const product = findProduct(Number(button.dataset.id));
        if (!product) return;
        if (button.dataset.act === 'restock') openRestock(product);
        else if (button.dataset.act === 'edit') openEdit(product);
        else openDelete(product);
      });

      drawTop();
      drawTable();
    }
  };
})();