/** Primitivas de desenho usadas pelos layouts de post. */
import { assetUrl } from '../api/assetUrl';

export const BRAND = {
  red: '#ff5354',
  redDeep: '#c41a24',
  ink: '#0c0e11',
  inkSoft: 'rgba(12, 14, 17, 0.82)',
  light: '#e2e2e6',
  lightMuted: '#c5c6c9',
  darkText: '#111317',
  darkMuted: '#3f4247',
  paper: 'rgba(255, 255, 255, 0.86)',
};

export const FONTS = {
  display: 'Syne',
  body: '"Plus Jakarta Sans"',
  mono: '"JetBrains Mono"',
};

export interface Palette {
  text: string;
  muted: string;
  panel: string;
  accent: string;
  onAccent: string;
}

export function paletteFor(theme: 'DARK' | 'LIGHT'): Palette {
  return theme === 'DARK'
    ? { text: BRAND.light, muted: BRAND.lightMuted, panel: BRAND.inkSoft, accent: BRAND.red, onAccent: '#2b0004' }
    : { text: BRAND.darkText, muted: BRAND.darkMuted, panel: BRAND.paper, accent: BRAND.redDeep, onAccent: '#ffffff' };
}

/** Garante que as fontes da marca estejam prontas antes de desenhar. */
export async function ensureFonts(): Promise<void> {
  if (!('fonts' in document)) return;
  const faces = [
    '800 72px Syne',
    '700 32px Syne',
    '500 28px "Plus Jakarta Sans"',
    '700 20px "JetBrains Mono"',
  ];
  await Promise.all(faces.map((font) => document.fonts.load(font, 'JH.COMP')));
  await document.fonts.ready;
}

export function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    // assetUrl: com a API em outro domínio, o caminho relativo do asset sairia do host do frontend.
    image.crossOrigin = 'anonymous';
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('Não foi possível carregar a imagem do template.'));
    image.src = assetUrl(src);
  });
}

/** Desenha a imagem cobrindo o canvas inteiro, cortando o excedente pelo centro. */
export function drawCover(ctx: CanvasRenderingContext2D, image: HTMLImageElement, width: number, height: number) {
  drawCoverIn(ctx, image, 0, 0, width, height);
}

export interface PhotoTransform {
  /** 1 = cobre a área; acima aproxima e corta, abaixo encolhe e sobra espaço. */
  zoom?: number;
  /** -1 (todo à esquerda / topo) a 1 (todo à direita / base). */
  offsetX?: number;
  offsetY?: number;
  /** Cor usada atrás da foto quando ela fica menor que a área. */
  backdrop?: string;
}

export const MIN_PHOTO_ZOOM = 0.4;
export const MAX_PHOTO_ZOOM = 3;

/**
 * Cobre a área informada com a imagem, aplicando zoom e deslocamento.
 * O deslocamento é relativo ao excedente, então nunca deixa borda vazia.
 */
export function drawCoverIn(
  ctx: CanvasRenderingContext2D,
  image: HTMLImageElement,
  x: number,
  y: number,
  width: number,
  height: number,
  transform: PhotoTransform = {},
) {
  const zoom = Math.min(MAX_PHOTO_ZOOM, Math.max(MIN_PHOTO_ZOOM, transform.zoom ?? 1));
  const offsetX = Math.min(1, Math.max(-1, transform.offsetX ?? 0));
  const offsetY = Math.min(1, Math.max(-1, transform.offsetY ?? 0));

  const scale = Math.max(width / image.width, height / image.height) * zoom;
  const w = image.width * scale;
  const h = image.height * scale;

  // Abaixo de 100% a foto não cobre tudo: pinta o fundo para não ficar buraco.
  if (transform.backdrop && (w < width || h < height)) {
    ctx.save();
    ctx.fillStyle = transform.backdrop;
    ctx.fillRect(x, y, width, height);
    ctx.restore();
  }

  // Com folga (zoom < 1) o deslocamento passeia pelo espaço livre; com excedente, pelo corte.
  const slackX = Math.abs(w - width) / 2;
  const slackY = Math.abs(h - height) / 2;
  const left = x + (width - w) / 2 + offsetX * slackX;
  const top = y + (height - h) / 2 + offsetY * slackY;

  ctx.drawImage(image, left, top, w, h);
}

