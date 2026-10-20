import { useEffect, useRef, useState } from 'react';
import { Bot, Send, Sparkles, Save, User } from 'lucide-react';
import { aiApi, postsApi, type ChatMessage, type PostDraft } from '../api/services';
import { describeError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ErrorState, PageHeader } from '../components/primitives';

const SUGGESTIONS = [
  'Me ajude a planejar um post sobre backup corporativo para pequenas empresas.',
  'Qual ângulo ainda não exploramos sobre manutenção preventiva?',
  'Reescreva a legenda do post 03 com um gancho mais forte.',
];

/** Próximo código livre no planner: 01, 02, 03… */
function nextCode(existing: string[]): string {
  const used = new Set(existing);
  for (let i = 1; i < 100; i += 1) {
    const code = String(i).padStart(2, '0');
    if (!used.has(code)) return code;
  }
  return String(Date.now()).slice(-4);
}

export function AiStudioPage() {
  const { session } = useAuth();
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [model, setModel] = useState('');
  // Conversa e briefing moram aqui porque os dois painéis precisam dos mesmos dados:
  // o chat empresta o texto para o briefing, e a conversa vira posts no painel de baixo.
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [brief, setBrief] = useState('');
  const [daConversa, setDaConversa] = useState<PostDraft[] | null>(null);

  useEffect(() => {
    aiApi
      .status()
      .then((status) => {
        setEnabled(status.enabled);
        setModel(status.model);
      })
      .catch(() => setEnabled(false));
  }, []);

  return (
    <>
      <PageHeader
        eyebrow="CONTENT COPILOT"
        title="Assistente de Conteúdo"
        description="Converse com o Claude para destravar ideias, revisar ângulos e gerar posts já no formato do planner."
        actions={
          enabled !== null && (
            <span className="label-code rounded bg-surface-low px-3 py-1.5 text-content-muted">
              {enabled ? `MODELO: ${model}` : 'ASSISTENTE DESATIVADO'}
            </span>
          )
        }
      />

      <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-10 md:px-8">
        {enabled === false && (
          <ErrorState message="Defina a variável ANTHROPIC_API_KEY no backend para habilitar o assistente." />
        )}

        {!session && (
          <div className="panel p-4 text-sm text-content-muted">
            Entre com sua conta pelo botão no topo: as chamadas ao assistente exigem token de acesso.
          </div>
        )}

        {enabled && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
            <div className="lg:col-span-7">
              <ChatPanel
                messages={messages}
                setMessages={setMessages}
                onUsarComoBriefing={setBrief}
                onPostsGerados={setDaConversa}
              />
            </div>
            <div className="lg:col-span-5">
              <DraftPanel brief={brief} setBrief={setBrief} />
            </div>
          </div>
        )}

        {daConversa && <PostsDaConversa drafts={daConversa} onFechar={() => setDaConversa(null)} />}
      </div>
    </>
  );
}

