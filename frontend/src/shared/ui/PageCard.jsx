export function PageCard({ title, actions, children, className = '' }) {
  return (
    <section className={`card page-transition ${className}`.trim()}>
      {(title || actions) && (
        <header className="card-header">
          {title ? <h3>{title}</h3> : <span />}
          {actions ? <div className="inline-actions">{actions}</div> : null}
        </header>
      )}
      {children}
    </section>
  );
}
