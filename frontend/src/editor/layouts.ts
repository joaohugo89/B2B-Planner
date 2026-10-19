import {
  BRAND,
  FONTS,
  drawCover,
  drawCoverIn,
  drawScrim,
  drawTextBlock,
  fitFontSize,
  loadImage,
  measureTextBlock,
  paletteFor,
  roundedRect,
  type PhotoTransform,
} from './canvas';

export type LayoutId = 'HERO' | 'TOPICOS' | 'SLIDE';
export type Ratio = 'RATIO_4_5' | 'RATIO_1_1';

/** Multiplicadores de tamanho por tipo de texto (1 = tamanho padrão do layout). */
export interface TextScale {
  headline: number;
  body: number;
  label: number;
}

export const DEFAULT_SCALE: TextScale = { headline: 1, body: 1, label: 1 };

/** Onde o bloco de texto fica na arte. */
export type VerticalAnchor = 'TOP' | 'MIDDLE' | 'BOTTOM';
export type HorizontalAlign = 'LEFT' | 'CENTER';

/** Como a foto enviada pelo usuário entra na arte. */
export type PhotoMode = 'BACKGROUND' | 'FRAME';

export interface PostSpec {
  scale: TextScale;
  /** Foto do usuário (URL do asset); null usa só o template. */
  photoUrl: string | null;
  /** BACKGROUND cobre a arte inteira; FRAME mostra a foto em um quadro acima do texto. */
  photoMode: PhotoMode;
  /** Aproximação da foto: 1 cobre a área, até 3 aproxima e corta mais. */
  photoZoom: number;
  /** Deslocamento da foto dentro do corte, de -1 a 1. */
  photoOffsetX: number;
  photoOffsetY: number;
  /** Ancoragem vertical do bloco de texto. */
  anchor: VerticalAnchor;
  /** Alinhamento horizontal do texto. */
  align: HorizontalAlign;
  /** Deslocamento fino em px (negativo sobe, positivo desce). */
  offsetY: number;
  templateUrl: string | null;
  ratio: Ratio;
  theme: 'DARK' | 'LIGHT';
  layout: LayoutId;
  eyebrow: string;
  headline: string;
  body: string;
  bullets: string[];
  cta: string;
  badge: string;
  showLogo: boolean;
  scrim: number;
}

export const LAYOUTS: { id: LayoutId; label: string; hint: string }[] = [
  { id: 'HERO', label: 'Hero', hint: 'Manchete grande + apoio + faixa de CTA' },
  { id: 'TOPICOS', label: 'Tópicos', hint: 'Manchete + lista numerada de pontos' },
  { id: 'SLIDE', label: 'Slide', hint: 'Capa de carrossel com indicador e texto corrido' },
];

export function canvasSize(ratio: Ratio): { width: number; height: number } {
  return ratio === 'RATIO_1_1' ? { width: 1080, height: 1080 } : { width: 1080, height: 1350 };
}

/** Margens seguras do Instagram: laterais 72px, topo/base livres de UI nativa. */
function safeArea(ratio: Ratio) {
  const { width, height } = canvasSize(ratio);
  const side = 72;
  const top = ratio === 'RATIO_1_1' ? 96 : 150;
  const bottom = ratio === 'RATIO_1_1' ? 96 : 150;
  return { side, top, bottom, width, height, innerWidth: width - side * 2 };
}

