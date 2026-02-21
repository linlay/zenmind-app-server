import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'include'
  });

  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = { error: text };
    }
  }

  if (!response.ok) {
    throw new Error((payload && payload.error) || `HTTP ${response.status}`);
  }

  return payload;
}

function formatTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString();
}

function useAuthState() {
  const [loading, setLoading] = useState(true);
  const [session, setSession] = useState(null);

  const refresh = async () => {
    try {
      const me = await api('/admin/api/session/me');
      setSession(me);
    } catch {
      setSession(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  return {
    loading,
    session,
    setSession,
    refresh
  };
}

function LoginPage({ onLogin }) {
  const navigate = useNavigate();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('password');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const session = await api('/admin/api/session/login', {
        method: 'POST',
        body: JSON.stringify({ username, password })
      });
      onLogin(session);
      navigate('/users');
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="app-shell">
      <div className="card" style={{ maxWidth: 420, margin: '80px auto' }}>
        <h2>Admin Login</h2>
        {error && <div className="error">{error}</div>}
        <form onSubmit={submit}>
          <label>Username</label>
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
          <label>Password</label>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <button disabled={submitting} type="submit">{submitting ? 'Signing in...' : 'Sign In'}</button>
        </form>
      </div>
    </div>
  );
}

function ProtectedLayout({ session, onLogout }) {
  const location = useLocation();

  return (
    <div className="app-shell">
      <div className="topbar">
        <div>
          <h2 style={{ margin: 0 }}>Auth Admin</h2>
          <small>Signed in as {session.username}</small>
        </div>
        <div className="inline-actions">
          <button className="secondary" onClick={onLogout}>Logout</button>
        </div>
      </div>

      <div className="tabs">
        <Link to="/users" style={{ opacity: location.pathname.includes('/users') ? 1 : 0.65 }}>Users</Link>
        <Link to="/clients" style={{ opacity: location.pathname.includes('/clients') ? 1 : 0.65 }}>Clients</Link>
        <Link to="/inbox" style={{ opacity: location.pathname.includes('/inbox') ? 1 : 0.65 }}>Inbox</Link>
        <Link to="/security" style={{ opacity: location.pathname.includes('/security') ? 1 : 0.65 }}>Security</Link>
      </div>

      <div style={{ marginTop: 16 }}>
        <Routes>
          <Route path="/users" element={<UsersPage />} />
          <Route path="/clients" element={<ClientsPage />} />
          <Route path="/inbox" element={<InboxPage />} />
          <Route path="/security" element={<SecurityPage />} />
          <Route path="*" element={<Navigate to="/users" replace />} />
        </Routes>
      </div>
    </div>
  );
}

function UsersPage() {
  const [users, setUsers] = useState([]);
  const [error, setError] = useState('');
  const [form, setForm] = useState({ username: '', password: '', displayName: '', status: 'ACTIVE' });

  const loadUsers = async () => {
    try {
      const data = await api('/admin/api/users');
      setUsers(data);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const createUser = async (event) => {
    event.preventDefault();
    setError('');
    try {
      await api('/admin/api/users', {
        method: 'POST',
        body: JSON.stringify(form)
      });
      setForm({ username: '', password: '', displayName: '', status: 'ACTIVE' });
      await loadUsers();
    } catch (err) {
      setError(err.message);
    }
  };

  const toggleStatus = async (user) => {
    const status = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await api(`/admin/api/users/${user.userId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status })
      });
      await loadUsers();
    } catch (err) {
      setError(err.message);
    }
  };

  const resetPassword = async (user) => {
    const password = window.prompt(`Reset password for ${user.username}`);
    if (!password) return;
    try {
      await api(`/admin/api/users/${user.userId}/password`, {
        method: 'POST',
        body: JSON.stringify({ password })
      });
      window.alert('Password reset completed');
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <>
      <div className="card">
        <h3>Create User</h3>
        {error && <div className="error">{error}</div>}
        <form onSubmit={createUser}>
          <div className="row">
            <div>
              <label>Username</label>
              <input value={form.username} onChange={(e) => setForm((v) => ({ ...v, username: e.target.value }))} required />
            </div>
            <div>
              <label>Password</label>
              <input type="password" value={form.password} onChange={(e) => setForm((v) => ({ ...v, password: e.target.value }))} required />
            </div>
          </div>
          <div className="row">
            <div>
              <label>Display Name</label>
              <input value={form.displayName} onChange={(e) => setForm((v) => ({ ...v, displayName: e.target.value }))} required />
            </div>
            <div>
              <label>Status</label>
              <select value={form.status} onChange={(e) => setForm((v) => ({ ...v, status: e.target.value }))}>
                <option value="ACTIVE">ACTIVE</option>
                <option value="DISABLED">DISABLED</option>
              </select>
            </div>
          </div>
          <button type="submit">Create User</button>
        </form>
      </div>

      <div className="card">
        <h3>Users</h3>
        <table className="table">
          <thead>
            <tr>
              <th>User ID</th>
              <th>Username</th>
              <th>Display Name</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.userId}>
                <td>{user.userId}</td>
                <td>{user.username}</td>
                <td>{user.displayName}</td>
                <td>{user.status}</td>
                <td>
                  <div className="inline-actions">
                    <button className="secondary" onClick={() => toggleStatus(user)}>
                      {user.status === 'ACTIVE' ? 'Disable' : 'Activate'}
                    </button>
                    <button onClick={() => resetPassword(user)}>Reset Password</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function ClientsPage() {
  const [clients, setClients] = useState([]);
  const [error, setError] = useState('');
  const [newSecret, setNewSecret] = useState('');
  const [form, setForm] = useState({
    clientId: '',
    clientName: '',
    clientSecret: '',
    grantTypes: 'authorization_code,refresh_token',
    redirectUris: 'myapp://oauthredirect',
    scopes: 'openid,profile',
    requirePkce: true,
    status: 'ACTIVE'
  });

  const parsedPayload = useMemo(() => ({
    clientId: form.clientId,
    clientName: form.clientName,
    clientSecret: form.clientSecret || null,
    grantTypes: form.grantTypes.split(',').map((v) => v.trim()).filter(Boolean),
    redirectUris: form.redirectUris.split(',').map((v) => v.trim()).filter(Boolean),
    scopes: form.scopes.split(',').map((v) => v.trim()).filter(Boolean),
    requirePkce: Boolean(form.requirePkce),
    status: form.status
  }), [form]);

  const loadClients = async () => {
    try {
      const data = await api('/admin/api/clients');
      setClients(data);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    loadClients();
  }, []);

  const createClient = async (event) => {
    event.preventDefault();
    setError('');
    try {
      await api('/admin/api/clients', {
        method: 'POST',
        body: JSON.stringify(parsedPayload)
      });
      setForm((prev) => ({ ...prev, clientId: '', clientName: '', clientSecret: '' }));
      await loadClients();
    } catch (err) {
      setError(err.message);
    }
  };

  const toggleStatus = async (client) => {
    const status = client.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await api(`/admin/api/clients/${client.clientId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status })
      });
      await loadClients();
    } catch (err) {
      setError(err.message);
    }
  };

  const rotateSecret = async (client) => {
    try {
      const result = await api(`/admin/api/clients/${client.clientId}/secret/rotate`, {
        method: 'POST'
      });
      setNewSecret(`${result.clientId}: ${result.newClientSecret}`);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <>
      <div className="card">
        <h3>Create Client</h3>
        {error && <div className="error">{error}</div>}
        <form onSubmit={createClient}>
          <div className="row">
            <div>
              <label>Client ID</label>
              <input value={form.clientId} onChange={(e) => setForm((v) => ({ ...v, clientId: e.target.value }))} required />
            </div>
            <div>
              <label>Client Name</label>
              <input value={form.clientName} onChange={(e) => setForm((v) => ({ ...v, clientName: e.target.value }))} required />
            </div>
          </div>
          <div className="row">
            <div>
              <label>Client Secret (optional for public client)</label>
              <input value={form.clientSecret} onChange={(e) => setForm((v) => ({ ...v, clientSecret: e.target.value }))} />
            </div>
            <div>
              <label>Status</label>
              <select value={form.status} onChange={(e) => setForm((v) => ({ ...v, status: e.target.value }))}>
                <option value="ACTIVE">ACTIVE</option>
                <option value="DISABLED">DISABLED</option>
              </select>
            </div>
          </div>
          <label>Grant Types (comma separated)</label>
          <input value={form.grantTypes} onChange={(e) => setForm((v) => ({ ...v, grantTypes: e.target.value }))} />

          <label>Redirect URIs (comma separated)</label>
          <input value={form.redirectUris} onChange={(e) => setForm((v) => ({ ...v, redirectUris: e.target.value }))} />

          <label>Scopes (comma separated)</label>
          <input value={form.scopes} onChange={(e) => setForm((v) => ({ ...v, scopes: e.target.value }))} />

          <label>
            <input
              type="checkbox"
              checked={form.requirePkce}
              onChange={(e) => setForm((v) => ({ ...v, requirePkce: e.target.checked }))}
              style={{ width: 'auto', marginRight: 8 }}
            />
            Require PKCE
          </label>

          <button type="submit">Create Client</button>
        </form>
      </div>

      {newSecret && (
        <div className="card">
          <h3>Rotated Client Secret</h3>
          <p>{newSecret}</p>
        </div>
      )}

      <div className="card">
        <h3>Clients</h3>
        <table className="table">
          <thead>
            <tr>
              <th>Client ID</th>
              <th>Name</th>
              <th>Grant Types</th>
              <th>PKCE</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {clients.map((client) => (
              <tr key={client.clientId}>
                <td>{client.clientId}</td>
                <td>{client.clientName}</td>
                <td>{client.grantTypes.join(', ')}</td>
                <td>{client.requirePkce ? 'YES' : 'NO'}</td>
                <td>{client.status}</td>
                <td>
                  <div className="inline-actions">
                    <button className="secondary" onClick={() => toggleStatus(client)}>
                      {client.status === 'ACTIVE' ? 'Disable' : 'Activate'}
                    </button>
                    <button onClick={() => rotateSecret(client)}>Rotate Secret</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function InboxPage() {
  const [messages, setMessages] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [error, setError] = useState('');
  const [form, setForm] = useState({
    title: '',
    content: '',
    type: 'INFO'
  });

  const loadInbox = async () => {
    try {
      const [list, counter] = await Promise.all([
        api('/admin/api/inbox?limit=100'),
        api('/admin/api/inbox/unread-count')
      ]);
      setMessages(Array.isArray(list) ? list : []);
      setUnreadCount(Number(counter?.unreadCount || 0));
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    loadInbox();
  }, []);

  const sendMessage = async (event) => {
    event.preventDefault();
    setError('');
    try {
      await api('/admin/api/inbox/send', {
        method: 'POST',
        body: JSON.stringify(form)
      });
      setForm({ title: '', content: '', type: 'INFO' });
      await loadInbox();
    } catch (err) {
      setError(err.message);
    }
  };

  const markRead = async (messageId) => {
    try {
      await api('/admin/api/inbox/read', {
        method: 'POST',
        body: JSON.stringify({ messageIds: [messageId] })
      });
      await loadInbox();
    } catch (err) {
      setError(err.message);
    }
  };

  const markAllRead = async () => {
    try {
      await api('/admin/api/inbox/read-all', {
        method: 'POST'
      });
      await loadInbox();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <>
      <div className="card">
        <h3>Send Inbox Message</h3>
        {error && <div className="error">{error}</div>}
        <form onSubmit={sendMessage}>
          <label>Title</label>
          <input
            value={form.title}
            onChange={(e) => setForm((prev) => ({ ...prev, title: e.target.value }))}
            required
          />

          <label>Content</label>
          <textarea
            value={form.content}
            onChange={(e) => setForm((prev) => ({ ...prev, content: e.target.value }))}
            rows={4}
            required
          />

          <label>Type</label>
          <select value={form.type} onChange={(e) => setForm((prev) => ({ ...prev, type: e.target.value }))}>
            <option value="INFO">INFO</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
            <option value="SYSTEM">SYSTEM</option>
          </select>

          <button type="submit">Send to Inbox</button>
        </form>
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <h3 style={{ margin: 0 }}>Inbox Messages</h3>
          <div className="inline-actions">
            <span>Unread: {unreadCount}</span>
            <button className="secondary" onClick={markAllRead}>Mark All Read</button>
          </div>
        </div>
        <table className="table">
          <thead>
            <tr>
              <th>Title</th>
              <th>Type</th>
              <th>Content</th>
              <th>Read</th>
              <th>Created At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {messages.map((message) => (
              <tr key={message.messageId}>
                <td>{message.title}</td>
                <td>{message.type}</td>
                <td style={{ maxWidth: 360 }}>{message.content}</td>
                <td>{message.read ? 'YES' : 'NO'}</td>
                <td>{formatTime(message.createAt)}</td>
                <td>
                  <div className="inline-actions">
                    {!message.read ? (
                      <button className="secondary" onClick={() => markRead(message.messageId)}>Mark Read</button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function SecurityPage() {
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [jwks, setJwks] = useState(null);
  const [issueForm, setIssueForm] = useState({
    masterPassword: 'password',
    deviceName: 'Admin Console Device',
    accessTtlSeconds: 600
  });
  const [refreshForm, setRefreshForm] = useState({
    deviceToken: '',
    accessTtlSeconds: 600
  });

  const [issueResult, setIssueResult] = useState(null);
  const [refreshResult, setRefreshResult] = useState(null);
  const [devices, setDevices] = useState([]);
  const [tokens, setTokens] = useState([]);
  const [tokenFilter, setTokenFilter] = useState({
    sources: 'APP_ACCESS,OAUTH_ACCESS,OAUTH_REFRESH',
    status: 'ALL',
    limit: 100
  });

  const copyText = async (text) => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      setSuccess('Copied to clipboard');
    } catch {
      setError('Failed to copy to clipboard');
    }
  };

  const loadJwks = async () => {
    const data = await api('/admin/api/security/jwks');
    setJwks(data);
  };

  const loadDevices = async () => {
    const data = await api('/admin/api/security/app-devices');
    setDevices(Array.isArray(data) ? data : []);
  };

  const loadTokens = async (nextFilter = tokenFilter) => {
    const params = new URLSearchParams();
    params.set('sources', nextFilter.sources || 'APP_ACCESS,OAUTH_ACCESS,OAUTH_REFRESH');
    params.set('status', nextFilter.status || 'ALL');
    params.set('limit', String(nextFilter.limit || 100));
    const data = await api(`/admin/api/security/tokens?${params.toString()}`);
    setTokens(Array.isArray(data) ? data : []);
  };

  const loadAll = async () => {
    setError('');
    try {
      await Promise.all([loadJwks(), loadDevices(), loadTokens()]);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    loadAll();
  }, []);

  const issueAppToken = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    try {
      const payload = {
        masterPassword: issueForm.masterPassword,
        deviceName: issueForm.deviceName,
        accessTtlSeconds: issueForm.accessTtlSeconds ? Number(issueForm.accessTtlSeconds) : null
      };
      const result = await api('/admin/api/security/app-tokens/issue', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setIssueResult(result);
      setRefreshForm((prev) => ({ ...prev, deviceToken: result.deviceToken || prev.deviceToken }));
      setSuccess('Issued app access token successfully');
      await Promise.all([loadDevices(), loadTokens()]);
    } catch (err) {
      setError(err.message);
    }
  };

  const refreshAppToken = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    try {
      const payload = {
        deviceToken: refreshForm.deviceToken,
        accessTtlSeconds: refreshForm.accessTtlSeconds ? Number(refreshForm.accessTtlSeconds) : null
      };
      const result = await api('/admin/api/security/app-tokens/refresh', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setRefreshResult(result);
      setRefreshForm((prev) => ({ ...prev, deviceToken: result.deviceToken || prev.deviceToken }));
      setSuccess('Refreshed app access token successfully');
      await Promise.all([loadDevices(), loadTokens()]);
    } catch (err) {
      setError(err.message);
    }
  };

  const revokeDevice = async (device) => {
    setError('');
    setSuccess('');
    try {
      await api(`/admin/api/security/app-devices/${device.deviceId}/revoke`, {
        method: 'POST'
      });
      setSuccess(`Device revoked: ${device.deviceName}`);
      await Promise.all([loadDevices(), loadTokens()]);
    } catch (err) {
      setError(err.message);
    }
  };

  const applyTokenFilter = async (event) => {
    event.preventDefault();
    setError('');
    try {
      await loadTokens(tokenFilter);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <>
      <div className="card">
        <div className="section-header">
          <h3 style={{ margin: 0 }}>Security Overview</h3>
          <button className="secondary" onClick={loadAll}>Refresh All</button>
        </div>
        {error && <div className="error">{error}</div>}
        {success && <div className="success">{success}</div>}
      </div>

      <div className="card">
        <h3>JWKs</h3>
        <div className="row">
          <div>
            <h4>App JWK Set</h4>
            <pre className="json-block">{jwks ? JSON.stringify(jwks.appJwks, null, 2) : 'Loading...'}</pre>
          </div>
          <div>
            <h4>OIDC JWK Set</h4>
            <pre className="json-block">{jwks ? JSON.stringify(jwks.oidcJwks, null, 2) : 'Loading...'}</pre>
          </div>
        </div>
      </div>

      <div className="card">
        <h3>Issue App Access Token</h3>
        <form onSubmit={issueAppToken}>
          <div className="row">
            <div>
              <label>Master Password</label>
              <input
                type="password"
                value={issueForm.masterPassword}
                onChange={(e) => setIssueForm((v) => ({ ...v, masterPassword: e.target.value }))}
                required
              />
            </div>
            <div>
              <label>Device Name</label>
              <input
                value={issueForm.deviceName}
                onChange={(e) => setIssueForm((v) => ({ ...v, deviceName: e.target.value }))}
                required
              />
            </div>
          </div>
          <div className="row">
            <div>
              <label>Access TTL Seconds</label>
              <input
                type="number"
                min="1"
                value={issueForm.accessTtlSeconds}
                onChange={(e) => setIssueForm((v) => ({ ...v, accessTtlSeconds: e.target.value }))}
              />
            </div>
            <div style={{ display: 'flex', alignItems: 'end' }}>
              <button type="submit">Issue Token</button>
            </div>
          </div>
        </form>

        {issueResult && (
          <div className="card inner-card">
            <h4>Issue Result</h4>
            <table className="table compact">
              <tbody>
                <tr><th>Username</th><td>{issueResult.username}</td></tr>
                <tr><th>Device ID</th><td>{issueResult.deviceId}</td></tr>
                <tr><th>Device Name</th><td>{issueResult.deviceName}</td></tr>
                <tr><th>Expire At</th><td>{formatTime(issueResult.accessTokenExpireAt)}</td></tr>
                <tr>
                  <th>Access Token</th>
                  <td>
                    <div className="token-cell">{issueResult.accessToken}</div>
                    <button className="secondary" onClick={() => copyText(issueResult.accessToken)}>Copy Access Token</button>
                  </td>
                </tr>
                <tr>
                  <th>Device Token</th>
                  <td>
                    <div className="token-cell">{issueResult.deviceToken}</div>
                    <button className="secondary" onClick={() => copyText(issueResult.deviceToken)}>Copy Device Token</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card">
        <h3>Refresh App Access Token</h3>
        <form onSubmit={refreshAppToken}>
          <div className="row">
            <div>
              <label>Device Token</label>
              <input
                value={refreshForm.deviceToken}
                onChange={(e) => setRefreshForm((v) => ({ ...v, deviceToken: e.target.value }))}
                required
              />
            </div>
            <div>
              <label>Access TTL Seconds</label>
              <input
                type="number"
                min="1"
                value={refreshForm.accessTtlSeconds}
                onChange={(e) => setRefreshForm((v) => ({ ...v, accessTtlSeconds: e.target.value }))}
              />
            </div>
          </div>
          <button type="submit">Refresh Token</button>
        </form>

        {refreshResult && (
          <div className="card inner-card">
            <h4>Refresh Result</h4>
            <table className="table compact">
              <tbody>
                <tr><th>Device ID</th><td>{refreshResult.deviceId}</td></tr>
                <tr><th>Expire At</th><td>{formatTime(refreshResult.accessTokenExpireAt)}</td></tr>
                <tr>
                  <th>Access Token</th>
                  <td>
                    <div className="token-cell">{refreshResult.accessToken}</div>
                    <button className="secondary" onClick={() => copyText(refreshResult.accessToken)}>Copy Access Token</button>
                  </td>
                </tr>
                <tr>
                  <th>Device Token</th>
                  <td>
                    <div className="token-cell">{refreshResult.deviceToken}</div>
                    <button className="secondary" onClick={() => copyText(refreshResult.deviceToken)}>Copy Device Token</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card">
        <h3>App Devices</h3>
        <table className="table">
          <thead>
            <tr>
              <th>Device ID</th>
              <th>Name</th>
              <th>Status</th>
              <th>Last Seen</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {devices.map((device) => (
              <tr key={device.deviceId}>
                <td>{device.deviceId}</td>
                <td>{device.deviceName}</td>
                <td>
                  <span className={`badge ${device.status === 'ACTIVE' ? 'badge-green' : 'badge-red'}`}>
                    {device.status}
                  </span>
                </td>
                <td>{formatTime(device.lastSeenAt)}</td>
                <td>{formatTime(device.createAt)}</td>
                <td>
                  {device.status === 'ACTIVE' ? (
                    <button className="danger" onClick={() => revokeDevice(device)}>Revoke</button>
                  ) : (
                    <span>-</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h3>Token Audit</h3>
        <form onSubmit={applyTokenFilter}>
          <div className="filter-row">
            <div>
              <label>Sources (comma separated)</label>
              <input
                value={tokenFilter.sources}
                onChange={(e) => setTokenFilter((v) => ({ ...v, sources: e.target.value }))}
              />
            </div>
            <div>
              <label>Status</label>
              <select
                value={tokenFilter.status}
                onChange={(e) => setTokenFilter((v) => ({ ...v, status: e.target.value }))}
              >
                <option value="ALL">ALL</option>
                <option value="ACTIVE">ACTIVE</option>
                <option value="EXPIRED">EXPIRED</option>
                <option value="REVOKED">REVOKED</option>
              </select>
            </div>
            <div>
              <label>Limit (max 200)</label>
              <input
                type="number"
                min="1"
                max="200"
                value={tokenFilter.limit}
                onChange={(e) => setTokenFilter((v) => ({ ...v, limit: e.target.value }))}
              />
            </div>
            <div style={{ display: 'flex', alignItems: 'end' }}>
              <button type="submit">Apply Filter</button>
            </div>
          </div>
        </form>

        <table className="table">
          <thead>
            <tr>
              <th>Source</th>
              <th>Status</th>
              <th>User</th>
              <th>Device</th>
              <th>Client</th>
              <th>Issued</th>
              <th>Expires</th>
              <th>Token</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {tokens.map((item) => (
              <tr key={item.tokenId}>
                <td>{item.source}</td>
                <td>
                  <span className={`badge ${item.status === 'ACTIVE' ? 'badge-green' : item.status === 'REVOKED' ? 'badge-red' : 'badge-gray'}`}>
                    {item.status}
                  </span>
                </td>
                <td>{item.username || '-'}</td>
                <td>{item.deviceName ? `${item.deviceName} (${item.deviceId})` : '-'}</td>
                <td>{item.clientId || '-'}</td>
                <td>{formatTime(item.issuedAt)}</td>
                <td>{formatTime(item.expiresAt)}</td>
                <td><div className="token-cell">{item.token}</div></td>
                <td>
                  <button className="secondary" onClick={() => copyText(item.token)}>Copy</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export default function App() {
  const navigate = useNavigate();
  const { loading, session, setSession } = useAuthState();

  const logout = async () => {
    await api('/admin/api/session/logout', { method: 'POST' });
    setSession(null);
    navigate('/login');
  };

  if (loading) {
    return <div className="app-shell">Loading...</div>;
  }

  return (
    <Routes>
      <Route path="/login" element={session ? <Navigate to="/users" replace /> : <LoginPage onLogin={setSession} />} />
      <Route
        path="/*"
        element={session ? <ProtectedLayout session={session} onLogout={logout} /> : <Navigate to="/login" replace />}
      />
    </Routes>
  );
}
