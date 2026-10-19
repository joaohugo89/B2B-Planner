import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Copy as CopyIcon,
  Download,
  FileText,
  Image as ImageIcon,
  Layers,
  Minus,
  Plus,
  RotateCcw,
  Save,
  Trash2,
  Wand2,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { assetsApi, copiesApi, postsApi } from '../api/services';
import { describeError } from '../api/client';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { ErrorState, LoadingState, PageHeader } from '../components/primitives';
import { DraftsPanel } from '../components/DraftsPanel';
import { PhotoPicker } from '../components/PhotoPicker';
import {
  canvasSize,
  DEFAULT_SCALE,
  LAYOUTS,
  renderPost,
  type LayoutId,
  type PostSpec,
  type Ratio,
  type TextScale,
} from '../editor/layouts';
import { ensureFonts } from '../editor/canvas';
import type { Post, TemplateAsset } from '../types/api';
import { assetUrl } from '../api/assetUrl';

const FIRST_SLIDE: PostSpec = {
  scale: DEFAULT_SCALE,
  photoUrl: null,
  photoMode: 'FRAME',
  photoZoom: 1,
  photoOffsetX: 0,
  photoOffsetY: 0,
  anchor: 'BOTTOM',
  align: 'LEFT',
  offsetY: 0,
  templateUrl: null,
  ratio: 'RATIO_4_5',
  theme: 'DARK',
  layout: 'HERO',
  eyebrow: 'Arquitetura computacional',
  headline: 'Tecnologia para pessoas e empresas',
  body: 'Assistência técnica especializada e sistemas sob medida para o seu negócio não parar.',
  bullets: ['Diagnóstico eletrônico | Laudo com fotos e medições', 'Upgrade cirúrgico | SSD NVMe e RAM dual-channel'],
  cta: 'Fale no WhatsApp',
  badge: 'JH-01',
  showLogo: true,
  scrim: 0.9,
};

/** Próximo código livre no planner: 01, 02, 03… */
function nextPostCode(existing: string[]): string {
  const used = new Set(existing);
  for (let i = 1; i < 100; i += 1) {
    const code = String(i).padStart(2, '0');
    if (!used.has(code)) return code;
  }
  return String(Date.now()).slice(-4);
}

function slugify(text: string): string {
  return (
    text
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '')
      .slice(0, 40) || 'post'
  );
}

/** Desenha um slide fora da tela e devolve o PNG. */
async function renderToBlob(spec: PostSpec): Promise<Blob> {
  const { width, height } = canvasSize(spec.ratio);
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas indisponível neste navegador.');

  await renderPost(ctx, spec);
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Falha ao gerar o PNG.'))), 'image/png');
  });
}