/** Selo "JH" no topo, igual ao da identidade. */
function drawLogoBadge(ctx: CanvasRenderingContext2D, x: number, y: number, theme: 'DARK' | 'LIGHT') {
  const palette = paletteFor(theme);
  const size = 86;

  ctx.save();
  ctx.fillStyle = palette.panel;
  roundedRect(ctx, x, y, size, size, 10);
  ctx.fill();
  ctx.strokeStyle = palette.accent;
  ctx.lineWidth = 2;
  ctx.stroke();

  ctx.fillStyle = palette.accent;
  ctx.font = `800 38px ${FONTS.display}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('JH', x + size / 2, y + size / 2 + 2);
  ctx.restore();

  return size;
}

function drawBadgePill(
  ctx: CanvasRenderingContext2D,
  text: string,
  right: number,
  y: number,
  theme: 'DARK' | 'LIGHT',
  scale: number,
) {
  if (!text.trim()) return;
  const palette = paletteFor(theme);
  const label = text.toUpperCase();

  ctx.save();
  ctx.font = `700 ${Math.round(20 * scale)}px ${FONTS.mono}`;
  const paddingX = 20;
  const width = ctx.measureText(label).width + paddingX * 2;
  const height = Math.round(44 * scale);
  const x = right - width;

  ctx.fillStyle = palette.accent;
  roundedRect(ctx, x, y, width, height, 8);
  ctx.fill();

  ctx.fillStyle = palette.onAccent;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(label, x + width / 2, y + height / 2 + 1);
  ctx.restore();
}

/** Faixa de CTA no rodapé, no mesmo gradiente dos botões da marca. */
function drawCtaBar(
  ctx: CanvasRenderingContext2D,
  text: string,
  ratio: Ratio,
  theme: 'DARK' | 'LIGHT',
  scale: number,
) {
  if (!text.trim()) return 0;
  const area = safeArea(ratio);
  const palette = paletteFor(theme);
  const height = Math.round(92 * scale);
  const y = area.height - area.bottom - height;

  ctx.save();
  const gradient = ctx.createLinearGradient(area.side, y, area.side + area.innerWidth, y);
  gradient.addColorStop(0, BRAND.red);
  gradient.addColorStop(1, BRAND.redDeep);
  ctx.fillStyle = gradient;
  roundedRect(ctx, area.side, y, area.innerWidth, height, 10);
  ctx.fill();

  ctx.fillStyle = theme === 'DARK' ? '#2b0004' : '#ffffff';
  ctx.font = `700 ${Math.round(26 * scale)}px ${FONTS.mono}`;
  ctx.textAlign = 'left';
  ctx.textBaseline = 'middle';
  if ('letterSpacing' in ctx) {
    (ctx as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = '2px';
  }
  ctx.fillText(text.toUpperCase(), area.side + 32, y + height / 2 + 1);

  // Seta à direita.
  const arrowX = area.side + area.innerWidth - 44;
  const midY = y + height / 2;
  ctx.strokeStyle = theme === 'DARK' ? '#2b0004' : '#ffffff';
  ctx.lineWidth = 4;
  ctx.beginPath();
  ctx.moveTo(arrowX - 18, midY);
  ctx.lineTo(arrowX, midY);
  ctx.moveTo(arrowX - 8, midY - 9);
  ctx.lineTo(arrowX, midY);
  ctx.lineTo(arrowX - 8, midY + 9);
  ctx.stroke();
  ctx.restore();

  void palette;
  return height + 28;
}

function drawEyebrow(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  maxWidth: number,
  theme: 'DARK' | 'LIGHT',
  scale: number,
  align: CanvasTextAlign = 'left',
) {
  if (!text.trim()) return 0;
  const palette = paletteFor(theme);
  return drawTextBlock(ctx, {
    text: `// ${text}`,
    x,
    y,
    maxWidth,
    align,
    font: `700 ${Math.round(24 * scale)}px ${FONTS.mono}`,
    color: palette.accent,
    lineHeight: Math.round(32 * scale),
    letterSpacing: '3px',
    uppercase: true,
    maxLines: 1,
  });
}

