import { useState } from 'react';
import { request } from '../../shared/api/apiClient';
import { copyToClipboard } from '../../shared/utils/clipboard';
import { Button } from '../../shared/ui/Button';
import { PageCard } from '../../shared/ui/PageCard';
import { toast } from '../../shared/ui/toast';

export function ToolsPage() {
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [bcryptPassword, setBcryptPassword] = useState('');
  const [bcryptResult, setBcryptResult] = useState('');
  const [jwkForm, setJwkForm] = useState({ e: 'AQAB', n: '' });
  const [publicKeyResult, setPublicKeyResult] = useState('');
  const [keyPairResult, setKeyPairResult] = useState(null);

  const copyText = async (text) => {
    if (await copyToClipboard(text)) {
      setSuccess('Copied to clipboard');
      toast.success('Copied to clipboard');
    } else {
      setError('Failed to copy to clipboard');
      toast.error('Failed to copy to clipboard');
    }
  };

  const generateBcrypt = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');

    try {
      const result = await request('/admin/api/bcrypt/generate', {
        method: 'POST',
        body: JSON.stringify({ password: bcryptPassword })
      });
      setBcryptResult(result.bcrypt || '');
      setSuccess('Generated bcrypt successfully');
      toast.success('Generated bcrypt successfully');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to generate bcrypt';
      setError(message);
      toast.error(message);
    }
  };

  const generatePublicKey = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');

    try {
      const result = await request('/admin/api/security/public-key/generate', {
        method: 'POST',
        body: JSON.stringify({ e: jwkForm.e, n: jwkForm.n })
      });
      setPublicKeyResult(result.publicKey || '');
      setSuccess('Generated public key successfully');
      toast.success('Generated public key successfully');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to generate public key';
      setError(message);
      toast.error(message);
    }
  };

  const generateKeyPair = async () => {
    setError('');
    setSuccess('');

    try {
      const result = await request('/admin/api/security/key-pair/generate', {
        method: 'POST'
      });
      setKeyPairResult(result);
      setSuccess('Generated key pair successfully');
      toast.success('Generated key pair successfully');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to generate key pair';
      setError(message);
      toast.error(message);
    }
  };

  return (
    <>
      <PageCard title="Security Tools">
        {error ? <div className="error">{error}</div> : null}
        {success ? <div className="success">{success}</div> : null}
      </PageCard>

      <PageCard title="Bcrypt Generator">
        <form onSubmit={generateBcrypt}>
          <label>Password</label>
          <input
            type="text"
            value={bcryptPassword}
            onChange={(event) => setBcryptPassword(event.target.value)}
            required
          />
          <Button type="submit">Generate Bcrypt</Button>
        </form>
        {bcryptResult ? (
          <>
            <pre className="pem-block spacing-top">{bcryptResult}</pre>
            <Button variant="secondary" onClick={() => copyText(bcryptResult)}>Copy Bcrypt</Button>
          </>
        ) : null}
      </PageCard>

      <PageCard title="PublicKey Generator (e+n)">
        <form onSubmit={generatePublicKey}>
          <label>Exponent (e)</label>
          <input
            value={jwkForm.e}
            onChange={(event) => setJwkForm((prev) => ({ ...prev, e: event.target.value }))}
            required
          />
          <label>Modulus (n)</label>
          <textarea
            value={jwkForm.n}
            onChange={(event) => setJwkForm((prev) => ({ ...prev, n: event.target.value }))}
            rows={5}
            required
          />
          <Button type="submit">Generate PublicKey</Button>
        </form>
        {publicKeyResult ? (
          <>
            <pre className="pem-block spacing-top">{publicKeyResult}</pre>
            <Button variant="secondary" onClick={() => copyText(publicKeyResult)}>Copy PublicKey</Button>
          </>
        ) : null}
      </PageCard>

      <PageCard title="Private/Public Key Pair Generator">
        <Button onClick={generateKeyPair}>Generate RSA2048 Key Pair</Button>
        {keyPairResult ? (
          <div className="spacing-top">
            <label>Public Key</label>
            <pre className="pem-block">{keyPairResult.publicKey}</pre>
            <Button variant="secondary" onClick={() => copyText(keyPairResult.publicKey)}>Copy Public Key</Button>
            <label className="spacing-top-sm">Private Key</label>
            <pre className="pem-block">{keyPairResult.privateKey}</pre>
            <Button variant="secondary" onClick={() => copyText(keyPairResult.privateKey)}>Copy Private Key</Button>
          </div>
        ) : null}
      </PageCard>
    </>
  );
}