function ChatPanel({
  messages,
  setMessages,
  onUsarComoBriefing,
  onPostsGerados,
}: {
  messages: ChatMessage[];
  setMessages: (valor: ChatMessage[]) => void;
  onUsarComoBriefing: (texto: string) => void;
  onPostsGerados: (drafts: PostDraft[]) => void;
}) {
  const [input, setInput] = useState('');
  const [gerando, setGerando] = useState(false);
  const [quantidade, setQuantidade] = useState(3);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  async function send(text: string) {
    const content = text.trim();
    if (!content || sending) return;

    const history: ChatMessage[] = [...messages, { role: 'user', content }];
    setMessages(history);
    setInput('');
    setSending(true);
    setError(null);

    try {
      const reply = await aiApi.chat(history);
      setMessages([...history, { role: 'assistant', content: reply }]);
    } catch (err) {
      setError(describeError(err));
      // Mantém a pergunta na caixa para o usuário reenviar sem redigitar.
      setInput(content);
      setMessages(messages);
    } finally {
      setSending(false);
    }
  }

  async function transformarEmPosts() {
    if (messages.length === 0 || gerando) return;
    setGerando(true);
    setError(null);
    try {
      onPostsGerados(await aiApi.draftsFromChat(messages, quantidade));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setGerando(false);
    }
  }

  return (
    <section className="panel flex h-[32rem] flex-col">
      <header className="flex flex-wrap items-center gap-2 border-b border-surface-high px-4 py-3">
        <Bot size={16} className="text-brand" />
        <span className="label-code text-brand-soft">Conversa</span>

        {messages.length > 0 && (
          <div className="ml-auto flex items-center gap-2">
            <select
              value={quantidade}
              onChange={(event) => setQuantidade(Number(event.target.value))}
              className="rounded bg-surface-lowest px-2 py-1 font-mono text-[11px] outline-none"
            >
              {[1, 2, 3, 4, 5].map((n) => (
                <option key={n} value={n}>{n} post{n > 1 ? 's' : ''}</option>
              ))}
            </select>
            <button type="button" className="btn-ghost" onClick={transformarEmPosts} disabled={gerando}>
              <Sparkles size={12} />
              {gerando ? 'Lendo a conversa…' : 'Transformar em posts'}
            </button>
          </div>
        )}
      </header>

      <div className="flex-1 space-y-4 overflow-y-auto p-4">
        {messages.length === 0 && (
          <div className="flex flex-col gap-2">
            <p className="text-sm text-content-muted">Comece por uma destas:</p>
            {SUGGESTIONS.map((suggestion) => (
              <button
                key={suggestion}
                type="button"
                className="rounded border border-surface-high bg-surface-lowest p-3 text-left text-sm text-content-muted transition hover:border-brand hover:text-content"
                onClick={() => send(suggestion)}
              >
                {suggestion}
              </button>
            ))}
          </div>
        )}

        {messages.map((message, index) => (
          <div key={index} className="flex gap-3">
            <div
              className={`flex h-7 w-7 shrink-0 items-center justify-center rounded ${
                message.role === 'user' ? 'bg-surface-high text-content-muted' : 'bg-brand text-brand-ink'
              }`}
            >
              {message.role === 'user' ? <User size={13} /> : <Bot size={13} />}
            </div>
            <div className="flex min-w-0 flex-col gap-1">
              <p className="whitespace-pre-line text-sm leading-relaxed">{message.content}</p>

              {/* A conversa e o gerador são chamadas separadas: este botão é a ponte. */}
              {message.role === 'assistant' && (
                <button
                  type="button"
                  className="self-start text-[11px] uppercase tracking-wide text-content-muted transition hover:text-brand-soft"
                  onClick={() => onUsarComoBriefing(message.content.slice(0, LIMITE_BRIEFING))}
                >
                  ↗ usar como briefing
                </button>
              )}
            </div>
          </div>
        ))}

        {sending && <p className="label-code text-content-muted">Claude está escrevendo…</p>}
        {error && <p className="text-sm text-brand-soft">{error}</p>}
        <div ref={endRef} />
      </div>

      <form
        className="flex items-center gap-2 border-t border-surface-high p-3"
        onSubmit={(event) => {
          event.preventDefault();
          void send(input);
        }}
      >
        <input
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="Peça uma ideia, um ângulo ou uma legenda…"
          className="flex-1 rounded bg-surface-lowest px-3 py-2 text-sm outline-none placeholder:text-content-muted/60"
        />
        <button type="submit" className="btn-primary" disabled={sending || !input.trim()}>
          <Send size={14} />
          Enviar
        </button>
      </form>
    </section>
  );
}


/**
 * O modelo responde em texto livre: "Carrossel", "B2B / Gestores de PMEs", "Reels (vídeo)".
 * O planner guarda valores exatos. Aqui o texto é reduzido ao valor correspondente e, quando
 * nada bate, vale o que você escolheu nos seletores ao lado do briefing.
 */
