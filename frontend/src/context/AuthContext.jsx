import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import client, { TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from '../api/client';

const AuthContext = createContext(null);

function readStoredUser() {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    localStorage.removeItem(USER_STORAGE_KEY);
    return null;
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY));
  const [user, setUser] = useState(() => readStoredUser());

  const persistSession = useCallback((authPayload) => {
    const accessToken = authPayload?.accessToken;
    const summaryUser = authPayload?.user;
    if (!accessToken || !summaryUser) {
      throw new Error('Invalid authentication response received from server.');
    }
    localStorage.setItem(TOKEN_STORAGE_KEY, accessToken);
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(summaryUser));
    setToken(accessToken);
    setUser(summaryUser);
    return authPayload;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => {
      setToken(null);
      setUser(null);
    };
    window.addEventListener('dhanpay:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('dhanpay:unauthorized', handleUnauthorized);
  }, []);

  const login = useCallback(
    async ({ email, password }) => {
      const { data } = await client.post('/auth/login', {
        email: email.trim(),
        password,
      });
      return persistSession(data);
    },
    [persistSession]
  );

  const register = useCallback(
    async ({ fullName, email, password }) => {
      const { data } = await client.post('/auth/register', {
        fullName: fullName.trim(),
        email: email.trim(),
        password,
      });
      return persistSession(data);
    },
    [persistSession]
  );

  const updateUserWallet = useCallback((walletPatch) => {
    setUser((prev) => {
      if (!prev) {
        return prev;
      }
      const next = { ...prev, ...walletPatch };
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(next));
      return next;
    });
  }, []);

  const value = useMemo(
    () => ({
      user,
      token,
      isAuthenticated: Boolean(token && user),
      login,
      register,
      logout,
      updateUserWallet,
    }),
    [user, token, login, register, logout, updateUserWallet]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
