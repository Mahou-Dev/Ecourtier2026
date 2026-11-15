export function resolveApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured !== undefined) {
    return configured.replace(/\/+$/, '');
  }
  if (typeof window !== 'undefined' && window.location.port === '5173') {
    return 'http://localhost:5173';
  }
  return 'http://localhost:8080';
}

export function resolveWsUrl(path = '/ws'): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const baseUrl = resolveApiBaseUrl();
  if (/^https?:\/\//i.test(baseUrl)) {
    return `${baseUrl.replace(/^http/i, 'ws')}${normalizedPath}`;
  }
  if (typeof window !== 'undefined') {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const cleanBase = baseUrl.startsWith('/') ? baseUrl : '';
    return `${protocol}//${window.location.host}${cleanBase}${normalizedPath}`;
  }
  return `ws://localhost:8080${normalizedPath}`;
}
