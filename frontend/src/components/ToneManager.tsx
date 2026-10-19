import { useState } from 'react';
import { Check, Hash, Pencil, Plus, Tags, Trash2, X } from 'lucide-react';
import { hashtagBlocksApi, settingsApi, tonesApi, type HashtagBlock, type Tone } from '../api/services';
import { describeError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

const EMPTY = { id: null as number | null, code: '', label: '', description: '' };

/** Cadastro dos tons de copy e das hashtags fixas da marca. */
export function ToneManager({
  tones,
  globalHashtags,
  blocks,
  onTonesChanged,
  onBlocksChanged,
  onHashtagsChanged,
}: {
  tones: Tone[];
  globalHashtags: string;
  blocks: HashtagBlock[];
  onTonesChanged: () => void;
  onBlocksChanged: () => void;
  onHashtagsChanged: (value: string) => void;
}) {
  const { session } = useAuth();
  const [form, setForm] = useState<typeof EMPTY | null>(null);
  const [confirming, setConfirming] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [hashtags, setHashtags] = useState(globalHashtags);
  const [savingTags, setSavingTags] = useState(false);
  const [tagsSaved, setTagsSaved] = useState(false);

  async function saveTone() {
    if (!form || !form.code.trim() || !form.label.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      const payload = { code: form.code.trim(), label: form.label.trim(), description: form.description.trim() || undefined };
      if (form.id) await tonesApi.update(form.id, payload);
      else await tonesApi.create(payload);
      setForm(null);
      onTonesChanged();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  async function removeTone(tone: Tone) {
    setBusy(true);
    setError(null);
    try {
      await tonesApi.remove(tone.id);
      setConfirming(null);
      onTonesChanged();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  async function saveHashtags() {
    setSavingTags(true);
    setError(null);
    try {
      const saved = await settingsApi.setHashtags(hashtags);
      onHashtagsChanged(saved);
      setTagsSaved(true);
      setTimeout(() => setTagsSaved(false), 2500);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setSavingTags(false);
    }
  }

  return (
    <section className="panel flex flex-col gap-5 p-4">
      {/* Hashtags fixas */}
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <Tags size={14} className="text-brand" />
          <span className="label-code text-brand-soft">Hashtags fixas de todo post</span>
        </div>
        <p className="text-xs text-content-muted">
          Entram no fim de toda legenda copiada ou aplicada em um post, somadas às hashtags da própria legenda.
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <input
            value={hashtags}
            onChange={(event) => setHashtags(event.target.value)}
            disabled={!session}
            placeholder="#JHComp #AssistenciaTecnica"
            className="min-w-[16rem] flex-1 rounded bg-surface-lowest px-3 py-2 font-mono text-xs outline-none disabled:opacity-60"
          />
          {session && (
            <button type="button" className="btn-primary" onClick={saveHashtags} disabled={savingTags || hashtags === globalHashtags}>
              <Check size={12} />
              {savingTags ? 'Salvando…' : tagsSaved ? 'Salvo!' : 'Salvar tags'}
            </button>
          )}
        </div>
      </div>

      <div className="h-px bg-surface-high" />

      {/* Blocos de hashtags por categoria */}
      <BlockManager blocks={blocks} onChanged={onBlocksChanged} />

      <div className="h-px bg-surface-high" />

      {/* Tons */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <span className="label-code text-brand-soft">Tons de copy</span>
          {session && !form && (
            <button type="button" className="btn-ghost" onClick={() => setForm({ ...EMPTY })}>
              <Plus size={12} />
              Novo tom
            </button>
          )}
        </div>

        {form && (
          <div className="flex flex-col gap-2 rounded bg-surface-lowest p-3">
            <div className="grid grid-cols-1 gap-2 md:grid-cols-3">
              <input
                value={form.code}
                onChange={(event) => setForm({ ...form, code: event.target.value })}
                placeholder="Código (ex.: BASTIDOR)"
                className="rounded bg-surface px-3 py-2 font-mono text-xs outline-none"
              />
              <input
                value={form.label}
                onChange={(event) => setForm({ ...form, label: event.target.value })}
                placeholder="Nome exibido"
                className="rounded bg-surface px-3 py-2 text-sm outline-none"
              />
              <input
                value={form.description}
                onChange={(event) => setForm({ ...form, description: event.target.value })}
                placeholder="Quando usar (opcional)"
                className="rounded bg-surface px-3 py-2 text-sm outline-none"
              />
            </div>
            <div className="flex items-center gap-2">
              <button type="button" className="btn-primary" onClick={saveTone} disabled={busy}>
                <Check size={12} />
                {busy ? 'Salvando…' : 'Salvar tom'}
              </button>
              <button type="button" className="btn-ghost" onClick={() => setForm(null)}>
                <X size={12} />
                Cancelar
              </button>
            </div>
          </div>
        )}

        <div className="flex flex-col gap-2">
          {tones.map((tone) => (
            <div key={tone.id} className="flex flex-wrap items-center gap-3 rounded bg-surface-lowest px-3 py-2">
              <span className="label-code rounded bg-surface-high px-2 py-1 text-content-variant">{tone.code}</span>
              <span className="text-sm font-semibold">{tone.label}</span>
              <span className="flex-1 truncate text-xs text-content-muted">{tone.description}</span>
              <span className="label-code text-content-muted">{tone.usageCount} legenda(s)</span>

              {session && (
                confirming === tone.id ? (
                  <div className="flex items-center gap-1">
                    <button type="button" className="btn-primary px-2" onClick={() => removeTone(tone)} disabled={busy}>
                      Apagar
                    </button>
                    <button type="button" className="btn-ghost px-2" onClick={() => setConfirming(null)}>
                      Não
                    </button>
                  </div>
                ) : (
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      className="btn-ghost px-2"
                      onClick={() =>
                        setForm({ id: tone.id, code: tone.code, label: tone.label, description: tone.description ?? '' })
                      }
                    >
                      <Pencil size={12} />
                    </button>
                    <button type="button" className="btn-ghost px-2" onClick={() => setConfirming(tone.id)}>
                      <Trash2 size={12} />
                    </button>
                  </div>
                )
              )}
            </div>
          ))}
        </div>

        {error && <p className="text-sm text-brand-soft">{error}</p>}
        {!session && <p className="text-xs text-content-muted">Entre com sua conta para editar tons e hashtags.</p>}
      </div>
    </section>
  );
}

const EMPTY_BLOCK = { id: null as number | null, name: '', hashtags: '' };

/** Cadastro dos blocos de hashtags por categoria usados no formulário de legenda. */
function BlockManager({ blocks, onChanged }: { blocks: HashtagBlock[]; onChanged: () => void }) {
  const { session } = useAuth();
  const [form, setForm] = useState<typeof EMPTY_BLOCK | null>(null);
  const [confirming, setConfirming] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    if (!form || !form.name.trim() || !form.hashtags.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      const payload = { name: form.name.trim(), hashtags: form.hashtags.trim() };
      if (form.id) await hashtagBlocksApi.update(form.id, payload);
      else await hashtagBlocksApi.create(payload);
      setForm(null);
      onChanged();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  async function remove(block: HashtagBlock) {
    setBusy(true);
    setError(null);
    try {
      await hashtagBlocksApi.remove(block.id);
      setConfirming(null);
      onChanged();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Hash size={14} className="text-brand" />
          <span className="label-code text-brand-soft">Blocos de hashtags por categoria</span>
        </div>
        {session && !form && (
          <button type="button" className="btn-ghost" onClick={() => setForm({ ...EMPTY_BLOCK })}>
            <Plus size={12} />
            Novo bloco
          </button>
        )}
      </div>
      <p className="text-xs text-content-muted">
        Ao escrever uma legenda, um clique no bloco acrescenta essas hashtags no fim do campo, sem repetir as que já estão lá.
      </p>

      {form && (
        <div className="flex flex-col gap-2 rounded bg-surface-lowest p-3">
          <div className="grid grid-cols-1 gap-2 md:grid-cols-3">
            <input
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              placeholder="Nome (ex.: Manutenção)"
              className="rounded bg-surface px-3 py-2 text-sm outline-none"
            />
            <input
              value={form.hashtags}
              onChange={(event) => setForm({ ...form, hashtags: event.target.value })}
              placeholder="#ManutencaoPC #SSDNvme"
              className="rounded bg-surface px-3 py-2 font-mono text-xs outline-none md:col-span-2"
            />
          </div>
          <div className="flex items-center gap-2">
            <button type="button" className="btn-primary" onClick={save} disabled={busy}>
              <Check size={12} />
              {busy ? 'Salvando…' : 'Salvar bloco'}
            </button>
            <button type="button" className="btn-ghost" onClick={() => setForm(null)}>
              <X size={12} />
              Cancelar
            </button>
          </div>
        </div>
      )}

      <div className="flex flex-col gap-2">
        {blocks.length === 0 && (
          <p className="text-xs text-content-muted">Nenhum bloco cadastrado ainda.</p>
        )}
        {blocks.map((block) => (
          <div key={block.id} className="flex flex-wrap items-center gap-3 rounded bg-surface-lowest px-3 py-2">
            <span className="text-sm font-semibold">{block.name}</span>
            <span className="flex-1 truncate font-mono text-[11px] text-content-muted">{block.hashtags}</span>
            <span className="label-code text-content-muted">{block.tagCount} tag(s)</span>

            {session && (
              confirming === block.id ? (
                <div className="flex items-center gap-1">
                  <button type="button" className="btn-primary px-2" onClick={() => remove(block)} disabled={busy}>
                    Apagar
                  </button>
                  <button type="button" className="btn-ghost px-2" onClick={() => setConfirming(null)}>
                    Não
                  </button>
                </div>
              ) : (
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    className="btn-ghost px-2"
                    onClick={() => setForm({ id: block.id, name: block.name, hashtags: block.hashtags })}
                  >
                    <Pencil size={12} />
                  </button>
                  <button type="button" className="btn-ghost px-2" onClick={() => setConfirming(block.id)}>
                    <Trash2 size={12} />
                  </button>
                </div>
              )
            )}
          </div>
        ))}
      </div>

      {error && <p className="text-sm text-brand-soft">{error}</p>}
    </div>
  );
}