export function roundedRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius = 12,
) {
  const r = Math.min(radius, width / 2, height / 2);
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + width, y, x + width, y + height, r);
  ctx.arcTo(x + width, y + height, x, y + height, r);
  ctx.arcTo(x, y + height, x, y, r);
  ctx.arcTo(x, y, x + width, y, r);
  ctx.closePath();
}

/** Quebra o texto em linhas que cabem em maxWidth. */
export function wrapLines(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = [];

  for (const paragraph of text.split('\n')) {
    if (!paragraph.trim()) {
      lines.push('');
      continue;
    }
    let current = '';
    for (const word of paragraph.split(/\s+/)) {
      const candidate = current ? `${current} ${word}` : word;
      if (ctx.measureText(candidate).width <= maxWidth || !current) {
        current = candidate;
      } else {
        lines.push(current);
        current = word;
      }
    }
    if (current) lines.push(current);
  }

  return lines;
}

interface TextBlockOptions {
  text: string;
  x: number;
  y: number;
  maxWidth: number;
  font: string;
  color: string;
  lineHeight: number;
  align?: CanvasTextAlign;
  letterSpacing?: string;
  uppercase?: boolean;
  maxLines?: number;
}

/** Desenha um bloco de texto e devolve a altura ocupada. */
export function drawTextBlock(ctx: CanvasRenderingContext2D, options: TextBlockOptions): number {
  const { text, x, y, maxWidth, font, color, lineHeight, align = 'left', letterSpacing, uppercase, maxLines } = options;
  if (!text.trim()) return 0;

  ctx.save();
  ctx.font = font;
  ctx.fillStyle = color;
  ctx.textAlign = align;
  ctx.textBaseline = 'top';
  if (letterSpacing && 'letterSpacing' in ctx) {
    (ctx as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = letterSpacing;
  }

  const content = uppercase ? text.toUpperCase() : text;
  let lines = wrapLines(ctx, content, maxWidth);
  if (maxLines && lines.length > maxLines) {
    lines = lines.slice(0, maxLines);
    lines[maxLines - 1] = `${lines[maxLines - 1].replace(/[\s.,;:]+$/, '')}…`;
  }

  lines.forEach((line, index) => ctx.fillText(line, x, y + index * lineHeight));
  ctx.restore();

  return lines.length * lineHeight;
}

/** Mede a altura que um bloco ocuparia, sem desenhar. */
export function measureTextBlock(
  ctx: CanvasRenderingContext2D,
  text: string,
  font: string,
  maxWidth: number,
  lineHeight: number,
  uppercase?: boolean,
): number {
  if (!text.trim()) return 0;
  ctx.save();
  ctx.font = font;
  const lines = wrapLines(ctx, uppercase ? text.toUpperCase() : text, maxWidth);
  ctx.restore();
  return lines.length * lineHeight;
}

/** Reduz o corpo da fonte até o texto caber em no máximo maxLines. */
export function fitFontSize(
  ctx: CanvasRenderingContext2D,
  text: string,
  buildFont: (size: number) => string,
  maxWidth: number,
  startSize: number,
  minSize: number,
  maxLines: number,
): number {
  let size = startSize;
  while (size > minSize) {
    ctx.save();
    ctx.font = buildFont(size);
    const lines = wrapLines(ctx, text, maxWidth);
    // Uma palavra sozinha maior que a largura não quebra: precisa diminuir o corpo.
    const overflows = lines.some((line) => ctx.measureText(line).width > maxWidth);
    ctx.restore();
    if (lines.length <= maxLines && !overflows) break;
    size -= 2;
  }
  return size;
}

/** Véu sobre o template para o texto ficar legível em qualquer arte. */
export function drawScrim(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  theme: 'DARK' | 'LIGHT',
  strength: number,
) {
  if (strength <= 0) return;

  const gradient = ctx.createLinearGradient(0, 0, 0, height);
  if (theme === 'DARK') {
    gradient.addColorStop(0, `rgba(12, 14, 17, ${0.35 * strength})`);
    gradient.addColorStop(0.45, `rgba(12, 14, 17, ${0.55 * strength})`);
    gradient.addColorStop(1, `rgba(12, 14, 17, ${0.92 * strength})`);
  } else {
    gradient.addColorStop(0, `rgba(255, 255, 255, ${0.30 * strength})`);
    gradient.addColorStop(0.45, `rgba(255, 255, 255, ${0.55 * strength})`);
    gradient.addColorStop(1, `rgba(255, 255, 255, ${0.90 * strength})`);
  }

  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, width, height);
}
