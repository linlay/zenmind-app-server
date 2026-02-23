import { useEffect, useState } from 'react';
import { request } from '../../shared/api/apiClient';
import { copyToClipboard } from '../../shared/utils/clipboard';
import { formatTime } from '../../shared/utils/time';
import { Badge } from '../../shared/ui/Badge';
import { Button } from '../../shared/ui/Button';
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

export function SecurityPage() {
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [jwks, setJwks] = useState(null);
  const [generatedPublicKey, setGeneratedPublicKey] = useState('');
  const [issueForm, setIssueForm] = useState(initialIssueForm);
  const [refreshForm, setRefreshForm] = useState(initialRefreshForm);

  const [issueResult, setIssueResult] = useState(null);
  const [refreshResult, setRefreshResult] = useState(null);
  const [newDeviceAccess, setNewDeviceAccess] = useState(false);
  const [updatingNewDeviceAccess, setUpdatingNewDeviceAccess] = useState(false);
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

  const loadNewDeviceAccess = async () => {
    const data = await request('/admin/api/security/new-device-access');
    setNewDeviceAccess(Boolean(data?.allowNewDeviceLogin));
  };

  const loadAll = async () => {
    setLoading(true);
    setError('');
    try {
      await Promise.all([loadJwks(), loadNewDeviceAccess()]);
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
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to refresh app token';
      setError(message);
      toast.error(message);
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
    </>
  );
}
