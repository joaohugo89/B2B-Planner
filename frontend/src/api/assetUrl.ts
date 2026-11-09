const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

/**
 * Origem do backend quando a API está em outro domínio. Com o padrão relativo ('/api',
 * usado pelo proxy do Vite e por um deploy de domínio único) fica vazia.
 */
const API_ORIGIN = /^https?:\/\//i.test(API_BASE_URL) ? new URL(API_BASE_URL).origin : '';

/** Esquemas que podem virar src de imagem ou href de download. */
const PROTOCOLOS_PERMITIDOS = ['http:', 'https:', 'blob:'];

function origemPadrao(): string {
  return API_ORIGIN || (typeof window === 'undefined' ? 'http://localhost' : window.location.origin);
}

/**
 * Resolve uma URL vinda da API. Os assets são gravados como caminho relativo
 * ('/api/assets/files/x.png'), que apontaria para o host do frontend quando ele está num
 * domínio separado; aqui o caminho passa a sair da origem do backend. URLs absolutas
 * (bucket S3/R2 público ou CDN) voltam intactas.
 *
 * <p>O que não resolver para http, https, blob ou data:image vira undefined, e a tela deixa
 * de montar o link. A decisão sai do parser de URL do próprio navegador, e não de uma regex
 * de esquemas proibidos: o navegador descarta tabs e quebras de linha no meio do esquema,
 * então "java&#9;script:" chega como javascript: e passaria por qualquer lista de bloqueio.</p>
 */
export function assetUrl(url: string): string;
export function assetUrl(url: string | null | undefined): string | undefined;
export function assetUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined;

  // Mesma limpeza que o navegador faz antes de interpretar o esquema.
  // eslint-disable-next-line no-control-regex
  const limpo = url.replace(/[\u0000-\u001F\u007F]/g, '').trim();
  if (!limpo) return undefined;

  // "//host/x.png" não é caminho: o navegador o lê como outro domínio.
  if (limpo.startsWith('//')) return undefined;

  // data: nunca é relativo, e só imagem interessa aqui.
  if (/^data:/i.test(limpo)) {
    return /^data:image\//i.test(limpo) ? limpo : undefined;
  }

  let resolvida: URL;
  try {
    resolvida = new URL(limpo, origemPadrao());
  } catch {
    return undefined;
  }

  if (!PROTOCOLOS_PERMITIDOS.includes(resolvida.protocol)) {
    return undefined;
  }

  // Sem origem de backend configurada, o caminho relativo segue relativo, como antes.
  if (!API_ORIGIN && !/^https?:/i.test(limpo) && !/^blob:/i.test(limpo)) {
    return resolvida.pathname + resolvida.search;
  }

  return resolvida.href;
}
