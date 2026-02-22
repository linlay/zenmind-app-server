import { useEffect, useMemo, useState } from 'react';
import { request } from '../../shared/api/apiClient';
import { formatTime } from '../../shared/utils/time';
import { Button } from '../../shared/ui/Button';
import { DataTable } from '../../shared/ui/DataTable';
import { EmptyState } from '../../shared/ui/EmptyState';
import { LoadingOverlay } from '../../shared/ui/LoadingOverlay';
import { PageCard } from '../../shared/ui/PageCard';
import { toast } from '../../shared/ui/toast';

const initialForm = {
  title: '',
  content: '',
  type: 'INFO'
};

export function InboxPage() {
  const [messages, setMessages] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [error, setError] = useState('');
  const [form, setForm] = useState(initialForm);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const loadInbox = async () => {
    setLoading(true);
    try {
      const [list, counter] = await Promise.all([
        request('/admin/api/inbox?limit=100'),
        request('/admin/api/inbox/unread-count')
      ]);
      setMessages(Array.isArray(list) ? list : []);
      setUnreadCount(Number(counter?.unreadCount || 0));
      setError('');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load inbox';
      setError(message);
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInbox();
  }, []);

  const sendMessage = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await request('/admin/api/inbox/send', {
        method: 'POST',
        body: JSON.stringify(form)
      });
      setForm(initialForm);
      toast.success('Message sent to inbox');
      await loadInbox();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to send message';
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const markRead = async (messageId) => {
    try {
      await request('/admin/api/inbox/read', {
        method: 'POST',
        body: JSON.stringify({ messageIds: [messageId] })
      });
      toast.success('Message marked as read');
      await loadInbox();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to mark message as read';
      setError(message);
      toast.error(message);
    }
  };

  const markAllRead = async () => {
    try {
      await request('/admin/api/inbox/read-all', {
        method: 'POST'
      });
      toast.success('All messages marked as read');
      await loadInbox();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to mark all as read';
      setError(message);
      toast.error(message);
    }
  };

  const columns = useMemo(() => [
    { key: 'title', title: 'Title', render: (message) => message.title },
    { key: 'type', title: 'Type', render: (message) => message.type },
    { key: 'content', title: 'Content', render: (message) => <div className="token-cell">{message.content}</div> },
    { key: 'read', title: 'Read', render: (message) => (message.read ? 'YES' : 'NO') },
    { key: 'createdAt', title: 'Created At', render: (message) => formatTime(message.createAt) },
    {
      key: 'actions',
      title: 'Actions',
      render: (message) => (
        !message.read ? <Button variant="secondary" onClick={() => markRead(message.messageId)}>Mark Read</Button> : <span>-</span>
      )
    }
  ], []);

  return (
    <>
      <PageCard title="Send Inbox Message">
        {error ? <div className="error">{error}</div> : null}
        <form onSubmit={sendMessage}>
          <label>Title</label>
          <input
            value={form.title}
            onChange={(event) => setForm((prev) => ({ ...prev, title: event.target.value }))}
            required
          />

          <label>Content</label>
          <textarea
            value={form.content}
            onChange={(event) => setForm((prev) => ({ ...prev, content: event.target.value }))}
            rows={4}
            required
          />

          <label>Type</label>
          <select value={form.type} onChange={(event) => setForm((prev) => ({ ...prev, type: event.target.value }))}>
            <option value="INFO">INFO</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
            <option value="SYSTEM">SYSTEM</option>
          </select>

          <Button type="submit" loading={submitting}>Send to Inbox</Button>
        </form>
      </PageCard>

      <PageCard
        title="Inbox Messages"
        actions={(
          <>
            <span className="unread-pill">Unread: {unreadCount}</span>
            <Button variant="secondary" onClick={markAllRead}>Mark All Read</Button>
          </>
        )}
      >
        <LoadingOverlay show={loading} label="Loading inbox..." />
        <DataTable
          columns={columns}
          rows={messages}
          rowKey={(message) => message.messageId}
          empty={<EmptyState title="Inbox is empty" description="Send a message to see records here." />}
        />
      </PageCard>
    </>
  );
}
