const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

/**
 * Origem do backend quando a API está em outro domínio. Com o padrão relativo ('/api',
 * usado pelo proxy do Vite e por um deploy de domínio único) fica vazia.
 */
const API_ORIGIN = /^https?:\/\//i.test(API_BASE_URL) ? new URL(API_BASE_URL).origin : '';

/** Esquemas que podem virar src de imagem ou href de download sem risco. */
const SEGUROS = /^(https?:|blob:|data:image\/)/i;

/** Qualquer "palavra:" no começo é um esquema — inclusive javascript: e vbscript:. */
const TEM_ESQUEMA = /^[a-z][a-z0-9+.-]*:/i;

/**
 * Resolve uma URL vinda da API. Os assets são gravados como caminho relativo
 * ('/api/assets/files/x.png'), que apontaria para o host do frontend quando ele está num
 * domínio separado; aqui o caminho passa a sair da origem do backend. URLs absolutas
 * (bucket S3/R2 público ou CDN) voltam intactas.
 *
 * <p>Um valor com esquema estranho — `javascript:` à frente — vira undefined em vez de
 * seguir para o href: o campo é gravado por quem está logado, mas uma tela que confia no
 * banco para montar link executável é o caminho clássico de XSS armazenado.</p>
 */
export function assetUrl(url: string): string;
export function assetUrl(url: string | null | undefined): string | undefined;
export function assetUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined;

  const limpo = url.trim();
  if (SEGUROS.test(limpo)) return limpo;
  if (TEM_ESQUEMA.test(limpo)) return undefined;

  if (!API_ORIGIN) return limpo;
  return API_ORIGIN + (limpo.startsWith('/') ? limpo : '/' + limpo);
}
