import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import axios from 'axios';
import { describeError, tokenStorage } from '../api/client';
import { authApi } from '../api/services';

const SESSION_KEY = 'jhcomp.session';

interface Session {
  name: string;
  role: string;
}

interface AuthState {
  session: Session | null;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

function readSession(): Session | null {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Session;
  } catch {
    localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(readSession);

  const signIn = useCallback(async (email: string, password: string) => {
    try {
      const data = await authApi.login(email, password);
      tokenStorage.save(data.accessToken, data.refreshToken, data.expiresIn);
      const next = { name: data.name, role: data.role };
      localStorage.setItem(SESSION_KEY, JSON.stringify(next));
      setSession(next);
    } catch (error) {
      // No login, 401 significa credencial errada — não sessão expirada.
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        throw new Error('E-mail ou senha incorretos.');
      }
      throw new Error(describeError(error));
    }
  }, []);

  const signOut = useCallback(async () => {
    // Revoga no servidor antes de limpar; se falhar (token expirado/revogado, API fora),
    // o logout local acontece de qualquer jeito.
    const refreshToken = tokenStorage.refreshToken();
    if (refreshToken) {
      try {
        await authApi.logout(refreshToken);
      } catch {
        // Silencioso de propósito: o usuário sai mesmo assim.
      }
    }

    tokenStorage.clear();
    localStorage.removeItem(SESSION_KEY);
    setSession(null);
  }, []);

  const value = useMemo(() => ({ session, signIn, signOut }), [session, signIn, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
  return ctx;
}
