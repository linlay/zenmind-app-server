export function DataTable({ columns, rows, empty, rowKey, compact = false, stackedOnMobile = true }) {
  if (!rows.length) {
    return empty || null;
  }

  return (
    <div className="table-wrap">
      <table className={`table ${compact ? 'compact' : ''} ${stackedOnMobile ? 'stack-mobile' : ''}`.trim()}>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.title}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((column) => (
                <td key={column.key} data-label={column.title}>{column.render(row)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
