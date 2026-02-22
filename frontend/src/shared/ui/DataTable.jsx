export function DataTable({ columns, rows, empty, rowKey, compact = false }) {
  if (!rows.length) {
    return empty || null;
  }

  return (
    <div className="table-wrap">
      <table className={`table ${compact ? 'compact' : ''}`.trim()}>
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
                <td key={column.key}>{column.render(row)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
