// ===== Prediction defaults, shared with the Predictions and Reorders pages =====
const UserDefaults = {
  KEY: 'si-defaults',
  STANDARD: { window: 7, method: 'simple', safety: 2, cover: 14 },
  version: 0,    // goes up whenever the defaults change, so the other pages know to reload them

  get() {
    let saved = {};
    try { saved = JSON.parse(localStorage.getItem(UserDefaults.KEY)) || {}; } catch (e) { saved = {}; }
    const d = Object.assign({}, UserDefaults.STANDARD, saved);
    const clamp = (value, min, max, fallback) => {
      const n = parseInt(value, 10);
      return isNaN(n) ? fallback : Math.max(min, Math.min(max, n));
    };
    return {
      window: [0, 7, 14].includes(Number(d.window)) ? Number(d.window) : 7,
      method: d.method === 'weighted' ? 'weighted' : 'simple',
      safety: clamp(d.safety, 0, 60, 2),
      cover: clamp(d.cover, 1, 120, 14)
    };
  },

  set(values) {
    try { localStorage.setItem(UserDefaults.KEY, JSON.stringify(values)); } catch (e) { /* storage is blocked */ }
    UserDefaults.version++;
  },

  reset() {
    try { localStorage.removeItem(UserDefaults.KEY); } catch (e) { /* storage is blocked */ }
    UserDefaults.version++;
  }
};

