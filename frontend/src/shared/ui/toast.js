const listeners = new Set();

function emit(level, message) {
  const item = {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    level,
    message
  };
  listeners.forEach((listener) => listener(item));
}

export const toast = {
  success(message) {
    emit('success', message);
  },
  error(message) {
    emit('error', message);
  },
  info(message) {
    emit('info', message);
  }
};

export function subscribeToast(listener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
