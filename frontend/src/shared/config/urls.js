export const uiBaseUrl = '/admin/';
export const adminApiBaseUrl = '/admin/api/';

export function toRouterBasename(baseUrl = uiBaseUrl) {
  const normalized = String(baseUrl || '/').trim();
  if (!normalized || normalized === '/') {
    return '/';
  }
  return normalized.endsWith('/') ? normalized.slice(0, -1) : normalized;
}

export function buildAdminApiUrl(path = '') {
  const normalized = String(path || '').trim();
  if (!normalized) {
    return adminApiBaseUrl;
  }
  if (/^(?:https?:)?\/\//.test(normalized) || normalized.startsWith(adminApiBaseUrl)) {
    return normalized;
  }
  if (normalized.startsWith('/')) {
    return `${adminApiBaseUrl.slice(0, -1)}${normalized}`;
  }
  return `${adminApiBaseUrl}${normalized}`;
}
