import { buildAdminApiUrl } from '../config/urls';

const unauthorizedListeners = new Set();

export class ApiError extends Error {
  constructor(message, { status, payload = null, code = null, handled = false } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status ?? 0;
    this.payload = payload;
    this.code = code;
    this.handled = handled;
  }
}

function emitUnauthorized(event) {
  unauthorizedListeners.forEach((listener) => listener(event));
}

export function subscribeUnauthorized(listener) {
  unauthorizedListeners.add(listener);
  return () => {
    unauthorizedListeners.delete(listener);
  };
}

export function isUnauthorizedError(error) {
  return error instanceof ApiError && error.code === 'UNAUTHORIZED';
}

export function isHandledUnauthorizedError(error) {
  return isUnauthorizedError(error) && error.handled;
}

export function getErrorMessage(error, fallback = 'Request failed') {
  if (isHandledUnauthorizedError(error)) {
    return '';
  }
  return error instanceof Error && error.message ? error.message : fallback;
}

export async function request(path, options = {}) {
  const { skipAuthRedirect = false, headers: optionHeaders, ...fetchOptions } = options;
  const headers = { ...(optionHeaders || {}) };
  const requestUrl = buildAdminApiUrl(path);
  if (fetchOptions.body && !(fetchOptions.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(requestUrl, {
    ...fetchOptions,
    headers,
    credentials: 'include'
  });

  const text = await response.text();
  let payload = null;

  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = { error: text };
    }
  }

  if (!response.ok) {
    const message = (payload && payload.error) || `HTTP ${response.status}`;
    const isUnauthorized = response.status === 401;
    const handled = isUnauthorized && !skipAuthRedirect;
    const error = new ApiError(message, {
      status: response.status,
      payload,
      code: isUnauthorized ? 'UNAUTHORIZED' : null,
      handled
    });

    if (handled) {
      emitUnauthorized({ path: requestUrl, status: response.status, message });
    }

    throw error;
  }

  return payload;
}
