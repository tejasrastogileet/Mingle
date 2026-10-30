const API_CONTEXT_PATH = '/api/v1.0';
const configuredBackendUrl = import.meta.env.VITE_BACKEND_URL?.trim();

if (!configuredBackendUrl) {
  throw new Error('VITE_BACKEND_URL must be set to the backend base URL.');
}

const backendUrl = configuredBackendUrl.replace(/\/+$/, '');

export const backendApiUrl = backendUrl.endsWith(API_CONTEXT_PATH)
  ? backendUrl
  : `${backendUrl}${API_CONTEXT_PATH}`;

export const getChatWebSocketUrl = (roomId) => {
  const url = new URL(backendApiUrl);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = `${url.pathname.replace(/\/+$/, '')}/ws/chat/${encodeURIComponent(roomId)}`;
  return url.toString();
};
