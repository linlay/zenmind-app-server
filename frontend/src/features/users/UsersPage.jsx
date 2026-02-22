import { useEffect, useMemo, useState } from 'react';
import { request } from '../../shared/api/apiClient';
import { Button } from '../../shared/ui/Button';
import { DataTable } from '../../shared/ui/DataTable';
import { EmptyState } from '../../shared/ui/EmptyState';
import { LoadingOverlay } from '../../shared/ui/LoadingOverlay';
import { PageCard } from '../../shared/ui/PageCard';
import { toast } from '../../shared/ui/toast';

const initialForm = { username: '', password: '', displayName: '', status: 'ACTIVE' };

export function UsersPage() {
  const [users, setUsers] = useState([]);
  const [error, setError] = useState('');
  const [form, setForm] = useState(initialForm);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const loadUsers = async () => {
    setLoading(true);
    try {
      const data = await request('/admin/api/users');
      setUsers(Array.isArray(data) ? data : []);
      setError('');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load users';
      setError(message);
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const createUser = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');

    try {
      await request('/admin/api/users', {
        method: 'POST',
        body: JSON.stringify(form)
      });
      setForm(initialForm);
      toast.success('User created');
      await loadUsers();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to create user';
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const toggleStatus = async (user) => {
    const status = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await request(`/admin/api/users/${user.userId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status })
      });
      toast.success(`User ${status === 'ACTIVE' ? 'activated' : 'disabled'}`);
      await loadUsers();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to update status';
      setError(message);
      toast.error(message);
    }
  };

  const resetPassword = async (user) => {
    const password = window.prompt(`Reset password for ${user.username}`);
    if (!password) return;

    try {
      await request(`/admin/api/users/${user.userId}/password`, {
        method: 'POST',
        body: JSON.stringify({ password })
      });
      toast.success('Password reset completed');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to reset password';
      setError(message);
      toast.error(message);
    }
  };

  const columns = useMemo(() => [
    { key: 'userId', title: 'User ID', render: (user) => user.userId },
    { key: 'username', title: 'Username', render: (user) => user.username },
    { key: 'displayName', title: 'Display Name', render: (user) => user.displayName },
    { key: 'status', title: 'Status', render: (user) => user.status },
    {
      key: 'actions',
      title: 'Actions',
      render: (user) => (
        <div className="inline-actions">
          <Button variant="secondary" onClick={() => toggleStatus(user)}>
            {user.status === 'ACTIVE' ? 'Disable' : 'Activate'}
          </Button>
          <Button onClick={() => resetPassword(user)}>Reset Password</Button>
        </div>
      )
    }
  ], []);

  return (
    <>
      <PageCard title="Create User">
        {error ? <div className="error">{error}</div> : null}
        <form onSubmit={createUser}>
          <div className="row row-2">
            <div>
              <label>Username</label>
              <input
                value={form.username}
                onChange={(event) => setForm((prev) => ({ ...prev, username: event.target.value }))}
                required
              />
            </div>
            <div>
              <label>Password</label>
              <input
                type="password"
                value={form.password}
                onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
                required
              />
            </div>
          </div>

          <div className="row row-2">
            <div>
              <label>Display Name</label>
              <input
                value={form.displayName}
                onChange={(event) => setForm((prev) => ({ ...prev, displayName: event.target.value }))}
                required
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

          <Button type="submit" loading={submitting}>Create User</Button>
        </form>
      </PageCard>

      <PageCard title="Users" actions={<Button variant="ghost" onClick={loadUsers}>Refresh</Button>}>
        <LoadingOverlay show={loading} label="Loading users..." />
        <DataTable
          columns={columns}
          rows={users}
          rowKey={(user) => user.userId}
          empty={<EmptyState title="No users" description="Create your first user from the form above." />}
        />
      </PageCard>
    </>
  );
}