/** Desenha o post completo no contexto informado. */
export async function renderPost(ctx: CanvasRenderingContext2D, spec: PostSpec): Promise<void> {
  const area = safeArea(spec.ratio);
  const palette = paletteFor(spec.theme);

  ctx.clearRect(0, 0, area.width, area.height);
  ctx.fillStyle = spec.theme === 'DARK' ? BRAND.ink : '#f4f4f6';
  ctx.fillRect(0, 0, area.width, area.height);

  const photoAsBackground = Boolean(spec.photoUrl) && (spec.photoMode ?? 'FRAME') === 'BACKGROUND';

  if (photoAsBackground) {
    // Com zoom menor que 100% a foto não cobre tudo: o template fica aparecendo por trás.
    if (spec.templateUrl && (spec.photoZoom ?? 1) < 1) {
      drawCover(ctx, await loadImage(spec.templateUrl), area.width, area.height);
    }
    drawCoverIn(ctx, await loadImage(spec.photoUrl!), 0, 0, area.width, area.height, photoTransform(spec));
  } else if (spec.templateUrl) {
    drawCover(ctx, await loadImage(spec.templateUrl), area.width, area.height);
  }

  // Foto como fundo precisa de um véu mais forte para o texto continuar legível.
  drawScrim(ctx, area.width, area.height, spec.theme, photoAsBackground ? Math.max(spec.scrim, 0.75) : spec.scrim);

  // Cabeçalho: selo + badge.
  let headerBottom = area.top;
  if (spec.showLogo) {
    const size = drawLogoBadge(ctx, area.side, area.top, spec.theme);
    headerBottom = area.top + size;
  }
  const labelScale = spec.scale?.label ?? 1;
  drawBadgePill(ctx, spec.badge, area.side + area.innerWidth, area.top + (spec.showLogo ? 21 : 0), spec.theme, labelScale);
  if (spec.badge.trim() && !spec.showLogo) {
    headerBottom = area.top + Math.round(44 * labelScale);
  }

  const ctaHeight = drawCtaBar(ctx, spec.cta, spec.ratio, spec.theme, labelScale);
  const contentBottom = area.height - area.bottom - ctaHeight;

  // Foto em destaque: quadro logo abaixo do cabeçalho, o texto desce para o espaço restante.
  if (spec.photoUrl && (spec.photoMode ?? 'FRAME') === 'FRAME') {
    const top = headerBottom + 28;
    const available = contentBottom - top;

    // O texto vem primeiro: a foto fica com o que sobra, nunca empurrando o bloco sobre a CTA.
    const textNeeds = textBlockMinHeight(ctx, spec, area);
    const frameHeight = Math.min(Math.round(available * 0.46), available - textNeeds);

    if (frameHeight >= 180) {
      drawPhotoFrame(ctx, await loadImage(spec.photoUrl), area, top, frameHeight, spec.theme, photoTransform(spec));
      headerBottom = top + frameHeight;
    }
  }

  if (spec.layout === 'TOPICOS') {
    drawTopicos(ctx, spec, area, palette, headerBottom, contentBottom);
  } else if (spec.layout === 'SLIDE') {
    drawSlide(ctx, spec, area, palette, headerBottom, contentBottom);
  } else {
    drawHero(ctx, spec, area, palette, headerBottom, contentBottom);
  }
}

type Area = ReturnType<typeof safeArea>;
type Palette = ReturnType<typeof paletteFor>;

/** Altura que o bloco de texto precisa, usada para dimensionar a foto em destaque. */
function textBlockMinHeight(ctx: CanvasRenderingContext2D, spec: PostSpec, area: Area): number {
  const { headline: hScale, body: bScale, label: lScale } = resolveScale(spec);

  const headlineSize = spec.layout === 'HERO' ? 92 * hScale : 70 * hScale;
  const headlineHeight = measureTextBlock(
    ctx,
    spec.headline,
    `800 ${Math.round(headlineSize)}px ${FONTS.display}`,
    area.innerWidth,
    Math.round(headlineSize * 1.06),
    true,
  );

  const eyebrow = spec.eyebrow.trim() ? Math.round(32 * lScale) + 14 : 0;
  const bodyLine = Math.round(44 * bScale);
  const body = spec.body.trim() ? bodyLine * 2 + 34 : 0; // reserva ao menos duas linhas de apoio

  return eyebrow + headlineHeight + body + 72;
}

/** Zoom e deslocamento escolhidos no editor, com valores seguros. */
function photoTransform(spec: PostSpec): PhotoTransform {
  return {
    zoom: spec.photoZoom ?? 1,
    offsetX: spec.photoOffsetX ?? 0,
    offsetY: spec.photoOffsetY ?? 0,
  };
}