function normalizar<T extends string>(bruto: string | undefined, validos: readonly T[], padrao: T): T {
  const texto = (bruto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toUpperCase();

  const direto = validos.find((valor) => texto.includes(valor));
  if (direto) return direto;

  // O modelo às vezes responde em inglês ("Carousel", "Static") ou com sinônimos.
  const sinonimo = SINONIMOS.find(([termo]) => texto.includes(termo));
  if (sinonimo) {
    const correspondente = validos.find((valor) => valor === sinonimo[1]);
    if (correspondente) return correspondente;
  }

  return padrao;
}

/** Palavra que o modelo pode usar → valor que o planner guarda. */
const SINONIMOS: ReadonlyArray<readonly [string, string]> = [
  ['CAROUSEL', 'CARROSSEL'],
  ['CARUSSEL', 'CARROSSEL'],
  ['STATIC', 'ESTATICO'],
  ['IMAGEM UNICA', 'ESTATICO'],
  ['POST UNICO', 'ESTATICO'],
  ['VIDEO', 'REELS'],
  ['HYBRID', 'HIBRIDO'],
  ['AMBOS', 'HIBRIDO'],
];

/** Mesmo teto do backend: o briefing descreve o post, não é o post. */
const LIMITE_BRIEFING = 2000;

const FORMATOS = ['CARROSSEL', 'ESTATICO', 'REELS'] as const;
const PUBLICOS = ['HIBRIDO', 'B2B', 'B2C'] as const;


/**
 * Grava o rascunho como post em rascunho e devolve o código criado.
 * Vive fora dos componentes porque tanto o gerador quanto a lista vinda da conversa salvam.
 */
async function salvarNoPlanner(draft: PostDraft, publico: string, formato: string) {
  const existing = await postsApi.list();
  const created = await postsApi.create({
    code: nextCode(existing.map((post) => post.code)),
    title: draft.title,
    brandPillar: draft.brandPillar,
    format: normalizar(draft.format, FORMATOS, formato as (typeof FORMATOS)[number]),
    audience: normalizar(draft.audience, PUBLICOS, publico as (typeof PUBLICOS)[number]),
    status: 'RASCUNHO',
    slideCount: draft.slideCount > 0 ? draft.slideCount : 1,
    summary: draft.summary,
    caption: draft.caption,
    hashtags: draft.hashtags,
    ctaLabel: draft.ctaLabel,
  });
  return created.code;
}

function DraftPanel({ brief, setBrief }: { brief: string; setBrief: (valor: string) => void }) {
  const [audience, setAudience] = useState('B2B');
  const [format, setFormat] = useState('CARROSSEL');
  const [draft, setDraft] = useState<PostDraft | null>(null);
  const [fromCache, setFromCache] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function generate() {
    if (!brief.trim() || busy) return;
    setBusy(true);
    setError(null);
    setStatus(null);
    try {
      const result = await aiApi.draft(brief, audience, format);
      setDraft(result.draft);
      setFromCache(result.cached);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  async function saveToPlanner() {
    if (!draft || busy) return;
    setBusy(true);
    setError(null);
    try {
      const code = await salvarNoPlanner(draft, audience, format);
      setStatus(`Salvo no planner como post ${code} (rascunho).`);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel flex flex-col gap-4 p-4">
      <header className="flex items-center gap-2">
        <Sparkles size={16} className="text-brand" />
        <span className="label-code text-brand-soft">Gerar post completo</span>
      </header>

      <textarea
        value={brief}
        onChange={(event) => setBrief(event.target.value)}
        rows={4}
        maxLength={LIMITE_BRIEFING}
        placeholder="Briefing: sobre o que é o post, para quem e qual resultado você quer."
        className="rounded bg-surface-lowest p-3 text-sm outline-none placeholder:text-content-muted/60"
      />
      <p className="label-code text-right text-content-muted">
        {brief.length}/{LIMITE_BRIEFING} — descreva o tema em poucas linhas; o texto pronto sai daqui
      </p>

      <div className="grid grid-cols-2 gap-2">
        <select
          value={audience}
          onChange={(event) => setAudience(event.target.value)}
          className="rounded bg-surface-lowest px-3 py-2 font-mono text-xs"
        >
          <option value="B2B">B2B</option>
          <option value="B2C">B2C</option>
          <option value="HIBRIDO">Híbrido</option>
        </select>
        <select
          value={format}
          onChange={(event) => setFormat(event.target.value)}
          className="rounded bg-surface-lowest px-3 py-2 font-mono text-xs"
        >
          <option value="CARROSSEL">Carrossel</option>
          <option value="ESTATICO">Estático</option>
          <option value="REELS">Reels</option>
        </select>
      </div>

      <button type="button" className="btn-primary justify-center" onClick={generate} disabled={busy || !brief.trim()}>
        <Sparkles size={14} />
        {busy ? 'Gerando…' : 'Gerar rascunho'}
      </button>

      {error && <p className="text-sm text-brand-soft">{error}</p>}
      {status && <p className="text-sm text-content-muted">{status}</p>}

      {draft && (
        <article className="flex flex-col gap-3 rounded bg-surface-lowest p-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="label-code text-brand-soft">{draft.brandPillar}</span>
              {fromCache && (
                <span className="label-code rounded bg-surface px-2 py-0.5 text-content-muted">
                  do cache • sem nova chamada à API
                </span>
              )}
            </div>
            <h3 className="font-display text-lg leading-tight">{draft.title}</h3>
          </div>
          <p className="text-sm text-content-muted">{draft.summary}</p>
          <p className="whitespace-pre-line rounded bg-surface p-3 text-xs leading-relaxed">{draft.caption}</p>
          <p className="font-mono text-[11px] text-content-variant">{draft.hashtags}</p>
          <div className="flex items-center justify-between">
            <span className="label-code text-content-muted">
              {draft.format} • {draft.audience} • {draft.slideCount} slide(s)
            </span>
            <button type="button" className="btn-ghost" onClick={saveToPlanner} disabled={busy}>
              <Save size={12} />
              Salvar no planner
            </button>
          </div>
        </article>
      )}
    </section>
  );
}

/**
 * Os posts que saíram da conversa. Cada um é salvo separadamente: o modelo pode acertar
 * três de quatro, e o que não serve você simplesmente não salva.
 *
 * <p>Nada aqui é gravado: o estado vive só na memória desta tela. Sair da página descarta a
 * conversa, o briefing e os posts que você não salvou — de propósito, para o planner não
 * encher de rascunho que ninguém aprovou.</p>
 */
function PostsDaConversa({ drafts, onFechar }: { drafts: PostDraft[]; onFechar: () => void }) {
  const [salvos, setSalvos] = useState<Record<number, string>>({});
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState<number | null>(null);

  const pendentes = drafts.length - Object.keys(salvos).length;

  async function salvar(draft: PostDraft, indice: number) {
    if (salvando !== null || salvos[indice]) return;
    setSalvando(indice);
    setErro(null);
    try {
      // A reserva tem de ser um valor que o planner aceita: o texto do modelo é o que
      // pode estar errado, então ele não serve como plano B de si mesmo.
      const code = await salvarNoPlanner(draft, 'B2B', 'CARROSSEL');
      setSalvos((atual) => ({ ...atual, [indice]: code }));
    } catch (err) {
      setErro(describeError(err));
    } finally {
      setSalvando(null);
    }
  }

  return (
    <section className="panel flex flex-col gap-4 p-4">
      <header className="flex items-center gap-2">
        <Sparkles size={16} className="text-brand" />
        <span className="label-code text-brand-soft">
          {drafts.length} post{drafts.length > 1 ? 's' : ''} desta conversa
        </span>
        <button type="button" className="btn-ghost ml-auto" onClick={onFechar}>
          Descartar
        </button>
      </header>

      {pendentes > 0 && (
        <p className="text-xs text-content-muted">
          {pendentes === drafts.length
            ? 'Nenhum destes foi salvo ainda.'
            : `${pendentes} de ${drafts.length} ainda não foram salvos.`}{' '}
          Eles somem ao sair desta tela — salve os que quiser manter.
        </p>
      )}

      {erro && <p className="text-sm text-brand-soft">{erro}</p>}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {drafts.map((draft, indice) => (
          <article key={indice} className="flex flex-col gap-2 rounded bg-surface-lowest p-3">
            <span className="label-code text-content-muted">{draft.brandPillar}</span>
            <h3 className="font-display text-base leading-tight">{draft.title}</h3>
            <p className="text-xs text-content-muted">{draft.summary}</p>

            <p className="max-h-40 overflow-y-auto whitespace-pre-line rounded bg-surface p-2 text-xs leading-relaxed">
              {draft.caption}
            </p>

            <p className="font-mono text-[11px] text-brand-soft">{draft.hashtags}</p>

            <div className="mt-auto flex items-center gap-2 pt-2">
              <span className="label-code text-content-muted">
                {draft.format} • {draft.audience} • {draft.slideCount} slide(s)
              </span>
              <button
                type="button"
                className="btn-primary ml-auto"
                onClick={() => salvar(draft, indice)}
                disabled={salvando !== null || Boolean(salvos[indice])}
              >
                {salvos[indice] ? `Post ${salvos[indice]}` : salvando === indice ? 'Salvando…' : 'Salvar'}
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
