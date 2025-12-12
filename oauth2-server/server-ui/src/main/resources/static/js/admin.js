/* global window, document, fetch */

function csrfHeaders() {
  const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
  const headerName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
  if (!token || !headerName) {
    return {};
  }
  return { [headerName]: token };
}

async function apiFetch(url, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...csrfHeaders(),
    ...(options.headers || {}),
  };

  const resp = await fetch(url, {
    credentials: 'same-origin',
    ...options,
    headers,
  });

  const contentType = resp.headers.get('content-type') || '';
  const hasJson = contentType.includes('application/json');
  const body = hasJson ? await resp.json().catch(() => null) : await resp.text().catch(() => null);

  if (!resp.ok) {
    const msg = (body && body.message) ? body.message : (typeof body === 'string' ? body : resp.statusText);
    const err = new Error(msg || `Request failed (${resp.status})`);
    err.status = resp.status;
    err.body = body;
    throw err;
  }

  return body;
}

function showError(elId, err) {
  const el = document.getElementById(elId);
  if (!el) return;
  el.style.display = 'block';
  el.textContent = err?.message || String(err);
}

function clearError(elId) {
  const el = document.getElementById(elId);
  if (!el) return;
  el.style.display = 'none';
  el.textContent = '';
}

function csvToArray(v) {
  if (!v) return [];
  return String(v)
    .split(',')
    .map(s => s.trim())
    .filter(Boolean);
}

function escapeHtml(s) {
  return String(s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

async function loadClients() {
  clearError('clientsError');
  const tableHost = document.getElementById('clientsTable');
  tableHost.innerHTML = 'Loading…';

  try {
    const clients = await apiFetch('/api/admin/clients', { method: 'GET' });
    const rows = clients.map(c => {
      const scopes = (c.scopes || []).join(', ');
      const redirectUris = (c.redirectUris || []).join(', ');
      const grantTypes = (c.authorizationGrantTypes || []).join(', ');
      const authMethods = (c.clientAuthenticationMethods || []).join(', ');
      const enabled = !!c.enabled;
      return `
        <tr>
          <td><code>${escapeHtml(c.clientId)}</code></td>
          <td>${escapeHtml(c.clientName || '')}</td>
          <td>${enabled ? 'Enabled' : 'Disabled'}</td>
          <td>${escapeHtml(scopes)}</td>
          <td class="nowrap">${escapeHtml(redirectUris)}</td>
          <td>${escapeHtml(grantTypes)}</td>
          <td>${escapeHtml(authMethods)}</td>
          <td class="nowrap">
            <button type="button" data-action="edit" data-client-id="${escapeHtml(c.clientId)}">Edit</button>
            <button type="button" data-action="toggle" data-client-id="${escapeHtml(c.clientId)}" data-enabled="${enabled}">${enabled ? 'Disable' : 'Enable'}</button>
            <button type="button" data-action="delete" data-client-id="${escapeHtml(c.clientId)}">Delete</button>
          </td>
        </tr>
      `;
    }).join('');

    tableHost.innerHTML = `
      <table class="table" style="width:100%; border-collapse: collapse;">
        <thead>
          <tr>
            <th>Client ID</th>
            <th>Name</th>
            <th>Status</th>
            <th>Scopes</th>
            <th>Redirect URIs</th>
            <th>Grant Types</th>
            <th>Auth Methods</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>${rows || '<tr><td colspan="8">No clients found.</td></tr>'}</tbody>
      </table>
    `;

    tableHost.querySelectorAll('button[data-action]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const action = btn.getAttribute('data-action');
        const clientId = btn.getAttribute('data-client-id');
        if (!clientId) return;

        if (action === 'delete') {
          if (!window.confirm(`Delete client ${clientId}?`)) return;
          try {
            await apiFetch(`/api/admin/clients/${encodeURIComponent(clientId)}`, { method: 'DELETE' });
            await loadClients();
          } catch (e) {
            showError('clientsError', e);
          }
        }

        if (action === 'toggle') {
          const currentlyEnabled = btn.getAttribute('data-enabled') === 'true';
          const nextEnabled = !currentlyEnabled;
          try {
            await apiFetch(`/api/admin/clients/${encodeURIComponent(clientId)}/enabled?enabled=${nextEnabled}`, { method: 'POST', body: '{}' });
            await loadClients();
          } catch (e) {
            showError('clientsError', e);
          }
        }

        if (action === 'edit') {
          try {
            const c = await apiFetch(`/api/admin/clients/${encodeURIComponent(clientId)}`, { method: 'GET' });
            fillClientForm(c);
            window.scrollTo({ top: 0, behavior: 'smooth' });
          } catch (e) {
            showError('clientsError', e);
          }
        }
      });
    });
  } catch (e) {
    tableHost.innerHTML = '';
    showError('clientsError', e);
  }
}

