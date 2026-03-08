import { useCallback, useEffect, useMemo, useState } from 'react';
import { getErrorMessage, isHandledUnauthorizedError, request } from '../../shared/api/apiClient';
import { formatTime } from '../../shared/utils/time';
import { Button } from '../../shared/ui/Button';
import { DataTable } from '../../shared/ui/DataTable';
import { EmptyState } from '../../shared/ui/EmptyState';
import { LoadingOverlay } from '../../shared/ui/LoadingOverlay';
import { PageCard } from '../../shared/ui/PageCard';
import { toast } from '../../shared/ui/toast';

function fileKey(file) {
  return file.id || file.resolvedPath || file.path;
}

function pickSelectedPath(files, preferredPath, currentPath) {
  if (!Array.isArray(files) || files.length === 0) {
    return '';
  }
  const candidates = [preferredPath, currentPath];
  for (const candidate of candidates) {
    if (!candidate) {
      continue;
    }
    if (files.some((file) => fileKey(file) === candidate)) {
      return candidate;
    }
  }
  return fileKey(files[0]);
}

export function ConfigFilesPage() {
  const [files, setFiles] = useState([]);
  const [selectedPath, setSelectedPath] = useState('');
  const [content, setContent] = useState('');
  const [savedContent, setSavedContent] = useState('');
  const [error, setError] = useState('');
  const [listLoading, setListLoading] = useState(true);
  const [contentLoading, setContentLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const selectedFile = useMemo(
    () => files.find((file) => fileKey(file) === selectedPath) || null,
    [files, selectedPath]
  );
  const hasUnsavedChanges = content !== savedContent;

  const loadFiles = useCallback(async (preferredPath = '') => {
    setListLoading(true);
    try {
      const data = await request('/admin/api/config-files');
      const list = Array.isArray(data) ? data : [];
      setFiles(list);
      setSelectedPath((currentPath) => pickSelectedPath(list, preferredPath, currentPath));
      setError('');
    } catch (err) {
      const message = getErrorMessage(err, 'Failed to load editable files');
      if (!isHandledUnauthorizedError(err)) {
        setError(message);
        toast.error(message);
      }
    } finally {
      setListLoading(false);
    }
  }, []);

  const loadContent = useCallback(async (targetPath = selectedPath) => {
    const targetFile = files.find((file) => fileKey(file) === targetPath);
    if (!targetFile || !targetFile.exists) {
      setContent('');
      setSavedContent('');
      return;
    }
      setContentLoading(true);
    try {
      const result = await request(`/admin/api/config-files/content?id=${encodeURIComponent(targetFile.id)}`);
      const nextContent = typeof result?.content === 'string' ? result.content : '';
      setContent(nextContent);
      setSavedContent(nextContent);
      setError('');
    } catch (err) {
      const message = getErrorMessage(err, 'Failed to load file content');
      if (!isHandledUnauthorizedError(err)) {
        setError(message);
        toast.error(message);
      }
    } finally {
      setContentLoading(false);
    }
  }, [files, selectedPath]);

  useEffect(() => {
    loadFiles();
  }, [loadFiles]);

  useEffect(() => {
    if (!selectedPath) {
      setContent('');
      setSavedContent('');
      return;
    }
    loadContent(selectedPath);
  }, [selectedPath, loadContent]);

  const saveContent = async () => {
    if (!selectedFile || !selectedFile.exists) {
      return;
    }
    setSaving(true);
    try {
      await request('/admin/api/config-files/content', {
        method: 'PUT',
        body: JSON.stringify({
          id: selectedFile.id,
          content
        })
      });
      setSavedContent(content);
      setError('');
      toast.success('Configuration file saved');
      await loadFiles(fileKey(selectedFile));
    } catch (err) {
      const message = getErrorMessage(err, 'Failed to save file');
      if (!isHandledUnauthorizedError(err)) {
        setError(message);
        toast.error(message);
      }
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo(() => [
    {
      key: 'name',
      title: 'Name',
      render: (file) => (
        <div className="config-file-summary">
          <div>{file.name || file.id}</div>
          <small>{file.id}</small>
        </div>
      )
    },
    {
      key: 'type',
      title: 'Type',
      render: (file) => file.type || '-'
    },
    {
      key: 'hostPath',
      title: 'Host Path',
      render: (file) => (
        <div className="config-file-path">
          {file.hostPath || file.resolvedPath || file.path}
        </div>
      )
    },
    {
      key: 'status',
      title: 'Exists',
      render: (file) => (file.exists ? 'YES' : 'NO')
    },
    {
      key: 'size',
      title: 'Size',
      render: (file) => (file.exists ? `${Number(file.size || 0)} B` : '-')
    },
    {
      key: 'updatedAt',
      title: 'Updated At',
      render: (file) => (file.updateAt ? formatTime(file.updateAt) : '-')
    },
    {
      key: 'action',
      title: 'Action',
      render: (file) => (
        <Button
          variant={fileKey(file) === selectedPath ? 'secondary' : 'ghost'}
          onClick={() => setSelectedPath(fileKey(file))}
        >
          {fileKey(file) === selectedPath ? 'Selected' : 'Select'}
        </Button>
      )
    }
  ], [selectedPath]);

  return (
    <>
      <PageCard title="External Config Files" actions={<Button variant="ghost" onClick={() => loadFiles()}>Refresh List</Button>}>
        <LoadingOverlay show={listLoading} label="Loading editable files..." />
        {error ? <div className="error">{error}</div> : null}
        <DataTable
          columns={columns}
          rows={files}
          rowKey={fileKey}
          empty={<EmptyState title="No editable files configured" description="Configure release/config-files.yml and run make config-sync." />}
        />
      </PageCard>

      <PageCard
        title="File Editor"
        actions={(
          <>
            <Button variant="ghost" onClick={() => loadContent()} disabled={!selectedFile || !selectedFile.exists || contentLoading}>
              Reload
            </Button>
            <Button onClick={saveContent} loading={saving} disabled={!selectedFile || !selectedFile.exists || !hasUnsavedChanges || contentLoading}>
              Save
            </Button>
          </>
        )}
      >
        <LoadingOverlay show={contentLoading} label="Loading file content..." />
        {!selectedFile ? (
          <EmptyState title="No file selected" description="Select one file from the list above." />
        ) : (
          <>
            <div className="config-file-meta">
              <p>{selectedFile.name || selectedFile.id}</p>
              <small className="mono-inline">{selectedFile.hostPath || selectedFile.resolvedPath || selectedFile.path}</small>
              {!selectedFile.exists ? <div className="error">File does not exist. Create it first before editing.</div> : null}
            </div>
            <textarea
              className="config-editor"
              rows={18}
              value={content}
              onChange={(event) => setContent(event.target.value)}
              disabled={!selectedFile.exists || contentLoading}
            />
          </>
        )}
      </PageCard>
    </>
  );
}
