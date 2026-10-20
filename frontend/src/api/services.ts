import { api } from './client';
import type {
  CopyEntry,
  PlannerMetrics,
  Post,
  PostStatus,
  PublishState,
  TemplateAsset,
} from '../types/api';

export const postsApi = {
  async list(params?: { status?: PostStatus; audience?: string }) {
    const { data } = await api.get<Post[]>('/posts', { params });
    return data;
  },
  async metrics() {
    const { data } = await api.get<PlannerMetrics>('/posts/metrics');
    return data;
  },
  async byCode(code: string) {
    const { data } = await api.get<Post>(`/posts/code/${code}`);
    return data;
  },
  async changeStatus(id: number, status: PostStatus) {
    const { data } = await api.patch<Post>(`/posts/${id}/status`, { status });
    return data;
  },
  async create(payload: Partial<Post> & { code: string; title: string }) {
    const { data } = await api.post<Post>('/posts', payload);
    return data;
  },
  async update(id: number, payload: Partial<Post> & { code: string; title: string }) {
    const { data } = await api.put<Post>(`/posts/${id}`, payload);
    return data;
  },
  async replaceSlides(id: number, slides: string[]) {
    const { data } = await api.put<Post>(`/posts/${id}/slides`, { slides });
    return data;
  },
  async remove(id: number) {
    await api.delete(`/posts/${id}`);
  },
};

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface PostDraft {
  title: string;
  brandPillar: string;
  format: string;
  audience: string;
  slideCount: number;
  summary: string;
  caption: string;
  hashtags: string;
  ctaLabel: string;
}

/**
 * Gerar texto leva muito mais que uma consulta comum: modelos gratuitos como o Gemini
 * costumam passar de dois minutos. O limite geral de 15s vale para o resto da API.
 */
const ESPERA_IA = 300_000;

export const aiApi = {
  async status() {
    const { data } = await api.get<{ enabled: boolean; model: string }>('/ai/status');
    return data;
  },
  async chat(messages: ChatMessage[]) {
    const { data } = await api.post<{ reply: string }>('/ai/chat', { messages }, { timeout: ESPERA_IA });
    return data.reply;
  },
  /** `cached` marca o rascunho que veio do cache em memória, sem nova chamada à API. */
  async draft(brief: string, audience?: string, format?: string) {
    const { data } = await api.post<{ draft: PostDraft; cached: boolean }>(
      '/ai/draft',
      { brief, audience, format },
      { timeout: ESPERA_IA },
    );
    return data;
  },
  /** Consumo de tokens do mês e últimas chamadas; exige estar logado. */
  async usage() {
    const { data } = await api.get<AiUsage>('/ai/usage');
    return data;
  },
};

export interface AiUsageEntry {
  at: string;
  operation: string;
  model: string;
  inputTokens: number;
  outputTokens: number;
  effort: string | null;
}

export interface AiUsage {
  monthStart: string;
  calls: number;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  recent: AiUsageEntry[];
}

export const assetsApi = {
  async list(params?: { kind?: string; ratio?: string }) {
    const { data } = await api.get<TemplateAsset[]>('/assets', { params });
    return data;
  },
  async upload(file: File, meta: { code: string; name: string; kind: string; ratio: string; theme: string; composition?: string }) {
    const body = new FormData();
    body.append('file', file);
    // Sem Content-Type manual: o navegador precisa gerar o boundary do multipart.
    const { data } = await api.post<TemplateAsset>('/assets/upload', body, { params: meta });
    return data;
  },
  async remove(id: number) {
    await api.delete(`/assets/${id}`);
  },
};

export const copiesApi = {
  async list(params?: { tone?: string; postCode?: string }) {
    const { data } = await api.get<CopyEntry[]>('/copies', { params });
    return data;
  },
  async create(payload: Partial<CopyEntry> & { headline: string; body: string }) {
    const { data } = await api.post<CopyEntry>('/copies', payload);
    return data;
  },
  async update(id: number, payload: Partial<CopyEntry> & { headline: string; body: string }) {
    const { data } = await api.put<CopyEntry>(`/copies/${id}`, payload);
    return data;
  },
  async remove(id: number) {
    await api.delete(`/copies/${id}`);
  },
};

export interface Tone {
  id: number;
  code: string;
  label: string;
  description: string | null;
  usageCount: number;
}

export const tonesApi = {
  async list() {
    const { data } = await api.get<Tone[]>('/tones');
    return data;
  },
  async create(payload: { code: string; label: string; description?: string }) {
    const { data } = await api.post<Tone>('/tones', payload);
    return data;
  },
  async update(id: number, payload: { code: string; label: string; description?: string }) {
    const { data } = await api.put<Tone>(`/tones/${id}`, payload);
    return data;
  },
  async remove(id: number) {
    await api.delete(`/tones/${id}`);
  },
};

