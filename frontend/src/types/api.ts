export type PostStatus = 'RASCUNHO' | 'AGENDADO' | 'PRONTO_PARA_POSTAR' | 'PUBLICADO';
export type PostFormat = 'ESTATICO' | 'CARROSSEL' | 'REELS';
export type Audience = 'B2B' | 'B2C' | 'HIBRIDO';
/** Código do tom cadastrado no Guia de Copy. */
export type CopyTone = string;
/** ARTE é a imagem final de um post, gerada no editor — não entra no catálogo de templates. */
export type AssetKind = 'BACKGROUND' | 'LOGO' | 'GUIDELINE' | 'ARTE' | 'FOTO';
export type AssetRatio = 'RATIO_4_5' | 'RATIO_1_1' | 'VECTOR';
export type AssetTheme = 'DARK' | 'LIGHT';

export interface Post {
  id: number;
  code: string;
  title: string;
  brandPillar: string | null;
  format: PostFormat;
  status: PostStatus;
  audience: Audience;
  slideCount: number;
  summary: string | null;
  caption: string | null;
  hashtags: string | null;
  coverImageUrl: string | null;
  ctaLabel: string | null;
  scheduledAt: string | null;
  estimatedReach: number | null;
  estimatedSaves: number | null;
  /** Artes do carrossel, na ordem de publicação. */
  slides: string[];
  /** Id da mídia no Instagram, quando já publicado pelo planner. */
  instagramMediaId: string | null;
  publishedAt: string | null;
  /** Progresso da publicação em segundo plano; null quando nunca foi tentada. */
  publishState: PublishState | null;
  publishError: string | null;
}

export type PublishState = 'PUBLICANDO' | 'CONCLUIDO' | 'ERRO';

export interface PlannerMetrics {
  total: number;
  readyToPost: number;
  scheduled: number;
  published: number;
  b2b: number;
  b2c: number;
  carousels: number;
  statics: number;
  estimatedReach: number;
  estimatedSaves: number;
}

export interface TemplateAsset {
  id: number;
  code: string;
  name: string;
  kind: AssetKind;
  ratio: AssetRatio;
  theme: AssetTheme;
  composition: string | null;
  previewUrl: string | null;
  downloadUrl: string | null;
  fileName: string | null;
}

export interface CopyEntry {
  id: number;
  postCode: string | null;
  headline: string;
  tone: CopyTone;
  body: string;
  hashtags: string | null;
  cta: string | null;
  strategy: string | null;
  bestTime: string | null;
  charCount: number;
}
