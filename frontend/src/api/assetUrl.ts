const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

/**
 * Origem do backend quando a API está em outro domínio. Com o padrão relativo ('/api',
 * usado pelo proxy do Vite e por um deploy de domínio único) fica vazia.
 */
const API_ORIGIN = /^https?:\/\//i.test(API_BASE_URL) ? new URL(API_BASE_URL).origin : '';

/**
 * Resolve uma URL vinda da API. Os assets são gravados como caminho relativo
 * ('/api/assets/files/x.png'), que apontaria para o host do frontend quando ele está num
 * domínio separado; aqui o caminho passa a sair da origem do backend. URLs absolutas
 * (bucket S3/R2 público ou CDN) voltam intactas.
 */
export function assetUrl(url: string): string;
export function assetUrl(url: string | null | undefined): string | undefined;
export function assetUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined;
  if (/^(https?:|data:|blob:)/i.test(url)) return url;
  if (!API_ORIGIN) return url;
  return API_ORIGIN + (url.startsWith('/') ? url : '/' + url);
}