export interface HashtagBlock {
  id: number;
  name: string;
  hashtags: string;
  tagCount: number;
}

/** Blocos de hashtags por categoria, injetados na legenda com um clique. */
export const hashtagBlocksApi = {
  async list() {
    const { data } = await api.get<HashtagBlock[]>('/hashtag-blocks');
    return data;
  },
  async create(payload: { name: string; hashtags: string }) {
    const { data } = await api.post<HashtagBlock>('/hashtag-blocks', payload);
    return data;
  },
  async update(id: number, payload: { name: string; hashtags: string }) {
    const { data } = await api.put<HashtagBlock>(`/hashtag-blocks/${id}`, payload);
    return data;
  },
  async remove(id: number) {
    await api.delete(`/hashtag-blocks/${id}`);
  },
};

export interface InstagramStatus {
  configured: boolean;
  publicUrlReady: boolean;
  connected: boolean;
  username: string | null;
  pageName: string | null;
  tokenExpires: string | null;
  /** null quando não foi testado; false quando o token não responde mais. */
  tokenValid: boolean | null;
}

export interface ContentCheck {
  ok: boolean;
  captionLength: number;
  maxCaption: number;
  hashtags: number;
  maxHashtags: number;
  mentions: number;
  images: number;
  problems: string[];
}

export const instagramApi = {
  async status() {
    const { data } = await api.get<InstagramStatus>('/instagram/status');
    return data;
  },
  /** Pergunta à Meta se o token ainda vale. */
  async health() {
    const { data } = await api.get<InstagramStatus>('/instagram/health');
    return data;
  },
  /** Confere legenda, hashtags e imagens contra os limites do Instagram. */
  async check(postId: number) {
    const { data } = await api.get<ContentCheck>(`/instagram/check/${postId}`);
    return data;
  },
  async authorizeUrl() {
    const { data } = await api.get<{ url: string; state: string }>('/instagram/authorize-url');
    return data;
  },
  async connect(code: string, state: string) {
    const { data } = await api.post<InstagramStatus>('/instagram/connect', { code, state });
    return data;
  },
  async disconnect() {
    await api.delete('/instagram/connection');
  },
  /** Agenda a publicação: responde 202 na hora, com o progresso inicial. */
  async publish(postId: number) {
    const { data } = await api.post<PublishStatus>(`/instagram/publish/${postId}`);
    return data;
  },
  /** Andamento da publicação em segundo plano. */
  async publishStatus(postId: number) {
    const { data } = await api.get<PublishStatus>(`/instagram/publish/${postId}/status`);
    return data;
  },
};

export interface PublishStatus {
  postId: number;
  postCode: string;
  /** PUBLICANDO, CONCLUIDO, ERRO ou NUNCA quando nunca foi tentada. */
  state: 'NUNCA' | PublishState;
  publishing: boolean;
  published: boolean;
  error: string | null;
  instagramMediaId: string | null;
  publishedAt: string | null;
  startedAt: string | null;
  postStatus: PostStatus;
}

export interface IntegrationSettings {
  instagramAppId: string;
  instagramAppSecretDefined: boolean;
  instagramRedirectUri: string;
  instagramPublicBaseUrl: string;
  aiApiKeyDefined: boolean;
  aiProvider: string;
  aiBaseUrl: string;
  aiModel: string;
  aiBrandInstructions: string;
  aiEffort: string;
  instagramReady: boolean;
  aiReady: boolean;
}

export const settingsApi = {
  async getIntegrations() {
    const { data } = await api.get<IntegrationSettings>('/settings/integrations');
    return data;
  },
  async saveIntegrations(payload: Partial<Record<
    | 'instagramAppId'
    | 'instagramAppSecret'
    | 'instagramRedirectUri'
    | 'instagramPublicBaseUrl'
    | 'aiApiKey'
    | 'aiProvider'
    | 'aiBaseUrl'
    | 'aiModel'
    | 'aiBrandInstructions'
    | 'aiEffort',
    string
  >>) {
    const { data } = await api.put<IntegrationSettings>('/settings/integrations', payload);
    return data;
  },
  async getHashtags() {
    const { data } = await api.get<{ hashtags: string }>('/settings/hashtags');
    return data.hashtags;
  },
  async setHashtags(hashtags: string) {
    const { data } = await api.put<{ hashtags: string }>('/settings/hashtags', { hashtags });
    return data.hashtags;
  },
};

export const authApi = {
  async login(email: string, password: string) {
    const { data } = await api.post<{
      accessToken: string;
      refreshToken: string;
      expiresIn: number;
      name: string;
      role: string;
    }>('/auth/login', { email, password });
    return data;
  },

  /** Revoga o refresh token no servidor, para a sessão nao poder ser renovada depois do logout. */
  async logout(refreshToken: string) {
    const { data } = await api.post<{ revoked: boolean }>('/auth/logout', { refreshToken });
    return data;
  },
};