// ===== Settings page =====
(function () {
  const draft = Object.assign({}, UserDefaults.STANDARD);
  let view = null;

  function segHtml(id, options, current) {
    return '<div class="seg" id="' + id + '">' + options.map(o =>
      '<button type="button" class="seg-btn' + (String(o[0]) === String(current) ? ' active' : '') +
      '" data-v="' + o[0] + '">' + o[1] + '</button>').join('') + '</div>';
  }

  function setActive(selector, button) {
    view.querySelectorAll(selector + ' .seg-btn').forEach(b => b.classList.toggle('active', b === button));
  }

  // ---------- prediction defaults ----------

  function defaultsHtml() {
    return '<section class="card">' +
      '<div class="card-head"><h3>Prediction defaults</h3></div>' +
      '<p class="muted set-hint">The values the Predictions and Reorders pages start with. They are saved in this browser.</p>' +
      '<div class="pred-controls">' +
        '<div class="ctl"><span class="ctl-label">Analysis window</span>' +
          segHtml('dfWindow', [[7, 'Last 7 days'], [14, 'Last 14 days'], [0, 'All history']], draft.window) + '</div>' +
        '<div class="ctl"><span class="ctl-label">Method</span>' +
          segHtml('dfMethod', [['simple', 'Simple average'], ['weighted', 'Weighted average']], draft.method) + '</div>' +
        '<label class="ctl"><span class="ctl-label">Safety days</span>' +
          '<input class="input num-input" id="dfSafety" type="number" min="0" max="60" step="1" value="' + draft.safety + '"></label>' +
        '<label class="ctl"><span class="ctl-label">Cover days</span>' +
          '<input class="input num-input" id="dfCover" type="number" min="1" max="120" step="1" value="' + draft.cover + '"></label>' +
      '</div>' +
      '<div class="set-actions">' +
        '<button class="btn btn-dark" id="dfSave">Save defaults</button>' +
        '<button class="btn btn-light" id="dfReset">Reset to standard</button>' +
      '</div></section>';
  }

  function wireNumber(selector, key, min, max) {
    const input = view.querySelector(selector);
    input.addEventListener('change', () => {
      let value = parseInt(input.value, 10);
      if (isNaN(value)) value = draft[key];
      value = Math.max(min, Math.min(max, value));
      input.value = value;
      draft[key] = value;
    });
  }

  function drawDefaults() {
    view.querySelector('#setDefaults').innerHTML = defaultsHtml();

    view.querySelector('#dfWindow').addEventListener('click', event => {
      const button = event.target.closest('[data-v]');
      if (!button) return;
      draft.window = Number(button.dataset.v);
      setActive('#dfWindow', button);
    });
    view.querySelector('#dfMethod').addEventListener('click', event => {
      const button = event.target.closest('[data-v]');
      if (!button) return;
      draft.method = button.dataset.v;
      setActive('#dfMethod', button);
    });
    wireNumber('#dfSafety', 'safety', 0, 60);
    wireNumber('#dfCover', 'cover', 1, 120);

    view.querySelector('#dfSave').addEventListener('click', () => {
      UserDefaults.set({ window: draft.window, method: draft.method, safety: draft.safety, cover: draft.cover });
      UI.toast('Defaults saved in this browser');
    });
    view.querySelector('#dfReset').addEventListener('click', () => {
      UserDefaults.reset();
      Object.assign(draft, UserDefaults.get());
      drawDefaults();
      UI.toast('Defaults reset to the standard values');
    });
  }

  // ---------- system information ----------

  function drawInfo(info) {
    const rows = [
      ['Storage', info.storage],
      ['Address', 'http://' + info.address],
      ['Data folder', info.dataFolder],
      ['Products', info.products],
      ['Sales records', info.salesRecords],
      ['Auto-save', 'every ' + info.autoSaveSeconds + ' seconds'],
      ['Live simulation', info.simulationRunning ? 'running' : 'stopped'],
      ['Status levels', 'Critical: 3 days or less, Warning: 7 days or less, otherwise Safe'],
      ['Java version', info.javaVersion]
    ];
    view.querySelector('#setInfo').innerHTML =
      '<section class="card"><div class="card-head"><h3>System</h3></div><div class="kv">' +
      rows.map(r => '<div class="kv-row"><span>' + r[0] + '</span><strong>' + escapeHtml(r[1]) + '</strong></div>').join('') +
      '</div></section>';
  }

  async function reloadInfo() {
    const info = await api('/api/settings');
    if (parseHash().id === 'settings') drawInfo(info);
    refreshStatus();
  }

  // ---------- data tools ----------

  function toolHtml(color, title, text, buttonId, buttonText, danger) {
    return '<div class="tool" style="--c: var(--' + color + ')"><div><h4>' + title + '</h4><p>' + text + '</p></div>' +
           '<button class="btn ' + (danger ? 'btn-danger' : 'btn-dark') + '" id="' + buttonId + '">' + buttonText + '</button></div>';
  }

  function toolsHtml() {
    return '<section class="card"><div class="card-head"><h3>Data tools</h3></div><div class="tool-grid">' +
      toolHtml('lime', 'Save now', 'Writes everything to the storage right away. Changes are also saved after every action and every few seconds.', 'toolSave', 'Save now') +
      toolHtml('mint', 'Generate sample sales', 'Adds 14 days of random sales for every product. This lowers their stock. Useful for a demo.', 'toolSample', 'Generate') +
      toolHtml('lilac', 'Write CSV copy', 'Writes products.csv and sales.csv into the data folder, as a backup or to open in a spreadsheet.', 'toolCsv', 'Write CSV copy') +
      toolHtml('pink', 'Reset all data', 'Deletes every product and every sale. This cannot be undone.', 'toolReset', 'Reset all data', true) +
      '</div></section>';
  }

  async function runTool(path) {
    try {
      const result = await apiPost(path, {});
      UI.toast(result.message);
      await reloadInfo();
    } catch (error) {
      UI.toast(error.message, true);
    }
  }

  function wireTools() {
    view.querySelector('#toolSave').addEventListener('click', () => runTool('/api/data/save'));
    view.querySelector('#toolCsv').addEventListener('click', () => runTool('/api/data/csv'));

    view.querySelector('#toolSample').addEventListener('click', () => {
      UI.modal({
        title: 'Generate sample sales',
        bodyHtml: '<p class="modal-note">This adds 14 days of random sales for every product and lowers their stock. Continue?</p>',
        confirmText: 'Generate',
        onSubmit: async () => {
          const result = await apiPost('/api/data/sample', {});
          UI.toast(result.message);
          await reloadInfo();
        }
      });
    });

    view.querySelector('#toolReset').addEventListener('click', () => {
      UI.modal({
        title: 'Reset all data',
        danger: true,
        confirmText: 'Delete everything',
        bodyHtml: '<p class="modal-note">This deletes <strong>every product and every sale</strong> and cannot be undone. ' +
                  'Type <strong>RESET</strong> to confirm.</p>' +
                  '<label class="field"><span>Confirmation</span><input name="confirm" placeholder="RESET" autocomplete="off"></label>',
        onSubmit: async form => {
          const result = await apiPost('/api/data/reset', { confirm: new FormData(form).get('confirm') });
          UI.toast(result.message);
          await reloadInfo();
        }
      });
    });
  }

  // ---------- the page ----------

  Pages.settings = {
    title: 'Settings',
    sub: 'Defaults, system information and data tools',

    async render(v) {
      view = v;
      const info = await api('/api/settings');
      if (parseHash().id !== 'settings') return;

      Object.assign(draft, UserDefaults.get());
      view.innerHTML =
        '<div class="set-grid"><div id="setDefaults"></div><div id="setInfo"></div></div>' +
        '<div id="setTools">' + toolsHtml() + '</div>';

      drawDefaults();
      drawInfo(info);
      wireTools();
    }
  };
})();