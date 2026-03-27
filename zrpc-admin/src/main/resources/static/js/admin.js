'use strict';

const API = '/api';

// ---- Utilities ----

async function get(path) {
  const res = await fetch(API + path);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function put(path, body) {
  const res = await fetch(API + path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

function fmt(n, digits = 1) {
  return Number.isFinite(n) ? n.toFixed(digits) : '—';
}

function pct(n) {
  return Number.isFinite(n) ? (n * 100).toFixed(1) + '%' : '—';
}

function escHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

// ---- Navigation ----

const pages = document.querySelectorAll('.page');
const navItems = document.querySelectorAll('.nav-item');
const pageTitle = document.getElementById('page-title');

function navigate(id) {
  pages.forEach(p => p.classList.toggle('active', p.id === id));
  navItems.forEach(n => n.classList.toggle('active', n.dataset.page === id));
  const titles = {
    'page-dashboard': 'Dashboard',
    'page-services':  'Services',
    'page-providers': 'Providers',
    'page-metrics':   'Metrics'
  };
  pageTitle.textContent = titles[id] || '';
  loadPage(id);
}

navItems.forEach(n => n.addEventListener('click', () => navigate(n.dataset.page)));

function loadPage(id) {
  if (id === 'page-dashboard') loadDashboard();
  else if (id === 'page-services') loadServices();
  else if (id === 'page-providers') loadProviders();
  else if (id === 'page-metrics') loadMetrics();
}

document.getElementById('refresh-btn').addEventListener('click', () => {
  const active = document.querySelector('.page.active');
  if (active) loadPage(active.id);
});

// ---- Dashboard ----

async function loadDashboard() {
  try {
    const d = await get('/dashboard');

    document.getElementById('stat-services').textContent = d.totalServices ?? 0;
    document.getElementById('stat-providers').textContent = d.totalProviders ?? 0;
    document.getElementById('stat-calls').textContent = d.totalCalls ?? 0;
    document.getElementById('stat-qps').textContent = fmt(d.totalQps, 1);
    document.getElementById('stat-latency').textContent = fmt(d.avgLatencyMs, 1) + ' ms';
    document.getElementById('stat-error').textContent = pct(d.errorRate);

    const regEl = document.getElementById('stat-registry');
    const avail = d.registryAvailable;
    regEl.innerHTML = `
      <span class="registry-badge">
        <span class="dot ${avail ? 'dot-green' : 'dot-red'}"></span>
        ${escHtml(d.registryType)} — ${avail ? 'Online' : 'Offline'}
      </span>`;
  } catch (e) {
    console.error(e);
  }
}

// ---- Services ----

let allServices = [];

async function loadServices() {
  const tbody = document.getElementById('services-tbody');
  tbody.innerHTML = '<tr><td colspan="6" class="loading">Loading…</td></tr>';
  try {
    allServices = await get('/services');
    renderServices(allServices);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="6" class="empty">Failed to load: ${escHtml(e.message)}</td></tr>`;
  }
}

function renderServices(list) {
  const tbody = document.getElementById('services-tbody');
  if (!list.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="empty">No services registered</td></tr>';
    return;
  }
  tbody.innerHTML = list.map(s => `
    <tr>
      <td><code>${escHtml(s.name)}</code></td>
      <td>${s.providers}</td>
      <td>${s.totalCalls}</td>
      <td>${fmt(s.qps, 1)}</td>
      <td>${fmt(s.avgLatencyMs, 1)} ms</td>
      <td>
        <span class="badge ${s.errorRate > 0.05 ? 'badge-danger' : 'badge-success'}">
          ${pct(s.errorRate)}
        </span>
      </td>
    </tr>`).join('');
}

document.getElementById('services-search').addEventListener('input', function () {
  const q = this.value.trim().toLowerCase();
  renderServices(q ? allServices.filter(s => s.name.toLowerCase().includes(q)) : allServices);
});

// ---- Providers ----

let allProviders = [];

async function loadProviders() {
  const tbody = document.getElementById('providers-tbody');
  tbody.innerHTML = '<tr><td colspan="8" class="loading">Loading…</td></tr>';
  try {
    allProviders = await get('/providers');
    renderProviders(allProviders);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="8" class="empty">Failed: ${escHtml(e.message)}</td></tr>`;
  }
}

function renderProviders(list) {
  const tbody = document.getElementById('providers-tbody');
  if (!list.length) {
    tbody.innerHTML = '<tr><td colspan="8" class="empty">No providers</td></tr>';
    return;
  }
  tbody.innerHTML = list.map(p => {
    const id = escHtml(p.id);
    return `
    <tr>
      <td><code>${escHtml(p.host)}:${escHtml(String(p.port))}</code></td>
      <td><span class="badge badge-info">${escHtml(p.protocol)}</span></td>
      <td><small>${escHtml(p.serviceName)}</small></td>
      <td>${escHtml(p.version || '—')}</td>
      <td>${escHtml(p.application || '—')}</td>
      <td>
        <label class="toggle">
          <input type="checkbox" ${p.enabled ? 'checked' : ''}
            onchange="toggleProvider('${id}', this.checked)">
          <span class="slider"></span>
        </label>
      </td>
      <td>
        <input class="weight-input" type="number" min="0" max="1000" value="${p.weight}"
          id="w-${id}">
        <button class="weight-save" onclick="saveWeight('${id}')">Save</button>
      </td>
      <td>
        <span class="badge ${p.enabled ? 'badge-success' : 'badge-danger'}">
          ${p.enabled ? 'Online' : 'Offline'}
        </span>
      </td>
    </tr>`;
  }).join('');
}

async function toggleProvider(id, enabled) {
  try {
    await put(`/providers/${encodeURIComponent(id)}/enabled`, { enabled });
  } catch (e) {
    alert('Failed to update: ' + e.message);
    loadProviders();
  }
}

async function saveWeight(id) {
  const input = document.getElementById('w-' + id);
  if (!input) return;
  const weight = parseInt(input.value, 10);
  if (isNaN(weight) || weight < 0) { alert('Invalid weight'); return; }
  try {
    await put(`/providers/${encodeURIComponent(id)}/weight`, { weight });
    showToast('Weight updated');
  } catch (e) {
    alert('Failed: ' + e.message);
  }
}

document.getElementById('providers-search').addEventListener('input', function () {
  const q = this.value.trim().toLowerCase();
  renderProviders(q ? allProviders.filter(p =>
    p.host.includes(q) || p.serviceName.toLowerCase().includes(q)
  ) : allProviders);
});

// ---- Metrics ----

let allMetrics = [];

async function loadMetrics() {
  const tbody = document.getElementById('metrics-tbody');
  tbody.innerHTML = '<tr><td colspan="7" class="loading">Loading…</td></tr>';
  try {
    allMetrics = await get('/metrics');
    renderMetrics(allMetrics);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="7" class="empty">Failed: ${escHtml(e.message)}</td></tr>`;
  }
}

function renderMetrics(list) {
  const tbody = document.getElementById('metrics-tbody');
  if (!list.length) {
    tbody.innerHTML = '<tr><td colspan="7" class="empty">No metrics yet — invoke some RPCs first</td></tr>';
    return;
  }
  tbody.innerHTML = list.map(m => `
    <tr>
      <td><small>${escHtml(m.service)}</small></td>
      <td><code>${escHtml(m.method)}</code></td>
      <td>${m.totalCalls}</td>
      <td>${fmt(m.qps, 2)}</td>
      <td>${fmt(m.avgLatencyMs, 1)} ms</td>
      <td>${fmt(m.maxLatencyMs, 0)} ms</td>
      <td>
        <span class="badge ${m.errorRate > 0.05 ? 'badge-danger' : 'badge-success'}">
          ${pct(m.errorRate)}
        </span>
      </td>
    </tr>`).join('');
}

document.getElementById('metrics-search').addEventListener('input', function () {
  const q = this.value.trim().toLowerCase();
  renderMetrics(q ? allMetrics.filter(m =>
    m.service.toLowerCase().includes(q) || m.method.toLowerCase().includes(q)
  ) : allMetrics);
});

// ---- Toast ----

function showToast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.style.opacity = '1';
  t.style.transform = 'translateY(0)';
  setTimeout(() => {
    t.style.opacity = '0';
    t.style.transform = 'translateY(20px)';
  }, 2000);
}

// ---- Auto-refresh (30 s) ----
setInterval(() => {
  const active = document.querySelector('.page.active');
  if (active) loadPage(active.id);
}, 30000);

// ---- Init ----
navigate('page-dashboard');
