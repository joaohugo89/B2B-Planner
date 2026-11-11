import { api } from './client';
import type {
  CopyEntry,
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

/** Próximo código livre no planner: 01, 02, 03… Usado por quem cria post (editor e assistente). */
export function proximoCodigo(existentes: string[]): string {
  const usados = new Set(existentes);
  for (let i = 1; i < 100; i += 1) {
    const code = String(i).padStart(2, '0');
    if (!usados.has(code)) return code;
  }
  return String(Date.now()).slice(-4);
}

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
  /** Lê a conversa e devolve vários posts prontos, um por ideia. */
  async draftsFromChat(messages: ChatMessage[], count: number) {
    const { data } = await api.post<PostDraft[]>(
      '/ai/drafts',
      { messages, count },
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

interface AiUsageEntry {
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

interface ContentCheck {
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

// ---- Video Studio ----------------------------------------------------------

export type VideoStep = 'VOZ' | 'LIPSYNC' | 'LEGENDAS' | 'RENDER';
type VideoJobState = 'NA_FILA' | 'EXECUTANDO' | 'CONCLUIDO' | 'ERRO' | 'CANCELADO';
/** Os arquivos de um projeto; só os dois primeiros são enviados, o resto nasce de um job. */
type VideoFileKind = 'BASE' | 'AUDIO' | 'LIPSYNC' | 'SUBTITLES' | 'FINAL';

export interface VideoJob {
  id: number;
  step: VideoStep;
  state: VideoJobState;
  /** 0 a 100 enquanto a etapa roda e o motor informa; null fora disso. */
  progress: number | null;
  error: string | null;
  log: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface VideoProject {
  id: number;
  title: string;
  script: string | null;
  postId: number | null;
  hasBase: boolean;
  hasAudio: boolean;
  hasLipsync: boolean;
  hasSubtitles: boolean;
  hasFinal: boolean;
  updatedAt: string;
  jobs: VideoJob[];
}

type VideoProjectInput = { title: string; script: string | null; postId: number | null };

export const videoApi = {
  /** Quais etapas têm motor instalado no servidor. */
  async engines() {
    const { data } = await api.get<Record<VideoStep, boolean>>('/video/engines');
    return data;
  },
  async list(postId?: number) {
    const { data } = await api.get<VideoProject[]>('/video/projects', { params: { postId } });
    return data;
  },
  async get(id: number) {
    const { data } = await api.get<VideoProject>(`/video/projects/${id}`);
    return data;
  },
  async create(payload: VideoProjectInput) {
    const { data } = await api.post<VideoProject>('/video/projects', payload);
    return data;
  },
  async update(id: number, payload: VideoProjectInput) {
    const { data } = await api.put<VideoProject>(`/video/projects/${id}`, payload);
    return data;
  },
  async remove(id: number) {
    await api.delete(`/video/projects/${id}`);
  },
  async script(id: number, topic: string) {
    const { data } = await api.post<VideoProject>(`/video/projects/${id}/script`, { topic });
    return data;
  },
  async upload(id: number, kind: 'BASE' | 'AUDIO', file: File) {
    const body = new FormData();
    body.append('file', file);
    // Vídeo é grande: sem o timeout padrão, que cortaria o envio no meio.
    const { data } = await api.post<VideoProject>(`/video/projects/${id}/files/${kind}`, body, { timeout: 0 });
    return data;
  },
  /**
   * O arquivo vem como blob porque a rota exige o token, e um <video src> não manda
   * cabeçalho. Quem chama cria o endereço blob: e o revoga ao sair.
   */
  async file(id: number, kind: VideoFileKind) {
    const { data } = await api.get<Blob>(`/video/projects/${id}/files/${kind}`, { responseType: 'blob', timeout: 0 });
    return data;
  },
  async subtitles(id: number) {
    const { data } = await api.get<{ text: string }>(`/video/projects/${id}/subtitles`);
    return data.text;
  },
  async saveSubtitles(id: number, text: string) {
    const { data } = await api.put<VideoProject>(`/video/projects/${id}/subtitles`, { text });
    return data;
  },
  /** Roda em sequência todas as etapas que têm motor: do roteiro ao MP4. */
  async produce(id: number) {
    const { data } = await api.post<VideoJob>(`/video/projects/${id}/produce`);
    return data;
  },
  async hasAvatar() {
    const { data } = await api.get<{ saved: boolean }>('/video/avatar');
    return data.saved;
  },
  /** Guarda o vídeo-base deste projeto como avatar: os próximos projetos já nascem com ele. */
  async saveAvatar(id: number) {
    await api.post(`/video/projects/${id}/avatar`);
  },
  async submit(id: number, step: VideoStep) {
    const { data } = await api.post<VideoJob>(`/video/projects/${id}/jobs`, { step });
    return data;
  },
  async cancel(jobId: number) {
    await api.post(`/video/jobs/${jobId}/cancel`);
  },
};
