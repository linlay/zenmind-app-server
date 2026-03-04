import { useEffect, useMemo, useState } from 'react';
import { request } from '../../shared/api/apiClient';
import { Button } from '../../shared/ui/Button';
import { DataTable } from '../../shared/ui/DataTable';
import { EmptyState } from '../../shared/ui/EmptyState';
import { LoadingOverlay } from '../../shared/ui/LoadingOverlay';
import { PageCard } from '../../shared/ui/PageCard';
import { toast } from '../../shared/ui/toast';

const initialForm = {
  clientId: '',
  clientName: '',
  clientSecret: '',
  grantTypes: 'authorization_code,refresh_token',
  redirectUris: 'myapp://oauthredirect',
  scopes: 'openid,profile',
  requirePkce: true,
  status: 'ACTIVE'
};

export function ClientsPage() {
  const [clients, setClients] = useState([]);
  const [error, setError] = useState('');
  const [newSecret, setNewSecret] = useState('');
  const [form, setForm] = useState(initialForm);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

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
    setLoading(true);
    try {
      const data = await request('/admin/api/clients');
      setClients(Array.isArray(data) ? data : []);
      setError('');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load clients';
      setError(message);
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadClients();
  }, []);

  const createClient = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');

    try {
      await request('/admin/api/clients', {
        method: 'POST',
        body: JSON.stringify(parsedPayload)
      });
      setForm((prev) => ({ ...prev, clientId: '', clientName: '', clientSecret: '' }));
      toast.success('Client created');
      await loadClients();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to create client';
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const toggleStatus = async (client) => {
    const status = client.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await request(`/admin/api/clients/${client.clientId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status })
      });
      toast.success(`Client ${status === 'ACTIVE' ? 'activated' : 'disabled'}`);
      await loadClients();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to update client status';
      setError(message);
      toast.error(message);
    }
  };

  const rotateSecret = async (client) => {
    try {
      const result = await request(`/admin/api/clients/${client.clientId}/secret/rotate`, {
        method: 'POST'
      });
      setNewSecret(`${result.clientId}: ${result.newClientSecret}`);
      toast.success('Client secret rotated');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to rotate secret';
      setError(message);
      toast.error(message);
    }
  };

  const columns = useMemo(() => [
    { key: 'clientId', title: 'Client ID', render: (client) => client.clientId },
    { key: 'name', title: 'Name', render: (client) => client.clientName },
    { key: 'grantTypes', title: 'Grant Types', render: (client) => client.grantTypes.join(', ') },
    { key: 'pkce', title: 'PKCE', render: (client) => (client.requirePkce ? 'YES' : 'NO') },
    { key: 'status', title: 'Status', render: (client) => client.status },
    {
      key: 'actions',
      title: 'Actions',
      render: (client) => (
        <div className="inline-actions">
          <Button variant="secondary" onClick={() => toggleStatus(client)}>
            {client.status === 'ACTIVE' ? 'Disable' : 'Activate'}
          </Button>
          <Button onClick={() => rotateSecret(client)}>Rotate Secret</Button>
        </div>
      )
    }
  ], []);

  return (
    <>
      <PageCard title="Create Client">
        {error ? <div className="error">{error}</div> : null}
        <form onSubmit={createClient}>
          <div className="row row-2">
            <div>
              <label>Client ID</label>
              <input
                value={form.clientId}
                onChange={(event) => setForm((prev) => ({ ...prev, clientId: event.target.value }))}
                required
              />
            </div>
            <div>
              <label>Client Name</label>
              <input
                value={form.clientName}
                onChange={(event) => setForm((prev) => ({ ...prev, clientName: event.target.value }))}
                required
              />
            </div>
          </div>

          <div className="row row-2">
            <div>
              <label>Client Secret (optional for public client)</label>
              <input
                value={form.clientSecret}
                onChange={(event) => setForm((prev) => ({ ...prev, clientSecret: event.target.value }))}
              />
            </div>
            <div>
              <label>Status</label>
              <select
                value={form.status}
                onChange={(event) => setForm((prev) => ({ ...prev, status: event.target.value }))}
              >
                <option value="ACTIVE">ACTIVE</option>
                <option value="DISABLED">DISABLED</option>
              </select>
            </div>
          </div>

          <label>Grant Types (comma separated)</label>
          <input
            value={form.grantTypes}
            onChange={(event) => setForm((prev) => ({ ...prev, grantTypes: event.target.value }))}
          />

          <label>Redirect URIs (comma separated)</label>
          <input
            value={form.redirectUris}
            onChange={(event) => setForm((prev) => ({ ...prev, redirectUris: event.target.value }))}
          />

          <label>Scopes (comma separated)</label>
          <input
            value={form.scopes}
            onChange={(event) => setForm((prev) => ({ ...prev, scopes: event.target.value }))}
          />

          <label className="inline-checkbox">
            <input
              type="checkbox"
              checked={form.requirePkce}
              onChange={(event) => setForm((prev) => ({ ...prev, requirePkce: event.target.checked }))}
            />
            Require PKCE
          </label>

          <Button type="submit" loading={submitting}>Create Client</Button>
        </form>
      </PageCard>

      {newSecret ? (
        <PageCard title="Rotated Client Secret">
          <pre className="mono-inline">{newSecret}</pre>
        </PageCard>
      ) : null}

      <PageCard title="Clients" actions={<Button variant="ghost" onClick={loadClients}>Refresh</Button>}>
        <LoadingOverlay show={loading} label="Loading clients..." />
        <DataTable
          columns={columns}
          rows={clients}
          rowKey={(client) => client.clientId}
          empty={<EmptyState title="No clients" description="Create your first OAuth client from the form above." />}
        />
      </PageCard>
    </>
  );
}