function fillClientForm(c) {
  document.getElementById('clientId').value = c.clientId || '';
  document.getElementById('clientName').value = c.clientName || '';
  document.getElementById('clientSecret').value = '';
  document.getElementById('clientScopes').value = (c.scopes || []).join(',');
  document.getElementById('clientRedirectUris').value = (c.redirectUris || []).join(',');
  document.getElementById('clientGrantTypes').value = (c.authorizationGrantTypes || []).join(',');
  document.getElementById('clientAuthMethods').value = (c.clientAuthenticationMethods || []).join(',');
  document.getElementById('clientEnabled').value = String(!!c.enabled);
  document.getElementById('clientNotes').value = c.notes || '';
}

function resetClientForm() {
  document.getElementById('clientId').value = '';
  document.getElementById('clientName').value = '';
  document.getElementById('clientSecret').value = '';
  document.getElementById('clientScopes').value = '';
  document.getElementById('clientRedirectUris').value = '';
  document.getElementById('clientGrantTypes').value = 'authorization_code,refresh_token';
  document.getElementById('clientAuthMethods').value = 'client_secret_basic';
  document.getElementById('clientEnabled').value = 'true';
  document.getElementById('clientNotes').value = '';
}

async function saveClient() {
  clearError('clientsError');
  const payload = {
    clientId: document.getElementById('clientId').value.trim(),
    clientName: document.getElementById('clientName').value.trim(),
    clientSecret: document.getElementById('clientSecret').value.trim() || null,
    scopes: csvToArray(document.getElementById('clientScopes').value),
    redirectUris: csvToArray(document.getElementById('clientRedirectUris').value),
    authorizationGrantTypes: csvToArray(document.getElementById('clientGrantTypes').value),
    clientAuthenticationMethods: csvToArray(document.getElementById('clientAuthMethods').value),
    enabled: document.getElementById('clientEnabled').value === 'true',
    notes: document.getElementById('clientNotes').value.trim() || null,
  };

  if (!payload.clientId) {
    showError('clientsError', new Error('Client ID is required'));
    return;
  }

  await apiFetch('/api/admin/clients', { method: 'PUT', body: JSON.stringify(payload) });
  await loadClients();
}

async function loadScopes() {
  clearError('scopesError');
  const host = document.getElementById('scopesTable');
  host.innerHTML = 'Loading…';

  try {
    const scopes = await apiFetch('/api/admin/scopes', { method: 'GET' });
    const rows = scopes.map(s => {
      return `
        <tr>
          <td><code>${escapeHtml(s.scope)}</code></td>
          <td>${escapeHtml(s.description || '')}</td>
          <td>${s.system ? 'System' : 'Admin'}</td>
          <td>${s.enabled ? 'Enabled' : 'Disabled'}</td>
          <td class="nowrap">
            <button type="button" data-action="edit" data-scope="${escapeHtml(s.scope)}">Edit</button>
            <button type="button" data-action="delete" data-scope="${escapeHtml(s.scope)}">Delete</button>
          </td>
        </tr>
      `;
    }).join('');

    host.innerHTML = `
      <table class="table" style="width:100%; border-collapse: collapse;">
        <thead>
          <tr>
            <th>Scope</th>
            <th>Description</th>
            <th>Type</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>${rows || '<tr><td colspan="5">No scopes found.</td></tr>'}</tbody>
      </table>
    `;

    host.querySelectorAll('button[data-action]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const action = btn.getAttribute('data-action');
        const scope = btn.getAttribute('data-scope');
        if (!scope) return;

        if (action === 'edit') {
          const s = scopes.find(x => x.scope === scope);
          if (!s) return;
          document.getElementById('scopeName').value = s.scope;
          document.getElementById('scopeDesc').value = s.description || '';
          document.getElementById('scopeEnabled').value = String(!!s.enabled);
        }

        if (action === 'delete') {
          if (!window.confirm(`Delete scope ${scope}?`)) return;
          try {
            await apiFetch(`/api/admin/scopes/${encodeURIComponent(scope)}`, { method: 'DELETE' });
            await loadScopes();
          } catch (e) {
            showError('scopesError', e);
          }
        }
      });
    });
  } catch (e) {
    host.innerHTML = '';
    showError('scopesError', e);
  }
}

async function saveScope() {
  clearError('scopesError');
  const payload = {
    scope: document.getElementById('scopeName').value.trim(),
    description: document.getElementById('scopeDesc').value.trim() || null,
    enabled: document.getElementById('scopeEnabled').value === 'true',
  };
  if (!payload.scope) {
    showError('scopesError', new Error('Scope is required'));
    return;
  }

  await apiFetch('/api/admin/scopes', { method: 'PUT', body: JSON.stringify(payload) });
  await loadScopes();
}

