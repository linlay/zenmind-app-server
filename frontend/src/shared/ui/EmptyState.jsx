export function EmptyState({ title = 'No data', description = 'Nothing to show yet.' }) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <p>{description}</p>
    </div>
  );
}
