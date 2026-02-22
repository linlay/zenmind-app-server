import { useEffect, useMemo, useState } from 'react';
import { request } from '../../shared/api/apiClient';
import { copyToClipboard } from '../../shared/utils/clipboard';
import { formatTime } from '../../shared/utils/time';
import { tokenPreview } from '../../shared/utils/token';
import { Badge } from '../../shared/ui/Badge';
import { Button } from '../../shared/ui/Button';
import { DataTable } from '../../shared/ui/DataTable';
import { EmptyState } from '../../shared/ui/EmptyState';
import { LoadingOverlay } from '../../shared/ui/LoadingOverlay';
import { PageCard } from '../../shared/ui/PageCard';
import { toast } from '../../shared/ui/toast';

const initialIssueForm = {
  masterPassword: 'password',
  deviceName: 'Admin Console Device',
  accessTtlSeconds: 600
};

const initialRefreshForm = {
  deviceToken: '',
  accessTtlSeconds: 600
};

const initialTokenFilter = {
  sources: 'APP_ACCESS,OAUTH_ACCESS,OAUTH_REFRESH',
  status: 'ALL',
  limit: 100
};

const DEVICE_PAGE_SIZE = 10;
const TOKEN_PAGE_SIZE = 20;

function toneByStatus(status) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'REVOKED') return 'danger';
  if (status === 'EXPIRED') return 'neutral';
  return 'neutral';
}