async function loadDenyRules() {
  clearError('denyError');
  const host = document.getElementById('denyRulesTable');
  host.innerHTML = 'Loading…';

  try {
    const rules = await apiFetch('/api/admin/deny-rules', { method: 'GET' });
    const rows = rules.map(r => {
      const provider = r.providerId || '(global)';
      return `
        <tr>
          <td>${escapeHtml(String(r.id))}</td>
          <td>${escapeHtml(provider)}</td>
          <td>${escapeHtml(r.matchField)}</td>
          <td>${escapeHtml(r.matchType)}</td>
          <td><code>${escapeHtml(r.pattern)}</code></td>
          <td>${escapeHtml(r.reason || '')}</td>
          <td>${r.enabled ? 'Enabled' : 'Disabled'}</td>
          <td class="nowrap">
            <button type="button" data-action="delete" data-id="${escapeHtml(String(r.id))}">Delete</button>
          </td>
        </tr>
      `;
    }).join('');

    host.innerHTML = `
      <table class="table" style="width:100%; border-collapse: collapse;">
        <thead>
          <tr>
            <th>ID</th>
            <th>Provider</th>
            <th>Field</th>
            <th>Type</th>
            <th>Pattern</th>
            <th>Reason</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>${rows || '<tr><td colspan="8">No deny rules found.</td></tr>'}</tbody>
      </table>
    `;

    host.querySelectorAll('button[data-action="delete"]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = btn.getAttribute('data-id');
        if (!id) return;
        if (!window.confirm(`Delete deny rule ${id}?`)) return;
        try {
          await apiFetch(`/api/admin/deny-rules/${encodeURIComponent(id)}`, { method: 'DELETE' });
          await loadDenyRules();
        } catch (e) {
          showError('denyError', e);
        }
      });
    });
  } catch (e) {
    host.innerHTML = '';
    showError('denyError', e);
  }
}

async function createDenyRule() {
  clearError('denyError');
  const providerRaw = document.getElementById('denyProvider').value.trim();
  const payload = {
    providerId: providerRaw ? providerRaw : null,
    matchField: document.getElementById('denyField').value,
    matchType: document.getElementById('denyType').value,
    pattern: document.getElementById('denyPattern').value.trim(),
    reason: document.getElementById('denyReason').value.trim() || null,
    enabled: document.getElementById('denyEnabled').value === 'true',
  };
  if (!payload.pattern) {
    showError('denyError', new Error('Pattern is required'));
    return;
  }

  await apiFetch('/api/admin/deny-rules', { method: 'POST', body: JSON.stringify(payload) });
  document.getElementById('denyPattern').value = '';
  document.getElementById('denyReason').value = '';
  await loadDenyRules();
}

async function searchAudit() {
  clearError('auditError');
  const host = document.getElementById('auditTable');
  host.innerHTML = 'Loading…';

  const principal = document.getElementById('auditPrincipal').value.trim();
  const clientId = document.getElementById('auditClientId').value.trim();
  const result = document.getElementById('auditResult').value;

  const params = new URLSearchParams();
  if (principal) params.set('principal', principal);
  if (clientId) params.set('clientId', clientId);
  if (result) params.set('result', result);
  // Default: first page, most recent first
  params.set('page', '0');
  params.set('size', '25');

  try {
    const page = await apiFetch(`/api/audit/events/search?${params.toString()}`, { method: 'GET' });
    const content = page?.content || [];

    const rows = content.map(e => {
      return `
        <tr>
          <td>${escapeHtml(e.createdAt || '')}</td>
          <td>${escapeHtml(e.eventType || '')}</td>
          <td>${escapeHtml(e.result || '')}</td>
          <td>${escapeHtml(e.principal || '')}</td>
          <td><code>${escapeHtml(e.clientId || '')}</code></td>
          <td>${escapeHtml(e.message || '')}</td>
        </tr>
      `;
    }).join('');

    host.innerHTML = `
      <table class="table" style="width:100%; border-collapse: collapse;">
        <thead>
          <tr>
            <th>When</th>
            <th>Type</th>
            <th>Result</th>
            <th>Principal</th>
            <th>Client</th>
            <th>Message</th>
          </tr>
        </thead>
        <tbody>${rows || '<tr><td colspan="6">No events found.</td></tr>'}</tbody>
      </table>
    `;
  } catch (e) {
    host.innerHTML = '';
    showError('auditError', e);
  }
}

function wireEvents() {
  document.getElementById('saveClientBtn').addEventListener('click', async () => {
    try {
      await saveClient();
      resetClientForm();
    } catch (e) {
      showError('clientsError', e);
    }
  });

  document.getElementById('resetClientBtn').addEventListener('click', () => {
    resetClientForm();
    clearError('clientsError');
  });

  document.getElementById('saveScopeBtn').addEventListener('click', async () => {
    try {
      await saveScope();
    } catch (e) {
      showError('scopesError', e);
    }
  });

  document.getElementById('reloadScopesBtn').addEventListener('click', async () => {
    await loadScopes();
  });

  document.getElementById('createDenyRuleBtn').addEventListener('click', async () => {
    try {
      await createDenyRule();
    } catch (e) {
      showError('denyError', e);
    }
  });

  document.getElementById('reloadDenyRulesBtn').addEventListener('click', async () => {
    await loadDenyRules();
  });

  document.getElementById('searchAuditBtn').addEventListener('click', async () => {
    await searchAudit();
  });
}

async function init() {
  wireEvents();
  resetClientForm();

  await loadClients();
  await loadScopes();
  await loadDenyRules();
  await searchAudit();
}

window.addEventListener('DOMContentLoaded', () => {
  init().catch(e => {
    // Last-ditch, dump to console
    // eslint-disable-next-line no-console
    console.error(e);
  });
});
