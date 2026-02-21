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
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error((payload && payload.error) || `HTTP ${response.status}`);
  }

  return payload;
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
      </div>

      <div style={{ marginTop: 16 }}>
        <Routes>
          <Route path="/users" element={<UsersPage />} />
          <Route path="/clients" element={<ClientsPage />} />
          <Route path="/inbox" element={<InboxPage />} />
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
                <td>{message.createAt ? new Date(message.createAt).toLocaleString() : '-'}</td>
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
