import axios from 'axios';

export const TOKEN_STORAGE_KEY = 'dhanpay_token';
export const USER_STORAGE_KEY = 'dhanpay_user';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

client.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const requestUrl = error?.config?.url || '';
    const isAuthAttempt =
      requestUrl.includes('/auth/login') || requestUrl.includes('/auth/register');

    if (status === 401 && !isAuthAttempt) {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USER_STORAGE_KEY);
      window.dispatchEvent(new Event('dhanpay:unauthorized'));

      if (window.location.pathname !== '/login') {
        window.location.replace('/login');
      }
    }

    return Promise.reject(error);
  }
);

/**
 * Extracts a human-readable message and machine code from Spring Boot ApiErrorResponse payloads.
 */
export function extractApiError(error, fallback = 'An unexpected error occurred. Please try again.') {
  const data = error?.response?.data;
  if (data && typeof data === 'object') {
    return {
      code: data.code || 'API_ERROR',
      message: data.message || fallback,
      fieldErrors: data.fieldErrors || null,
    };
  }
  if (error?.message) {
    return {
      code: 'NETWORK_ERROR',
      message: error.message,
      fieldErrors: null,
    };
  }
  return {
    code: 'UNKNOWN_ERROR',
    message: fallback,
    fieldErrors: null,
  };
}

export default client;
