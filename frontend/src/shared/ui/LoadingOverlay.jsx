export function LoadingOverlay({ show, label = 'Loading...' }) {
  if (!show) return null;

  return (
    <div className="loading-overlay" role="status" aria-live="polite">
      <div className="spinner" />
      <span>{label}</span>
    </div>
  );
}