/** Quadro da foto em destaque, com corte central e a borda vermelha da marca. */
function drawPhotoFrame(
  ctx: CanvasRenderingContext2D,
  image: HTMLImageElement,
  area: Area,
  top: number,
  height: number,
  theme: 'DARK' | 'LIGHT',
  transform: PhotoTransform = {},
) {
  const palette = paletteFor(theme);
  const width = area.innerWidth;
  const x = area.side;

  ctx.save();
  roundedRect(ctx, x, top, width, height, 14);
  ctx.clip();

  drawCoverIn(ctx, image, x, top, width, height, {
    ...transform,
    backdrop: theme === 'DARK' ? 'rgba(12, 14, 17, 0.92)' : 'rgba(244, 244, 246, 0.95)',
  });

  // Leve degradê na base do quadro, para encostar no texto sem corte seco.
  const fade = ctx.createLinearGradient(0, top + height - 90, 0, top + height);
  fade.addColorStop(0, 'rgba(12, 14, 17, 0)');
  fade.addColorStop(1, theme === 'DARK' ? 'rgba(12, 14, 17, 0.55)' : 'rgba(255, 255, 255, 0.45)');
  ctx.fillStyle = fade;
  ctx.fillRect(x, top + height - 90, width, 90);
  ctx.restore();

  ctx.save();
  roundedRect(ctx, x, top, width, height, 14);
  ctx.strokeStyle = palette.accent;
  ctx.lineWidth = 3;
  ctx.stroke();
  ctx.restore();
}

function drawHero(
  ctx: CanvasRenderingContext2D,
  spec: PostSpec,
  area: Area,
  palette: Palette,
  headerBottom: number,
  contentBottom: number,
) {
  const { headline: hScale, body: bScale, label: lScale } = resolveScale(spec);
  const bodyFont = `500 ${Math.round(30 * bScale)}px ${FONTS.body}`;
  const bodyLine = Math.round(44 * bScale);

  const headlineFont = (size: number) => `800 ${size}px ${FONTS.display}`;
  const size = fitFontSize(
    ctx,
    spec.headline.toUpperCase(),
    headlineFont,
    area.innerWidth,
    Math.round(92 * hScale),
    34,
    4,
  );
  const lineHeight = Math.round(size * 1.06);

  const eyebrowHeight = spec.eyebrow.trim() ? Math.round(32 * lScale) + 14 : 0;
  const headlineHeight = measureTextBlock(ctx, spec.headline, headlineFont(size), area.innerWidth, lineHeight, true);

  // O apoio só pode ocupar o que sobra até a faixa de CTA — sem isso ele invade o rodapé
  // quando há foto em destaque ou textos aumentados.
  const top = headerBottom + 48;
  const freeForBody = contentBottom - top - eyebrowHeight - headlineHeight - 34 - 24;
  const bodyLines = Math.max(0, Math.min(4, Math.floor(freeForBody / bodyLine)));
  const bodyHeight = bodyLines === 0
    ? 0
    : Math.min(
        measureTextBlock(ctx, spec.body, bodyFont, area.innerWidth, bodyLine),
        bodyLines * bodyLine,
      );
  const total = eyebrowHeight + headlineHeight + (bodyHeight ? bodyHeight + 34 : 0) + 24;

  const { x, align } = textAnchor(spec, area);
  let y = placeBlock(spec, top, contentBottom, total);

  y += drawEyebrow(ctx, spec.eyebrow, x, y, area.innerWidth, spec.theme, lScale, align);
  if (spec.eyebrow.trim()) y += 14;

  y += drawTextBlock(ctx, {
    text: spec.headline,
    x,
    y,
    maxWidth: area.innerWidth,
    font: headlineFont(size),
    color: palette.text,
    lineHeight,
    align,
    uppercase: true,
    maxLines: 4,
  });

  // Régua vermelha de assinatura.
  y += 18;
  const ruleX = align === 'center' ? x - 90 : area.side;
  ctx.save();
  const gradient = ctx.createLinearGradient(ruleX, y, ruleX + 180, y);
  gradient.addColorStop(0, palette.accent);
  gradient.addColorStop(1, 'rgba(255, 83, 84, 0)');
  ctx.fillStyle = gradient;
  ctx.fillRect(ruleX, y, 180, 5);
  ctx.restore();
  y += 29;

  drawTextBlock(ctx, {
    text: spec.body,
    x,
    y,
    maxWidth: area.innerWidth,
    font: bodyFont,
    color: palette.muted,
    lineHeight: bodyLine,
    align,
    maxLines: bodyLines,
  });
}

