import axios, { AxiosError } from 'axios';

const ACCESS_TOKEN_KEY = 'jhcomp.accessToken';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use(async (config) => {
  // Renova antes de enviar: num upload grande, o servidor corta a conexão ao recusar
  // o token e o erro chega como falha de rede, sem 401 para o interceptor de resposta.
  if (isExpiringSoon() && !config.url?.includes('/auth/')) {
    await refreshAccessToken();
  }

  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // Em upload, o navegador define o multipart com boundary — o padrão JSON quebraria o parse.
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    config.headers.delete('Content-Type');
  }
  return config;
});

const REFRESH_TOKEN_KEY = 'jhcomp.refreshToken';
const EXPIRES_AT_KEY = 'jhcomp.expiresAt';
let refreshing: Promise<string | null> | null = null;

/** Considera expirado com 60s de folga, para não disparar requests com token no limite. */
function isExpiringSoon(): boolean {
  if (!localStorage.getItem(ACCESS_TOKEN_KEY)) return false;
  const expiresAt = Number(localStorage.getItem(EXPIRES_AT_KEY) ?? 0);
  return expiresAt > 0 && Date.now() > expiresAt - 60_000;
}

/** Troca o refresh token por um novo access token (uma renovação por vez). */
async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) return null;

  refreshing ??= axios
    .post<{ accessToken: string; refreshToken: string; expiresIn: number }>('/api/auth/refresh', { refreshToken })
    .then(({ data }) => {
      tokenStorage.save(data.accessToken, data.refreshToken, data.expiresIn);
      return data.accessToken;
    })
    .catch(() => {
      tokenStorage.clear();
      return null;
    })
    .finally(() => {
      refreshing = null;
    });

  return refreshing;
}

// Em 401, tenta renovar o token uma vez e repete a requisição original.
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (AxiosError['config'] & { _retried?: boolean }) | undefined;
    const isAuthCall = original?.url?.includes('/auth/');

    if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      const token = await refreshAccessToken();
      if (token) {
        original.headers = original.headers ?? {};
        (original.headers as Record<string, string>).Authorization = `Bearer ${token}`;
        return api.request(original);
      }
    }

    return Promise.reject(error);
  },
);

/** Converte qualquer falha do axios numa mensagem amigável em pt-BR. */
export function describeError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<{ message?: string }>;
    if (axiosError.code === 'ECONNABORTED') {
      return 'O backend demorou demais para responder. Verifique se a API está no ar.';
    }
    if (!axiosError.response) {
      return 'Não foi possível falar com a API. O backend está rodando?';
    }
    const { status, data } = axiosError.response;
    if (status === 401) return 'Sessão expirada ou credenciais inválidas.';
    if (status === 403) return 'Acesso negado para esta operação.';
    if (status === 404) return data?.message ?? 'Registro não encontrado.';
    return data?.message ?? `Falha inesperada na API (HTTP ${status}).`;
  }
  return 'Erro inesperado no cliente.';
}

export const tokenStorage = {
  refreshToken() {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },
  save(accessToken: string, refreshToken: string, expiresInSeconds?: number) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    if (expiresInSeconds) {
      localStorage.setItem(EXPIRES_AT_KEY, String(Date.now() + expiresInSeconds * 1000));
    }
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
  },
};