export function SecurityPage() {
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [jwks, setJwks] = useState(null);
  const [generatedPublicKey, setGeneratedPublicKey] = useState('');
  const [issueForm, setIssueForm] = useState(initialIssueForm);
  const [refreshForm, setRefreshForm] = useState(initialRefreshForm);

  const [issueResult, setIssueResult] = useState(null);
  const [refreshResult, setRefreshResult] = useState(null);
  const [devices, setDevices] = useState([]);
  const [tokens, setTokens] = useState([]);
  const [newDeviceAccess, setNewDeviceAccess] = useState(false);
  const [updatingNewDeviceAccess, setUpdatingNewDeviceAccess] = useState(false);
  const [tokenFilter, setTokenFilter] = useState(initialTokenFilter);
  const [devicePage, setDevicePage] = useState(1);
  const [tokenPage, setTokenPage] = useState(1);
  const [refreshingDevices, setRefreshingDevices] = useState(false);
  const [refreshingTokens, setRefreshingTokens] = useState(false);
  const [loading, setLoading] = useState(true);

  const copyText = async (text) => {
    if (await copyToClipboard(text)) {
      setSuccess('Copied to clipboard');
      toast.success('Copied to clipboard');
    } else {
      setError('Failed to copy to clipboard');
      toast.error('Failed to copy to clipboard');
    }
  };

  const loadJwks = async () => {
    const data = await request('/admin/api/security/jwks');
    setJwks(data);
  };

  const loadDevices = async ({ resetPage = false } = {}) => {
    const data = await request('/admin/api/security/app-devices');
    const rows = Array.isArray(data) ? data : [];
    setDevices(rows);
    setDevicePage((prev) => {
      if (resetPage) return 1;
      const totalPages = Math.max(1, Math.ceil(rows.length / DEVICE_PAGE_SIZE));
      return Math.min(prev, totalPages);
    });
  };

  const loadTokens = async (nextFilter = tokenFilter, { resetPage = false } = {}) => {
    const params = new URLSearchParams();
    params.set('sources', nextFilter.sources || initialTokenFilter.sources);
    params.set('status', nextFilter.status || initialTokenFilter.status);
    params.set('limit', String(nextFilter.limit || initialTokenFilter.limit));
    const data = await request(`/admin/api/security/tokens?${params.toString()}`);
    const rows = Array.isArray(data) ? data : [];
    setTokens(rows);
    setTokenPage((prev) => {
      if (resetPage) return 1;
      const totalPages = Math.max(1, Math.ceil(rows.length / TOKEN_PAGE_SIZE));
      return Math.min(prev, totalPages);
    });
  };

  const loadNewDeviceAccess = async () => {
    const data = await request('/admin/api/security/new-device-access');
    setNewDeviceAccess(Boolean(data?.allowNewDeviceLogin));
  };

  const loadAll = async () => {
    setLoading(true);
    setError('');
    try {
      await Promise.all([loadJwks(), loadDevices(), loadTokens(), loadNewDeviceAccess()]);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load security data';
      setError(message);
      toast.error(message);
    } finally {
      setLoading(false);
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
      const result = await request('/admin/api/security/app-tokens/issue', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setIssueResult(result);
      setRefreshForm((prev) => ({ ...prev, deviceToken: result.deviceToken || prev.deviceToken }));
      setSuccess('Issued app access token successfully');
      toast.success('Issued app access token successfully');
      await Promise.all([loadDevices({ resetPage: true }), loadTokens(tokenFilter, { resetPage: true })]);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to issue app token';
      setError(message);
      toast.error(message);
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
      const result = await request('/admin/api/security/app-tokens/refresh', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setRefreshResult(result);
      setRefreshForm((prev) => ({ ...prev, deviceToken: result.deviceToken || prev.deviceToken }));
      setSuccess('Refreshed app access token successfully');
      toast.success('Refreshed app access token successfully');
      await Promise.all([loadDevices({ resetPage: true }), loadTokens(tokenFilter, { resetPage: true })]);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to refresh app token';
      setError(message);
      toast.error(message);
    }
  };

  const revokeDevice = async (device) => {
    setError('');
    setSuccess('');

    try {
      await request(`/admin/api/security/app-devices/${device.deviceId}/revoke`, {
        method: 'POST'
      });
      setSuccess(`Device revoked: ${device.deviceName}`);
      toast.success(`Device revoked: ${device.deviceName}`);
      await Promise.all([loadDevices({ resetPage: true }), loadTokens(tokenFilter, { resetPage: true })]);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to revoke device';
      setError(message);
      toast.error(message);
    }
  };

  const applyTokenFilter = async (event) => {
    event.preventDefault();
    setError('');

    try {
      await loadTokens(tokenFilter, { resetPage: true });
      toast.success('Token filter applied');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to apply token filter';
      setError(message);
      toast.error(message);
    }
  };

  const refreshDevices = async () => {
    setError('');
    setRefreshingDevices(true);
    try {
      await loadDevices();
      toast.success('App devices refreshed');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to refresh app devices';
      setError(message);
      toast.error(message);
    } finally {
      setRefreshingDevices(false);
    }
  };

  const refreshTokenAudit = async () => {
    setError('');
    setRefreshingTokens(true);
    try {
      await loadTokens(tokenFilter);
      toast.success('Token audit refreshed');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to refresh token audit';
      setError(message);
      toast.error(message);
    } finally {
      setRefreshingTokens(false);
    }
  };

  const generatePublicKeyFromJwkSet = async () => {
    setError('');
    setSuccess('');

    try {
      const key = jwks?.jwks?.keys?.[0];
      if (!key?.e || !key?.n) {
        throw new Error('No JWK key found');
      }
      const result = await request('/admin/api/security/public-key/generate', {
        method: 'POST',
        body: JSON.stringify({ e: key.e, n: key.n })
      });
      setGeneratedPublicKey(result.publicKey || '');
      setSuccess('Generated public key successfully');
      toast.success('Generated public key successfully');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to generate public key';
      setError(message);
      toast.error(message);
    }
  };

  const setNewDeviceAccessStatus = async (nextValue) => {
    setError('');
    setSuccess('');
    setUpdatingNewDeviceAccess(true);

    try {
      const result = await request('/admin/api/security/new-device-access', {
        method: 'PUT',
        body: JSON.stringify({ allowNewDeviceLogin: nextValue })
      });
      const enabled = Boolean(result?.allowNewDeviceLogin);
      setNewDeviceAccess(enabled);
      setSuccess(`New device access ${enabled ? 'enabled' : 'disabled'}`);
      toast.success(`New device access ${enabled ? 'enabled' : 'disabled'}`);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to update new device access';
      setError(message);
      toast.error(message);
    } finally {
      setUpdatingNewDeviceAccess(false);
    }
  };

  const deviceColumns = useMemo(() => [
    { key: 'deviceId', title: 'Device ID', render: (device) => device.deviceId },
    { key: 'name', title: 'Name', render: (device) => device.deviceName },
    { key: 'status', title: 'Status', render: (device) => <Badge tone={device.status === 'ACTIVE' ? 'success' : 'danger'}>{device.status}</Badge> },
    { key: 'lastSeen', title: 'Last Seen', render: (device) => formatTime(device.lastSeenAt) },
    { key: 'created', title: 'Created', render: (device) => formatTime(device.createAt) },
    {
      key: 'actions',
      title: 'Actions',
      render: (device) => (
        device.status === 'ACTIVE'
          ? <Button variant="danger" onClick={() => revokeDevice(device)}>Revoke</Button>
          : <span>-</span>
      )
    }
  ], []);

  const tokenColumns = useMemo(() => [
    { key: 'source', title: 'Source', render: (item) => item.source },
    { key: 'status', title: 'Status', render: (item) => <Badge tone={toneByStatus(item.status)}>{item.status}</Badge> },
    { key: 'user', title: 'User', render: (item) => item.username || '-' },
    { key: 'device', title: 'Device', render: (item) => (item.deviceName ? `${item.deviceName} (${item.deviceId})` : '-') },
    { key: 'client', title: 'Client', render: (item) => item.clientId || '-' },
    { key: 'issued', title: 'Issued', render: (item) => formatTime(item.issuedAt) },
    { key: 'expires', title: 'Expires', render: (item) => formatTime(item.expiresAt) },
    { key: 'token', title: 'Token', render: (item) => <div className="token-cell">{tokenPreview(item.token)}</div> },
    { key: 'actions', title: 'Actions', render: (item) => <Button variant="secondary" onClick={() => copyText(item.token)}>Copy</Button> }
  ], [jwks]);

  const deviceTotalPages = Math.max(1, Math.ceil(devices.length / DEVICE_PAGE_SIZE));
  const tokenTotalPages = Math.max(1, Math.ceil(tokens.length / TOKEN_PAGE_SIZE));
  const currentDevicePage = Math.min(devicePage, deviceTotalPages);
  const currentTokenPage = Math.min(tokenPage, tokenTotalPages);
  const pagedDevices = devices.slice(
    (currentDevicePage - 1) * DEVICE_PAGE_SIZE,
    currentDevicePage * DEVICE_PAGE_SIZE
  );
  const pagedTokens = tokens.slice(
    (currentTokenPage - 1) * TOKEN_PAGE_SIZE,
    currentTokenPage * TOKEN_PAGE_SIZE
  );

  return (
    <>
      <PageCard title="Security Overview" actions={<Button variant="ghost" onClick={loadAll}>Refresh All</Button>}>
        <LoadingOverlay show={loading} label="Loading security data..." />
        {error ? <div className="error">{error}</div> : null}
        {success ? <div className="success">{success}</div> : null}
      </PageCard>

      <PageCard
        title="New Device Access"
        actions={<Badge tone={newDeviceAccess ? 'success' : 'danger'}>{newDeviceAccess ? 'OPEN' : 'CLOSED'}</Badge>}
      >
        <p className="muted">
          When closed, new devices cannot use <code>/api/auth/login</code> for first-time onboarding.
        </p>
        <Button
          onClick={() => setNewDeviceAccessStatus(!newDeviceAccess)}
          loading={updatingNewDeviceAccess}
        >
          {newDeviceAccess ? 'Disable New Device Access' : 'Enable New Device Access'}
        </Button>
      </PageCard>

      <PageCard title="JWKs">
        <pre className="json-block">{jwks ? JSON.stringify(jwks.jwks || {}, null, 2) : 'Loading...'}</pre>
        <div className="inline-actions spacing-top">
          <Button
            variant="secondary"
            onClick={generatePublicKeyFromJwkSet}
            disabled={!jwks?.jwks?.keys?.[0]?.e || !jwks?.jwks?.keys?.[0]?.n}
          >
            Generate Public Key
          </Button>
          {generatedPublicKey ? (
            <Button variant="ghost" onClick={() => copyText(generatedPublicKey)}>Copy Public Key</Button>
          ) : null}
        </div>
        {generatedPublicKey ? <pre className="pem-block spacing-top">{generatedPublicKey}</pre> : null}
      </PageCard>

      <PageCard title="Issue App Access Token">
        <form onSubmit={issueAppToken}>
          <div className="row row-2">
            <div>
              <label>Master Password</label>
              <input
                type="password"
                value={issueForm.masterPassword}
                onChange={(event) => setIssueForm((prev) => ({ ...prev, masterPassword: event.target.value }))}
                required
              />
            </div>
            <div>
              <label>Device Name</label>
              <input
                value={issueForm.deviceName}
                onChange={(event) => setIssueForm((prev) => ({ ...prev, deviceName: event.target.value }))}
                required
              />
            </div>
          </div>

          <div className="row row-2">
            <div>
              <label>Access TTL Seconds</label>
              <input
                type="number"
                min="1"
                value={issueForm.accessTtlSeconds}
                onChange={(event) => setIssueForm((prev) => ({ ...prev, accessTtlSeconds: event.target.value }))}
              />
            </div>
            <div className="align-end">
              <Button type="submit">Issue Token</Button>
            </div>
          </div>
        </form>

        {issueResult ? (
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
                    <Button variant="secondary" onClick={() => copyText(issueResult.accessToken)}>Copy Access Token</Button>
                  </td>
                </tr>
                <tr>
                  <th>Device Token</th>
                  <td>
                    <div className="token-cell">{issueResult.deviceToken}</div>
                    <Button variant="secondary" onClick={() => copyText(issueResult.deviceToken)}>Copy Device Token</Button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        ) : null}
      </PageCard>

      <PageCard title="Refresh App Access Token">
        <form onSubmit={refreshAppToken}>
          <div className="row row-2">
            <div>
              <label>Device Token</label>
              <input
                value={refreshForm.deviceToken}
                onChange={(event) => setRefreshForm((prev) => ({ ...prev, deviceToken: event.target.value }))}
                required
              />
            </div>
            <div>
              <label>Access TTL Seconds</label>
              <input
                type="number"
                min="1"
                value={refreshForm.accessTtlSeconds}
                onChange={(event) => setRefreshForm((prev) => ({ ...prev, accessTtlSeconds: event.target.value }))}
              />
            </div>
          </div>
          <Button type="submit">Refresh Token</Button>
        </form>

        {refreshResult ? (
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
                    <Button variant="secondary" onClick={() => copyText(refreshResult.accessToken)}>Copy Access Token</Button>
                  </td>
                </tr>
                <tr>
                  <th>Device Token</th>
                  <td>
                    <div className="token-cell">{refreshResult.deviceToken}</div>
                    <Button variant="secondary" onClick={() => copyText(refreshResult.deviceToken)}>Copy Device Token</Button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        ) : null}
      </PageCard>

      <PageCard title="App Devices" actions={<Button variant="ghost" onClick={refreshDevices} loading={refreshingDevices}>Refresh</Button>}>
        <DataTable
          columns={deviceColumns}
          rows={pagedDevices}
          rowKey={(device) => device.deviceId}
          empty={<EmptyState title="No devices" description="Issue an app token to create a device." />}
        />
        {devices.length ? (
          <div className="table-pagination">
            <small className="muted">
              Showing {(currentDevicePage - 1) * DEVICE_PAGE_SIZE + 1}-{Math.min(currentDevicePage * DEVICE_PAGE_SIZE, devices.length)} of {devices.length}
            </small>
            <div className="inline-actions">
              <Button
                variant="ghost"
                onClick={() => setDevicePage((prev) => Math.max(1, prev - 1))}
                disabled={currentDevicePage <= 1}
              >
                Prev
              </Button>
              <span className="table-pagination-info">Page {currentDevicePage} / {deviceTotalPages}</span>
              <Button
                variant="ghost"
                onClick={() => setDevicePage((prev) => Math.min(deviceTotalPages, prev + 1))}
                disabled={currentDevicePage >= deviceTotalPages}
              >
                Next
              </Button>
            </div>
          </div>
        ) : null}
      </PageCard>

      <PageCard title="Token Audit" actions={<Button variant="ghost" onClick={refreshTokenAudit} loading={refreshingTokens}>Refresh</Button>}>
        <form onSubmit={applyTokenFilter}>
          <div className="row row-4">
            <div>
              <label>Sources (comma separated)</label>
              <input
                value={tokenFilter.sources}
                onChange={(event) => setTokenFilter((prev) => ({ ...prev, sources: event.target.value }))}
              />
            </div>
            <div>
              <label>Status</label>
              <select
                value={tokenFilter.status}
                onChange={(event) => setTokenFilter((prev) => ({ ...prev, status: event.target.value }))}
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
                onChange={(event) => setTokenFilter((prev) => ({ ...prev, limit: event.target.value }))}
              />
            </div>
            <div className="filter-action-cell">
              <label className="label-spacer" aria-hidden="true">Action</label>
              <Button type="submit">Apply Filter</Button>
            </div>
          </div>
        </form>

        <DataTable
          columns={tokenColumns}
          rows={pagedTokens}
          rowKey={(item) => item.tokenId}
          empty={<EmptyState title="No tokens" description="No token records for current filter." />}
        />
        {tokens.length ? (
          <div className="table-pagination">
            <small className="muted">
              Showing {(currentTokenPage - 1) * TOKEN_PAGE_SIZE + 1}-{Math.min(currentTokenPage * TOKEN_PAGE_SIZE, tokens.length)} of {tokens.length}
            </small>
            <div className="inline-actions">
              <Button
                variant="ghost"
                onClick={() => setTokenPage((prev) => Math.max(1, prev - 1))}
                disabled={currentTokenPage <= 1}
              >
                Prev
              </Button>
              <span className="table-pagination-info">Page {currentTokenPage} / {tokenTotalPages}</span>
              <Button
                variant="ghost"
                onClick={() => setTokenPage((prev) => Math.min(tokenTotalPages, prev + 1))}
                disabled={currentTokenPage >= tokenTotalPages}
              >
                Next
              </Button>
            </div>
          </div>
        ) : null}
      </PageCard>
    </>
  );
}