/**
 * Posição do bloco de texto: ancora em cima/meio/embaixo da área útil,
 * com um deslocamento fino, sempre preso dentro das margens seguras.
 */
function placeBlock(spec: PostSpec, top: number, bottom: number, blockHeight: number): number {
  const anchor = spec.anchor ?? 'BOTTOM';
  const free = Math.max(0, bottom - top - blockHeight);

  const base = anchor === 'TOP' ? top : anchor === 'MIDDLE' ? top + free / 2 : top + free;
  const shifted = base + (spec.offsetY ?? 0);

  return Math.min(Math.max(shifted, top), Math.max(top, bottom - blockHeight));
}

/** x e alinhamento do texto conforme o alinhamento horizontal escolhido. */
function textAnchor(spec: PostSpec, area: Area): { x: number; align: CanvasTextAlign } {
  return (spec.align ?? 'LEFT') === 'CENTER'
    ? { x: area.side + area.innerWidth / 2, align: 'center' }
    : { x: area.side, align: 'left' };
}

/** Escalas com valores seguros, inclusive para specs antigos sem o campo. */
function resolveScale(spec: PostSpec) {
  const clamp = (value: number | undefined) => Math.min(1.6, Math.max(0.6, value ?? 1));
  return {
    headline: clamp(spec.scale?.headline),
    body: clamp(spec.scale?.body),
    label: clamp(spec.scale?.label),
  };
}

