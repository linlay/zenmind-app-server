import { useEffect, useState } from 'react';
import { subscribeToast } from './toast';

export function ToastViewport() {
  const [items, setItems] = useState([]);

  useEffect(() => {
    return subscribeToast((item) => {
      setItems((prev) => [...prev, item]);
      window.setTimeout(() => {
        setItems((prev) => prev.filter((toast) => toast.id !== item.id));
      }, 2600);
    });
  }, []);

  return (
    <div className="toast-viewport" aria-live="polite" aria-atomic="true">
      {items.map((item) => (
        <div key={item.id} className={`toast toast-${item.level}`}>
          {item.message}
        </div>
      ))}
    </div>
  );
}
