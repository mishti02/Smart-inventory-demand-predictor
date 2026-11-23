// ===== Smart Inventory web app: the shell, the router and helpers shared by every page =====

// Every page file adds itself here later, for example:  Pages.dashboard = { ... }
const Pages = {};

const ROUTES = [
  { id: 'dashboard',   label: 'Dashboard',   icon: 'grid',    section: 'MAIN MENU', title: 'Dashboard',        sub: 'An overview of your stock and sales' },
  { id: 'products',    label: 'Products',    icon: 'box',     section: 'MAIN MENU', title: 'Products Inventory', sub: 'All products, stock levels and status' },
  { id: 'categories',  label: 'Categories',  icon: 'layers',  section: 'MAIN MENU', title: 'Categories',       sub: 'Stock and sales by category' },
  { id: 'sales',       label: 'Sales',       icon: 'cart',    section: 'STOCK',     title: 'Sales',            sub: 'Record sales and see the history' },
  { id: 'predictions', label: 'Predictions', icon: 'trend',   section: 'STOCK',     title: 'Predictions',      sub: 'When will each product run out?' },
  { id: 'reorders',    label: 'Reorders',    icon: 'truck',   section: 'STOCK',     title: 'Reorders',         sub: 'What to order, and how much' },
  { id: 'reports',     label: 'Reports',     icon: 'chart',   section: 'GENERAL',   title: 'Reports',          sub: 'Charts, top sellers and exports' },
  { id: 'settings',    label: 'Settings',    icon: 'sliders', section: 'GENERAL',   title: 'Settings',         sub: 'Thresholds, defaults and storage' }
];

// ---------- helpers shared by all pages ----------

// Asks the Java server for JSON. Throws an Error with a readable message if it fails.
async function api(path) {
  const response = await fetch(path);
  if (!response.ok) {
    let message = 'Request failed (' + response.status + ')';
    try {
      const body = await response.json();
      if (body.error) message = body.error;
    } catch (e) { /* the answer was not JSON */ }
    throw new Error(message);
  }
  return response.json();
}

// Makes text safe to put inside HTML (a product called <b>x</b> must not become bold)
function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, ch => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[ch]));
}

// ---------- sidebar ----------

function buildNav() {
  // group the routes by section, keeping their order
  const sections = [];
  ROUTES.forEach(route => {
    let section = sections.find(s => s.name === route.section);
    if (!section) {
      section = { name: route.section, items: [] };
      sections.push(section);
    }
    section.items.push(route);
  });

  document.getElementById('nav').innerHTML = sections.map((section, index) => `
    <div class="nav-section">
      <button class="nav-label" ${index === 0 ? 'disabled' : ''}>
        <span>${section.name}</span>
        ${index === 0 ? '' : '<span class="caret">' + icon('chevron-up', 14) + '</span>'}
      </button>
      <div class="nav-items">
        ${section.items.map(route => `
          <a class="nav-item" data-route="${route.id}" href="#/${route.id}">
            <span class="nav-ico">${icon(route.icon, 18)}</span>
            <span class="nav-text">${route.label}</span>
          </a>`).join('')}
      </div>
    </div>`).join('');

  // clicking a section name folds it (the first section is fixed)
  document.querySelectorAll('.nav-label:not([disabled])').forEach(button => {
    button.addEventListener('click', () => button.closest('.nav-section').classList.toggle('collapsed'));
  });
}

// ---------- top bar ----------

function wireTopbar() {
  document.getElementById('globalSearch').addEventListener('keydown', event => {
    if (event.key === 'Enter') {
      const text = event.target.value.trim();
      location.hash = '#/products' + (text ? '?q=' + encodeURIComponent(text) : '');
    }
  });
  document.getElementById('bellBtn').addEventListener('click', () => {
    location.hash = '#/predictions';
  });
}

// The bell badge (number of CRITICAL products) and the storage name in the user chip
async function refreshStatus() {
  try {
    const data = await api('/api/dashboard');
    const critical = data.cards.critical;
    const badge = document.getElementById('bellBadge');
    badge.textContent = critical;
    badge.hidden = critical === 0;
    document.getElementById('userStorage').textContent = data.storage;
  } catch (error) {
    document.getElementById('userStorage').textContent = 'Server not reachable';
  }
}

// ---------- router: the address #/products decides which page is shown ----------

function parseHash() {
  const raw = location.hash.replace(/^#\/?/, '');      // "#/products?q=milk" becomes "products?q=milk"
  const parts = raw.split('?');
  const id = ROUTES.some(route => route.id === parts[0]) ? parts[0] : 'dashboard';
  return { id: id, params: new URLSearchParams(parts[1] || '') };
}

async function render() {
  const { id, params } = parseHash();
  const route = ROUTES.find(r => r.id === id);
  const page = Pages[id];

  // highlight the active item in the sidebar
  document.querySelectorAll('.nav-item').forEach(link => {
    link.classList.toggle('active', link.dataset.route === id);
  });

  // page title, subtitle and an empty spot for the page's buttons
  document.title = route.title + ' - Smart Inventory';
  document.getElementById('pageTitle').textContent = (page && page.title) || route.title;
  document.getElementById('pageSub').textContent = (page && page.sub) || route.sub;
  const actions = document.getElementById('pageActions');
  actions.innerHTML = '';

  const view = document.getElementById('view');
  view.innerHTML = '';

  try {
    if (page) {
      await page.render(view, params, { actions: actions });
    } else {
      await renderPlaceholder(view, route, params);
    }
  } catch (error) {
    view.innerHTML = '<div class="card error-card"><div class="card-head"><h3>Something went wrong</h3></div>' +
                     '<p class="muted">' + escapeHtml(error.message) + '</p></div>';
  }
}

// Shown for pages that are not built yet. It also proves the shell can read your Java data.
async function renderPlaceholder(view, route, params) {
  const data = await api('/api/dashboard');
  const c = data.cards;
  const search = params.get('q');

  view.innerHTML = `
    <div class="card">
      <div class="card-head"><h3>${route.title}</h3></div>
      <p class="muted">This page is built in a later step. The shell and the connection to your Java data already work:</p>
      <div class="chips">
        <span class="chip">${c.totalProducts} products</span>
        <span class="chip">${c.totalStock} units in stock</span>
        <span class="chip">${c.unitsSold7} units sold in 7 days</span>
        <span class="chip danger">${c.critical} critical</span>
      </div>
      ${search ? '<p class="muted">Search text received: <strong>' + escapeHtml(search) + '</strong></p>' : ''}
    </div>`;
}

// ---------- start ----------

function start() {
  document.querySelectorAll('[data-icon]').forEach(el => { el.innerHTML = icon(el.dataset.icon, 20); });
  document.getElementById('brandLogo').innerHTML = icon('box', 20);

  buildNav();
  wireTopbar();

  window.addEventListener('hashchange', render);   // runs on every click of a menu item
  render();
  refreshStatus();
  setInterval(refreshStatus, 15000);                // refresh the badge every 15 seconds
}

// DOMContentLoaded fires after every script has been read, so page files added later are ready
document.addEventListener('DOMContentLoaded', start);