export function PostStudioPage() {
  const assets = useApi(() => assetsApi.list({ kind: 'BACKGROUND' }), []);
  const posts = useApi(() => postsApi.list(), []);
  const copies = useApi(() => copiesApi.list(), []);
  const { session } = useAuth();

  const [slides, setSlides] = useState<PostSpec[]>([FIRST_SLIDE]);
  const [index, setIndex] = useState(0);
  const [renderError, setRenderError] = useState<string | null>(null);
  const [sourcePost, setSourcePost] = useState<number | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [selectedCopy, setSelectedCopy] = useState('');
  const [tab, setTab] = useState<'EDITOR' | 'DRAFTS'>('EDITOR');
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const dragRef = useRef<{ x: number; y: number } | null>(null);
  const [dragging, setDragging] = useState(false);

  const spec = slides[index] ?? slides[0];
  const draftCount = (posts.data ?? []).filter((post) => post.status === 'RASCUNHO').length;
  const isCarousel = slides.length > 1;

  const update = useCallback(
    <K extends keyof PostSpec>(key: K, value: PostSpec[K]) => {
      setSlides((current) => current.map((item, i) => (i === index ? { ...item, [key]: value } : item)));
    },
    [index],
  );

  /** Altera uma escala a partir do valor corrente, para não perder cliques seguidos. */
  const adjustScale = useCallback(
    (key: keyof TextScale, change: number | { set: number }) => {
      setSlides((current) =>
        current.map((item, i) => {
          if (i !== index) return item;
          const base = item.scale[key];
          const next = typeof change === 'number' ? base + change : change.set;
          return { ...item, scale: { ...item.scale, [key]: clampScale(next) } };
        }),
      );
    },
    [index],
  );

  /** Desloca o bloco de texto sem perder cliques seguidos. */
  const nudge = useCallback(
    (delta: number) => {
      setSlides((current) =>
        current.map((item, i) =>
          i === index ? { ...item, offsetY: Math.min(240, Math.max(-240, (item.offsetY ?? 0) + delta)) } : item,
        ),
      );
    },
    [index],
  );

  /** Arrastar a foto na prévia: o movimento do ponteiro vira deslocamento do enquadramento. */
  function startDrag(event: React.PointerEvent<HTMLCanvasElement>) {
    if (!spec.photoUrl) return;
    dragRef.current = { x: event.clientX, y: event.clientY };
    setDragging(true);
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function dragPhoto(event: React.PointerEvent<HTMLCanvasElement>) {
    const start = dragRef.current;
    if (!start || !spec.photoUrl) return;

    const rect = event.currentTarget.getBoundingClientRect();
    // Atravessar a prévia inteira equivale a percorrer todo o deslocamento possível.
    const deltaX = ((event.clientX - start.x) / rect.width) * 2;
    const deltaY = ((event.clientY - start.y) / rect.height) * 2;
    dragRef.current = { x: event.clientX, y: event.clientY };

    const clamp = (value: number) => Math.min(1, Math.max(-1, Number(value.toFixed(3))));
    setSlides((current) =>
      current.map((item, i) =>
        i === index
          ? {
              ...item,
              // Arrastar para a direita mostra o que está à esquerda da foto.
              photoOffsetX: clamp(item.photoOffsetX - deltaX),
              photoOffsetY: clamp(item.photoOffsetY - deltaY),
            }
          : item,
      ),
    );
  }

  function endDrag(event: React.PointerEvent<HTMLCanvasElement>) {
    dragRef.current = null;
    setDragging(false);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  }

  /** Proporção é do carrossel inteiro: muda em todos os slides. */
  function setRatio(ratio: Ratio) {
    setSlides((current) => current.map((item) => ({ ...item, ratio })));
  }

  function addSlide() {
    setSlides((current) => {
      const base = current[index];
      const next = [...current];
      next.splice(index + 1, 0, {
        ...base,
        layout: base.layout === 'HERO' ? 'SLIDE' : base.layout,
        headline: '',
        body: '',
        cta: '',
        badge: `${current.length + 1}/${current.length + 1}`,
      });
      return next;
    });
    setIndex((i) => i + 1);
  }

  function duplicateSlide() {
    setSlides((current) => {
      const next = [...current];
      next.splice(index + 1, 0, { ...current[index] });
      return next;
    });
    setIndex((i) => i + 1);
  }

  function removeSlide() {
    if (slides.length === 1) return;
    setSlides((current) => current.filter((_, i) => i !== index));
    setIndex((i) => Math.max(0, i - 1));
  }

  function moveSlide(direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= slides.length) return;
    setSlides((current) => {
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
    setIndex(target);
  }

  // Templates compatíveis com a proporção escolhida.
  const templates = useMemo(
    () => (assets.data ?? []).filter((asset) => asset.ratio === spec.ratio),
    [assets.data, spec.ratio],
  );

  // Ao trocar de proporção, cai para o primeiro template daquela proporção.
  useEffect(() => {
    if (!templates.length) return;
    const stillValid = templates.some((asset) => asset.downloadUrl === spec.templateUrl);
    if (!stillValid) {
      const first = templates[0];
      setSlides((current) =>
        current.map((item, i) => (i === index ? { ...item, templateUrl: first.downloadUrl, theme: first.theme } : item)),
      );
    }
  }, [templates, spec.templateUrl, index]);

  // Redesenha a prévia a cada mudança.
  useEffect(() => {
    let cancelled = false;
    const canvas = canvasRef.current;
    if (!canvas) return;

    const { width, height } = canvasSize(spec.ratio);
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    (async () => {
      try {
        await ensureFonts();
        if (cancelled) return;
        await renderPost(ctx, spec);
        if (!cancelled) setRenderError(null);
      } catch (err) {
        if (!cancelled) setRenderError(err instanceof Error ? err.message : 'Falha ao desenhar o post.');
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [spec]);

  function downloadBlob(blob: Blob, fileName: string) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.download = fileName;
    link.href = url;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 4000);
  }

  async function downloadCurrent() {
    const blob = await renderToBlob(spec);
    const size = spec.ratio === 'RATIO_1_1' ? '1080x1080' : '1080x1350';
    downloadBlob(blob, `jhcomp-${slugify(slides[0].headline)}-${index + 1}-${size}.png`);
  }

  async function downloadAll() {
    if (busy) return;
    setBusy('download');
    setMessage(null);
    try {
      const name = slugify(slides[0].headline);
      for (let i = 0; i < slides.length; i += 1) {
        const blob = await renderToBlob(slides[i]);
        downloadBlob(blob, `jhcomp-${name}-slide-${String(i + 1).padStart(2, '0')}.png`);
        await new Promise((resolve) => setTimeout(resolve, 350)); // o navegador enfileira downloads
      }
      setMessage(`${slides.length} slides baixados.`);
    } catch (err) {
      setMessage(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  /** Sobe todas as artes e cria (ou atualiza) o post no planner. */
  async function saveToPlanner() {
    if (busy) return;
    setBusy('save');
    setMessage(null);

    try {
      const list = posts.data ?? (await postsApi.list());
      const target = sourcePost ? list.find((item) => item.id === sourcePost) ?? null : null;
      const code = target?.code ?? nextPostCode(list.map((item) => item.code));

      const uploaded: string[] = [];
      for (let i = 0; i < slides.length; i += 1) {
        const blob = await renderToBlob(slides[i]);
        const file = new File([blob], `jhcomp-post-${code}-${i + 1}.png`, { type: 'image/png' });
        const art = await assetsApi.upload(file, {
          code: `ARTE-${code}-${i + 1}-${Date.now().toString().slice(-5)}`,
          name: `Post ${code} — slide ${i + 1}`,
          kind: 'ARTE',
          ratio: slides[i].ratio,
          theme: slides[i].theme,
          composition: slides[i].ratio === 'RATIO_1_1' ? '1080x1080 px' : '1080x1350 px',
        });
        uploaded.push(art.downloadUrl!);
      }

      const payload = {
        code,
        title: slides[0].headline,
        brandPillar: slides[0].eyebrow,
        format: (isCarousel ? 'CARROSSEL' : target?.format ?? 'ESTATICO') as Post['format'],
        status: (target?.status ?? 'RASCUNHO') as Post['status'],
        audience: (target?.audience ?? 'B2B') as Post['audience'],
        slideCount: slides.length,
        summary: slides[0].body,
        caption: target?.caption ?? null,
        hashtags: target?.hashtags ?? null,
        coverImageUrl: uploaded[0],
        ctaLabel: slides[0].cta,
        scheduledAt: target?.scheduledAt ?? null,
        estimatedReach: target?.estimatedReach ?? null,
        estimatedSaves: target?.estimatedSaves ?? null,
      };

      const saved = target ? await postsApi.update(target.id, payload) : await postsApi.create(payload);
      await postsApi.replaceSlides(saved.id, uploaded);

      setSourcePost(saved.id);
      posts.reload();
      setMessage(
        `${isCarousel ? `Carrossel de ${slides.length} slides` : 'Post'} ${saved.code} ${
          target ? 'atualizado' : 'criado como rascunho'
        } — revise em Posts Detalhados.`,
      );
    } catch (err) {
      setMessage(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  /** Traz uma legenda da biblioteca para os textos do slide atual. */
  function loadFromCopy(id: string) {
    const entry = copies.data?.find((item) => String(item.id) === id);
    if (!entry) return;

    const paragraphs = entry.body.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean);
    setSlides((current) =>
      current.map((item, i) =>
        i === index
          ? {
              ...item,
              eyebrow: entry.strategy || item.eyebrow,
              headline: entry.headline,
              body: paragraphs[0] ?? entry.body,
              cta: entry.cta ?? item.cta,
            }
          : item,
      ),
    );
    setMessage(
      paragraphs.length > 1
        ? `Legenda carregada no slide ${index + 1}. Ela tem ${paragraphs.length} parágrafos — use "Dividir em slides" para espalhá-los.`
        : `Legenda carregada no slide ${index + 1}.`,
    );
  }

  /** Cria um slide por parágrafo da legenda, a partir do slide atual. */
  function splitCopyIntoSlides(id: string) {
    const entry = copies.data?.find((item) => String(item.id) === id);
    if (!entry) return;

    const paragraphs = entry.body.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean);
    if (!paragraphs.length) return;

    const base = slides[index];
    const generated: PostSpec[] = paragraphs.map((paragraph, i) => ({
      ...base,
      layout: i === 0 ? base.layout : 'SLIDE',
      eyebrow: i === 0 ? entry.strategy || base.eyebrow : base.eyebrow,
      headline: i === 0 ? entry.headline : paragraph.split(/[.:!?]/)[0].slice(0, 60),
      body: i === 0 ? paragraph : paragraph,
      cta: i === paragraphs.length - 1 ? entry.cta ?? base.cta : '',
      badge: `${i + 1}/${paragraphs.length}`,
    }));

    setSlides(generated);
    setIndex(0);
    setMessage(`Legenda dividida em ${generated.length} slides. Ajuste as manchetes de cada um.`);
  }

  /** Retoma um rascunho: traz os textos do post e passa a atualizar esse mesmo post. */
  function openDraft(post: Post) {
    setSourcePost(post.id);
    setIndex(0);
    setSlides((current) => {
      const base = current[0];
      const count = Math.max(1, post.slides.length || post.slideCount);
      return Array.from({ length: count }, (_, i) => ({
        ...base,
        eyebrow: post.brandPillar ?? base.eyebrow,
        headline: i === 0 ? post.title : '',
        body: i === 0 ? post.summary ?? '' : '',
        cta: i === count - 1 ? post.ctaLabel ?? base.cta : '',
        badge: count > 1 ? `${i + 1}/${count}` : `JH-${post.code}`,
        layout: i === 0 ? base.layout : 'SLIDE',
      }));
    });
    setMessage(
      `Rascunho ${post.code} aberto no editor. As artes serão regeradas quando você salvar de novo.`,
    );
  }

  function loadFromPost(code: string) {
    const post = posts.data?.find((item) => item.code === code);
    if (!post) return;
    setSourcePost(post.id);
    setSlides((current) =>
      current.map((item, i) =>
        i === index
          ? {
              ...item,
              eyebrow: post.brandPillar ?? item.eyebrow,
              headline: post.title,
              body: post.summary ?? '',
              cta: post.ctaLabel ?? item.cta,
              badge: `JH-${post.code}`,
            }
          : item,
      ),
    );
  }

  const { width, height } = canvasSize(spec.ratio);

  return (
    <>
      <PageHeader
        eyebrow="POST COMPOSER // 1080 PX"
        title="Editor de Posts"
        description="Monte posts únicos ou carrosséis sobre os templates da marca, com os textos nas posições padrão, e baixe tudo pronto para publicar."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            {session && (
              <button type="button" className="btn-ghost" onClick={saveToPlanner} disabled={Boolean(busy)}>
                <Save size={12} />
                {busy === 'save' ? 'Salvando…' : sourcePost ? 'Atualizar no planner' : 'Salvar no planner'}
              </button>
            )}
            <button type="button" className="btn-primary" onClick={isCarousel ? downloadAll : downloadCurrent} disabled={Boolean(busy)}>
              <Download size={14} />
              {isCarousel ? `Baixar ${slides.length} slides` : 'Baixar PNG'}
            </button>
          </div>
        }
      />

      <div className="mx-auto max-w-7xl px-4 pt-6 md:px-8">
        <div className="flex items-center gap-1 rounded-lg bg-surface-low p-1">
          {([
            { id: 'EDITOR' as const, label: 'Editor' },
            { id: 'DRAFTS' as const, label: `Rascunhos${draftCount ? ` (${draftCount})` : ''}` },
          ]).map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setTab(item.id)}
              className={`label-code rounded px-4 py-2 transition ${
                tab === item.id ? 'bg-brand text-brand-ink' : 'text-content-muted hover:text-content'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {tab === 'DRAFTS' ? (
        <div className="mx-auto max-w-3xl px-4 py-8 md:px-8">
          <DraftsPanel
            posts={posts.data ?? []}
            loading={posts.loading}
            onChanged={posts.reload}
            onOpen={(post) => {
              openDraft(post);
              setTab('EDITOR');
            }}
          />
        </div>
      ) : (
      <div className="mx-auto grid max-w-7xl grid-cols-1 gap-8 px-4 py-10 lg:grid-cols-12 md:px-8">
        {/* Painel de edição */}
        <div className="flex flex-col gap-4 lg:col-span-5">
          <section className="panel flex flex-col gap-3 p-4">
            <div className="flex items-center justify-between">
              <span className="label-code text-brand-soft">
                {isCarousel ? `Carrossel — slide ${index + 1} de ${slides.length}` : 'Post único'}
              </span>
              <div className="flex items-center gap-1">
                <button type="button" className="btn-ghost px-2" onClick={() => moveSlide(-1)} disabled={index === 0} title="Mover para trás">
                  <ChevronLeft size={12} />
                </button>
                <button
                  type="button"
                  className="btn-ghost px-2"
                  onClick={() => moveSlide(1)}
                  disabled={index === slides.length - 1}
                  title="Mover para frente"
                >
                  <ChevronRight size={12} />
                </button>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {slides.map((_, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => setIndex(i)}
                  className={`label-code h-9 w-9 rounded transition ${
                    i === index ? 'bg-brand text-brand-ink' : 'bg-surface-high text-content-muted hover:text-content'
                  }`}
                >
                  {i + 1}
                </button>
              ))}
              <button type="button" className="btn-ghost" onClick={addSlide} title="Novo slide em branco">
                <Plus size={12} />
                Slide
              </button>
              <button type="button" className="btn-ghost" onClick={duplicateSlide} title="Duplicar este slide">
                <CopyIcon size={12} />
              </button>
              <button type="button" className="btn-ghost" onClick={removeSlide} disabled={slides.length === 1} title="Remover este slide">
                <Trash2 size={12} />
              </button>
            </div>

            <p className="text-xs text-content-muted">
              Cada slide tem template, layout e textos próprios; a proporção vale para o carrossel inteiro.
            </p>
          </section>

          <section className="panel flex flex-col gap-3 p-4">
            <span className="label-code text-brand-soft">Formato e layout</span>

            <div className="grid grid-cols-2 gap-2">
              {(['RATIO_4_5', 'RATIO_1_1'] as Ratio[]).map((ratio) => (
                <button
                  key={ratio}
                  type="button"
                  onClick={() => setRatio(ratio)}
                  className={`label-code rounded px-3 py-2 transition ${
                    spec.ratio === ratio ? 'bg-brand text-brand-ink' : 'bg-surface-high text-content-muted'
                  }`}
                >
                  {ratio === 'RATIO_4_5' ? '1080×1350 (4:5)' : '1080×1080 (1:1)'}
                </button>
              ))}
            </div>

            <div className="grid grid-cols-3 gap-2">
              {LAYOUTS.map((layout) => (
                <button
                  key={layout.id}
                  type="button"
                  title={layout.hint}
                  onClick={() => update('layout', layout.id as LayoutId)}
                  className={`label-code rounded px-3 py-2 transition ${
                    spec.layout === layout.id ? 'bg-brand text-brand-ink' : 'bg-surface-high text-content-muted'
                  }`}
                >
                  {layout.label}
                </button>
              ))}
            </div>

            <label className="flex items-center justify-between gap-3 text-sm text-content-muted">
              Véu de legibilidade
              <input
                type="range"
                min={0}
                max={1}
                step={0.05}
                value={spec.scrim}
                onChange={(event) => update('scrim', Number(event.target.value))}
                className="w-40 accent-brand"
              />
            </label>

            <label className="flex items-center gap-2 text-sm text-content-muted">
              <input
                type="checkbox"
                checked={spec.showLogo}
                onChange={(event) => update('showLogo', event.target.checked)}
                className="accent-brand"
              />
              Mostrar selo JH no topo
            </label>
          </section>

          <section className="panel flex flex-col gap-3 p-4">
            <div className="flex items-center justify-between">
              <span className="label-code text-brand-soft">Textos do slide {index + 1}</span>
              {posts.data && posts.data.length > 0 && (
                <select
                  defaultValue=""
                  onChange={(event) => loadFromPost(event.target.value)}
                  className="rounded bg-surface-lowest px-2 py-1 font-mono text-[11px]"
                >
                  <option value="">Puxar de um post…</option>
                  {posts.data.map((post) => (
                    <option key={post.id} value={post.code}>
                      {post.code} — {post.title.slice(0, 32)}
                    </option>
                  ))}
                </select>
              )}
            </div>

            {copies.data && copies.data.length > 0 && (
              <div className="flex flex-col gap-2 rounded bg-surface-lowest p-3">
                <span className="label-code text-content-muted">Puxar do Guia de Copy</span>
                <select
                  value={selectedCopy}
                  onChange={(event) => setSelectedCopy(event.target.value)}
                  className="rounded bg-surface px-3 py-2 text-sm"
                >
                  <option value="">Escolha uma legenda…</option>
                  {copies.data.map((entry) => (
                    <option key={entry.id} value={entry.id}>
                      {entry.postCode ? `${entry.postCode} — ` : ''}
                      {entry.headline.slice(0, 40)}
                    </option>
                  ))}
                </select>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    className="btn-ghost"
                    disabled={!selectedCopy}
                    onClick={() => loadFromCopy(selectedCopy)}
                  >
                    <FileText size={12} />
                    Usar neste slide
                  </button>
                  <button
                    type="button"
                    className="btn-ghost"
                    disabled={!selectedCopy}
                    onClick={() => splitCopyIntoSlides(selectedCopy)}
                    title="Um slide por parágrafo da legenda"
                  >
                    <Layers size={12} />
                    Dividir em slides
                  </button>
                </div>
              </div>
            )}

            <Field label="Chapéu (linha vermelha)" value={spec.eyebrow} onChange={(v) => update('eyebrow', v)} />
            <Field label="Manchete" value={spec.headline} onChange={(v) => update('headline', v)} textarea rows={2} />
            <Field label="Apoio" value={spec.body} onChange={(v) => update('body', v)} textarea rows={3} />

            <div className="grid grid-cols-2 gap-3">
              <Field label="CTA (faixa inferior)" value={spec.cta} onChange={(v) => update('cta', v)} />
              <Field label="Selo (canto direito)" value={spec.badge} onChange={(v) => update('badge', v)} />
            </div>
          </section>

          <section className="panel flex flex-col gap-3 p-4">
            <div className="flex items-center justify-between">
              <span className="label-code text-brand-soft">Tamanho dos textos</span>
              <button
                type="button"
                className="btn-ghost"
                onClick={() => update('scale', DEFAULT_SCALE)}
                disabled={isDefaultScale(spec.scale)}
              >
                <RotateCcw size={12} />
                Padrão
              </button>
            </div>

            <SizeControl label="Manchete" value={spec.scale.headline} onAdjust={(c) => adjustScale('headline', c)} />
            <SizeControl label="Apoio e tópicos" value={spec.scale.body} onAdjust={(c) => adjustScale('body', c)} />
            <SizeControl label="Chapéu, CTA e selo" value={spec.scale.label} onAdjust={(c) => adjustScale('label', c)} />

            <p className="text-xs text-content-muted">
              A manchete ainda encolhe sozinha se o texto não couber nas linhas do layout.
            </p>
          </section>

          <section className="panel flex flex-col gap-3 p-4">
            <div className="flex items-center justify-between">
              <span className="label-code text-brand-soft">Posição dos textos</span>
              <button
                type="button"
                className="btn-ghost"
                onClick={() => setSlides((current) => current.map((item, i) => (i === index ? { ...item, anchor: 'BOTTOM', align: 'LEFT', offsetY: 0 } : item)))}
                disabled={spec.anchor === 'BOTTOM' && spec.align === 'LEFT' && spec.offsetY === 0}
              >
                <RotateCcw size={12} />
                Padrão
              </button>
            </div>

            <div className="grid grid-cols-3 gap-2">
              {([
                { id: 'TOP' as const, label: 'Em cima' },
                { id: 'MIDDLE' as const, label: 'No meio' },
                { id: 'BOTTOM' as const, label: 'Embaixo' },
              ]).map((option) => (
                <button
                  key={option.id}
                  type="button"
                  onClick={() => update('anchor', option.id)}
                  className={`label-code rounded px-3 py-2 transition ${
                    spec.anchor === option.id ? 'bg-brand text-brand-ink' : 'bg-surface-high text-content-muted'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>

            <div className="grid grid-cols-2 gap-2">
              {([
                { id: 'LEFT' as const, label: 'Alinhado à esquerda' },
                { id: 'CENTER' as const, label: 'Centralizado' },
              ]).map((option) => (
                <button
                  key={option.id}
                  type="button"
                  onClick={() => update('align', option.id)}
                  className={`label-code rounded px-3 py-2 transition ${
                    spec.align === option.id ? 'bg-brand text-brand-ink' : 'bg-surface-high text-content-muted'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-3">
              <span className="label-code w-40 shrink-0 text-content-muted">Ajuste fino</span>
              <button type="button" className="btn-ghost px-2" onClick={() => nudge(-20)} aria-label="Subir textos">
                <ChevronUp size={12} />
              </button>
              <input
                type="range"
                min={-240}
                max={240}
                step={10}
                value={spec.offsetY}
                onChange={(event) => update('offsetY', Number(event.target.value))}
                className="flex-1 accent-brand"
              />
              <button type="button" className="btn-ghost px-2" onClick={() => nudge(20)} aria-label="Descer textos">
                <ChevronDown size={12} />
              </button>
              <span className="w-12 text-right font-mono text-[11px] text-content-muted">{spec.offsetY}px</span>
            </div>

            <p className="text-xs text-content-muted">O bloco nunca passa das margens seguras, mesmo no limite do ajuste.</p>
          </section>

          {spec.layout === 'TOPICOS' && (
            <section className="panel flex flex-col gap-3 p-4">
              <span className="label-code text-brand-soft">Tópicos — use “Título | detalhe”</span>
              {spec.bullets.map((bullet, i) => (
                <div key={i} className="flex items-center gap-2">
                  <input
                    value={bullet}
                    onChange={(event) => {
                      const next = [...spec.bullets];
                      next[i] = event.target.value;
                      update('bullets', next);
                    }}
                    className="flex-1 rounded bg-surface-lowest px-3 py-2 text-sm outline-none"
                  />
                  <button type="button" className="btn-ghost" onClick={() => update('bullets', spec.bullets.filter((_, j) => j !== i))}>
                    <Trash2 size={12} />
                  </button>
                </div>
              ))}
              {spec.bullets.length < 5 && (
                <button type="button" className="btn-ghost self-start" onClick={() => update('bullets', [...spec.bullets, ''])}>
                  <Plus size={12} />
                  Adicionar tópico
                </button>
              )}
            </section>
          )}

          <PhotoPicker
            photoUrl={spec.photoUrl}
            photoMode={spec.photoMode}
            zoom={spec.photoZoom}
            offsetX={spec.photoOffsetX}
            offsetY={spec.photoOffsetY}
            ratio={spec.ratio}
            onChange={({ photoUrl, photoMode, zoom, offsetX, offsetY }) =>
              setSlides((current) =>
                current.map((item, i) =>
                  i === index
                    ? {
                        ...item,
                        photoUrl,
                        photoMode: photoMode ?? item.photoMode,
                        photoZoom: zoom ?? item.photoZoom,
                        photoOffsetX: offsetX ?? item.photoOffsetX,
                        photoOffsetY: offsetY ?? item.photoOffsetY,
                      }
                    : item,
                ),
              )
            }
          />

          <section className="panel flex flex-col gap-3 p-4">
            <span className="label-code text-brand-soft">Template de fundo</span>

            {assets.loading && !assets.data && <LoadingState label="Carregando templates…" />}
            {assets.error && <ErrorState message={assets.error} onRetry={assets.reload} />}
            {!assets.loading && templates.length === 0 && (
              <p className="text-sm text-content-muted">
                Nenhum template nesta proporção. Suba um na aba Exportador de Templates.
              </p>
            )}

            <div className="grid max-h-80 grid-cols-4 gap-2 overflow-y-auto">
              {templates.map((asset: TemplateAsset) => (
                <button
                  key={asset.id}
                  type="button"
                  onClick={() =>
                    setSlides((current) =>
                      current.map((item, i) =>
                        i === index ? { ...item, templateUrl: asset.downloadUrl, theme: asset.theme } : item,
                      ),
                    )
                  }
                  className={`overflow-hidden rounded border transition ${
                    spec.templateUrl === asset.downloadUrl ? 'border-brand' : 'border-surface-high hover:border-outline'
                  }`}
                  title={`${asset.code} — ${asset.name}`}
                  style={{ aspectRatio: asset.ratio === 'RATIO_1_1' ? '1/1' : '4/5' }}
                >
                  {asset.previewUrl ? (
                    <img src={assetUrl(asset.previewUrl)} alt={asset.code} className="h-full w-full object-cover" />
                  ) : (
                    <ImageIcon size={16} className="text-content-muted" />
                  )}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-2">
              <span className="label-code text-content-muted">Tema do texto</span>
              {(['DARK', 'LIGHT'] as const).map((theme) => (
                <button
                  key={theme}
                  type="button"
                  onClick={() => update('theme', theme)}
                  className={`label-code rounded px-3 py-1 transition ${
                    spec.theme === theme ? 'bg-brand text-brand-ink' : 'bg-surface-high text-content-muted'
                  }`}
                >
                  {theme === 'DARK' ? 'Claro sobre escuro' : 'Escuro sobre claro'}
                </button>
              ))}
            </div>
          </section>
        </div>

        {/* Prévia */}
        <div className="flex flex-col gap-3 lg:col-span-7">
          <div className="flex items-center justify-between">
            <span className="label-code text-content-muted">
              <Wand2 size={12} className="mr-1 inline text-brand" />
              Slide {index + 1} de {slides.length} — exporta em {width}×{height}
            </span>
            {isCarousel && (
              <button type="button" className="btn-ghost" onClick={downloadCurrent} disabled={Boolean(busy)}>
                <Download size={12} />
                Baixar só este slide
              </button>
            )}
          </div>

          <div className="panel flex items-center justify-center p-4">
            <canvas
              ref={canvasRef}
              onPointerDown={startDrag}
              onPointerMove={dragPhoto}
              onPointerUp={endDrag}
              onPointerCancel={endDrag}
              className={`h-auto w-full max-w-[520px] touch-none rounded shadow-2xl ${
                spec.photoUrl ? (dragging ? 'cursor-grabbing' : 'cursor-grab') : ''
              }`}
              style={{ aspectRatio: `${width}/${height}` }}
              title={spec.photoUrl ? 'Arraste para reposicionar a foto' : undefined}
            />
          </div>

          {message && (
            <p className="panel p-3 text-sm text-content-muted">
              {message}{' '}
              {message.includes('Posts Detalhados') && (
                <Link to="/posts" className="text-brand-soft underline">
                  Abrir Posts Detalhados
                </Link>
              )}
            </p>
          )}

          {!session && (
            <p className="panel p-3 text-sm text-content-muted">
              Entre com sua conta para salvar no planner (o download continua liberado).
            </p>
          )}

          {renderError && <ErrorState message={renderError} />}

          <p className="text-xs text-content-muted">
            As margens seguem a safe zone do Instagram: 72 px nas laterais e {spec.ratio === 'RATIO_1_1' ? '96' : '150'} px
            no topo e na base, para nada ficar sob a interface do app.
          </p>
        </div>
      </div>
      )}
    </>
  );
}

const SCALE_STEP = 0.05;

function clampScale(value: number): number {
  return Math.min(1.6, Math.max(0.6, Number(value.toFixed(2))));
}

function isDefaultScale(scale: TextScale): boolean {
  return scale.headline === 1 && scale.body === 1 && scale.label === 1;
}

/** Controle de escala de um tipo de texto: 60% a 160% do tamanho padrão. */
function SizeControl({
  label,
  value,
  onAdjust,
}: {
  label: string;
  value: number;
  onAdjust: (change: number | { set: number }) => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <span className="label-code w-40 shrink-0 text-content-muted">{label}</span>
      <button type="button" className="btn-ghost px-2" onClick={() => onAdjust(-SCALE_STEP)} aria-label={`Diminuir ${label}`}>
        <Minus size={12} />
      </button>
      <input
        type="range"
        min={0.6}
        max={1.6}
        step={SCALE_STEP}
        value={value}
        onChange={(event) => onAdjust({ set: Number(event.target.value) })}
        className="flex-1 accent-brand"
      />
      <button type="button" className="btn-ghost px-2" onClick={() => onAdjust(SCALE_STEP)} aria-label={`Aumentar ${label}`}>
        <Plus size={12} />
      </button>
      <span className="w-12 text-right font-mono text-[11px] text-content-muted">{Math.round(value * 100)}%</span>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  textarea,
  rows = 2,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  textarea?: boolean;
  rows?: number;
}) {
  const className = 'rounded bg-surface-lowest px-3 py-2 text-sm outline-none placeholder:text-content-muted/60';
  return (
    <label className="flex flex-col gap-1">
      <span className="label-code text-content-muted">{label}</span>
      {textarea ? (
        <textarea rows={rows} value={value} onChange={(event) => onChange(event.target.value)} className={className} />
      ) : (
        <input value={value} onChange={(event) => onChange(event.target.value)} className={className} />
      )}
    </label>
  );
}