function drawTopicos(
  ctx: CanvasRenderingContext2D,
  spec: PostSpec,
  area: Area,
  palette: Palette,
  headerBottom: number,
  contentBottom: number,
) {
  const { headline: hScale, body: bScale, label: lScale } = resolveScale(spec);
  const { x, align } = textAnchor(spec, area);
  const items = spec.bullets.filter((item) => item.trim());
  const headerHeight = measureTextBlock(
    ctx,
    spec.headline,
    `800 ${Math.round(70 * hScale)}px ${FONTS.display}`,
    area.innerWidth,
    Math.round(70 * hScale * 1.08),
    true,
  ) + (spec.eyebrow.trim() ? Math.round(32 * lScale) + 12 : 0) + 34;
  const listHeight = items.length ? items.length * 100 : 0;

  let y = placeBlock(spec, headerBottom + 56, contentBottom, headerHeight + listHeight);

  y += drawEyebrow(ctx, spec.eyebrow, x, y, area.innerWidth, spec.theme, lScale, align);
  if (spec.eyebrow.trim()) y += 12;

  const headlineFont = (size: number) => `800 ${size}px ${FONTS.display}`;
  const size = fitFontSize(
    ctx,
    spec.headline.toUpperCase(),
    headlineFont,
    area.innerWidth,
    Math.round(70 * hScale),
    30,
    3,
  );
  y += drawTextBlock(ctx, {
    text: spec.headline,
    x,
    y,
    maxWidth: area.innerWidth,
    font: headlineFont(size),
    color: palette.text,
    lineHeight: Math.round(size * 1.08),
    align,
    uppercase: true,
    maxLines: 3,
  });

  y += 34;

  if (!items.length) return;

  const available = contentBottom - y - 8;
  const gap = 16;
  const cardHeight = Math.min(
    Math.round(132 * bScale),
    Math.max(Math.round(84 * bScale), (available - gap * (items.length - 1)) / items.length),
  );

  items.forEach((item, index) => {
    const top = y + index * (cardHeight + gap);
    if (top + cardHeight > contentBottom) return;

    ctx.save();
    ctx.fillStyle = palette.panel;
    roundedRect(ctx, area.side, top, area.innerWidth, cardHeight, 12);
    ctx.fill();
    ctx.strokeStyle = spec.theme === 'DARK' ? 'rgba(92, 64, 62, 0.6)' : 'rgba(17, 19, 23, 0.12)';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.restore();

    // Número à esquerda.
    const boxSize = 54;
    const boxX = area.side + 26;
    const boxY = top + (cardHeight - boxSize) / 2;
    ctx.save();
    ctx.fillStyle = spec.theme === 'DARK' ? 'rgba(255, 83, 84, 0.22)' : 'rgba(196, 26, 36, 0.14)';
    roundedRect(ctx, boxX, boxY, boxSize, boxSize, 8);
    ctx.fill();
    ctx.fillStyle = palette.accent;
    ctx.font = `700 26px ${FONTS.mono}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(String(index + 1).padStart(2, '0'), boxX + boxSize / 2, boxY + boxSize / 2 + 1);
    ctx.restore();

    const textX = boxX + boxSize + 24;
    const textWidth = area.side + area.innerWidth - textX - 26;
    const [title, ...rest] = item.split('|');
    const detail = rest.join('|').trim();

    const titleHeight = drawTextBlock(ctx, {
      text: title.trim(),
      x: textX,
      y: top + (detail ? cardHeight / 2 - 38 : cardHeight / 2 - 17),
      maxWidth: textWidth,
      font: `700 ${Math.round(30 * bScale)}px ${FONTS.body}`,
      color: palette.text,
      lineHeight: Math.round(36 * bScale),
      maxLines: 2,
    });

    if (detail) {
      drawTextBlock(ctx, {
        text: detail,
        x: textX,
        y: top + cardHeight / 2 - 38 + titleHeight + 6,
        maxWidth: textWidth,
        font: `500 ${Math.round(24 * bScale)}px ${FONTS.body}`,
        color: palette.muted,
        lineHeight: Math.round(30 * bScale),
        maxLines: 2,
      });
    }
  });
}

function drawSlide(
  ctx: CanvasRenderingContext2D,
  spec: PostSpec,
  area: Area,
  palette: Palette,
  headerBottom: number,
  contentBottom: number,
) {
  const { headline: hScale, body: bScale, label: lScale } = resolveScale(spec);
  const { x, align } = textAnchor(spec, area);
  const estimated = measureTextBlock(
    ctx,
    spec.headline,
    `800 ${Math.round(76 * hScale)}px ${FONTS.display}`,
    area.innerWidth,
    Math.round(76 * hScale * 1.08),
    true,
  ) + (spec.eyebrow.trim() ? Math.round(32 * lScale) + 16 : 0) + 36
    + measureTextBlock(ctx, spec.body, `500 ${Math.round(29 * bScale)}px ${FONTS.body}`, area.innerWidth - 64, Math.round(42 * bScale))
    + (spec.body.trim() ? 64 : 0);

  let y = placeBlock(spec, headerBottom + 64, contentBottom, estimated);

  y += drawEyebrow(ctx, spec.eyebrow, x, y, area.innerWidth, spec.theme, lScale, align);
  if (spec.eyebrow.trim()) y += 16;

  const headlineFont = (size: number) => `800 ${size}px ${FONTS.display}`;
  const size = fitFontSize(
    ctx,
    spec.headline.toUpperCase(),
    headlineFont,
    area.innerWidth,
    Math.round(76 * hScale),
    30,
    4,
  );
  y += drawTextBlock(ctx, {
    text: spec.headline,
    x,
    y,
    maxWidth: area.innerWidth,
    font: headlineFont(size),
    color: palette.text,
    lineHeight: Math.round(size * 1.08),
    align,
    uppercase: true,
    maxLines: 4,
  });

  y += 36;

  if (spec.body.trim()) {
    const padding = 32;
    const bodyFont = `500 ${Math.round(29 * bScale)}px ${FONTS.body}`;
    const bodyLine = Math.round(42 * bScale);
    const textWidth = area.innerWidth - padding * 2;
    const textHeight = measureTextBlock(ctx, spec.body, bodyFont, textWidth, bodyLine);
    const boxHeight = Math.min(textHeight + padding * 2, contentBottom - y);

    ctx.save();
    ctx.fillStyle = palette.panel;
    roundedRect(ctx, area.side, y, area.innerWidth, boxHeight, 14);
    ctx.fill();
    ctx.strokeStyle = spec.theme === 'DARK' ? 'rgba(92, 64, 62, 0.6)' : 'rgba(17, 19, 23, 0.12)';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.restore();

    drawTextBlock(ctx, {
      text: spec.body,
      x: area.side + padding,
      y: y + padding,
      maxWidth: textWidth,
      font: bodyFont,
      color: palette.muted,
      lineHeight: bodyLine,
      maxLines: Math.max(1, Math.floor((boxHeight - padding * 2) / bodyLine)),
    });
  }
}
