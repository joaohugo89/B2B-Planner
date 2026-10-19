import { useState } from 'react';
import { ImagePlus, RotateCcw, Trash2, Upload } from 'lucide-react';
import { assetsApi } from '../api/services';
import { describeError } from '../api/client';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import type { PhotoMode } from '../editor/layouts';
import { assetUrl } from '../api/assetUrl';

/** Envia uma foto do usuário e escolhe se ela vira fundo ou destaque do slide. */
export function PhotoPicker({
  photoUrl,
  photoMode,
  zoom,
  offsetX,
  offsetY,
  ratio,
  onChange,
}: {
  photoUrl: string | null;
  photoMode: PhotoMode;
  zoom: number;
  offsetX: number;
  offsetY: number;
  ratio: string;
  onChange: (next: {
    photoUrl: string | null;
    photoMode?: PhotoMode;
    zoom?: number;
    offsetX?: number;
    offsetY?: number;
  }) => void;
}) {
  const { session } = useAuth();
  const photos = useApi(() => assetsApi.list({ kind: 'FOTO' }), []);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [inputKey, setInputKey] = useState(0);

  async function upload(file: File) {
    setBusy(true);
    setError(null);
    try {
      const asset = await assetsApi.upload(file, {
        code: `FOTO-${Date.now().toString().slice(-7)}`,
        name: file.name.replace(/\.[^.]+$/, '').slice(0, 80) || 'Foto',
        kind: 'FOTO',
        ratio: ratio === 'RATIO_1_1' ? 'RATIO_1_1' : 'RATIO_4_5',
        theme: 'DARK',
      });
      onChange({ photoUrl: asset.downloadUrl, photoMode });
      photos.reload();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
      setInputKey((key) => key + 1);
    }
  }

  return (
    <section className="panel flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between">
        <span className="label-code text-brand-soft">Foto do slide</span>
        {photoUrl && (
          <button type="button" className="btn-ghost" onClick={() => onChange({ photoUrl: null })}>
            <Trash2 size={12} />
            Remover
          </button>
        )}
      </div>

      {session ? (
        <label className="flex items-center gap-2">
          <input
            key={inputKey}
            type="file"
            accept=".png,.jpg,.jpeg,.webp"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) void upload(file);
            }}
            className="flex-1 rounded bg-surface-lowest px-3 py-2 text-xs file:mr-3 file:rounded file:border-0 file:bg-surface-high file:px-3 file:py-1 file:text-content"
          />
          {busy && <Upload size={14} className="animate-pulse text-brand" />}
        </label>
      ) : (
        <p className="text-xs text-content-muted">Entre com sua conta para enviar fotos.</p>
      )}

      {photoUrl && (
        <>
          <div className="grid grid-cols-2 gap-2">
            {([
              { id: 'FRAME' as const, label: 'Em destaque' },
              { id: 'BACKGROUND' as const, label: 'Como fundo' },
            ]).map((option) => (
              <button
                key={option.id}
                type="button"
                onClick={() => onChange({ photoUrl, photoMode: option.id })}
                className={`label-code rounded px-3 py-2 transition ${
                  photoMode === option.id ? 'bg-brand text-brand-ink' : 'bg-surface-high text-content-muted'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>

          <div className="flex flex-col gap-2 rounded bg-surface-lowest p-3">
            <div className="flex items-center justify-between">
              <span className="label-code text-content-muted">Enquadramento</span>
              <button
                type="button"
                className="btn-ghost"
                onClick={() => onChange({ photoUrl, zoom: 1, offsetX: 0, offsetY: 0 })}
                disabled={zoom === 1 && offsetX === 0 && offsetY === 0}
              >
                <RotateCcw size={12} />
                Padrão
              </button>
            </div>

            <Slider
              label="Zoom"
              value={zoom}
              min={0.4}
              max={3}
              step={0.05}
              format={(v) => `${Math.round(v * 100)}%`}
              onChange={(value) => onChange({ photoUrl, zoom: value })}
            />
            <Slider
              label="Horizontal"
              value={offsetX}
              min={-1}
              max={1}
              step={0.05}
              format={(v) => (v === 0 ? 'centro' : v < 0 ? 'esquerda' : 'direita')}
              onChange={(value) => onChange({ photoUrl, offsetX: value })}
            />
            <Slider
              label="Vertical"
              value={offsetY}
              min={-1}
              max={1}
              step={0.05}
              format={(v) => (v === 0 ? 'centro' : v < 0 ? 'topo' : 'base')}
              onChange={(value) => onChange({ photoUrl, offsetY: value })}
            />

            <p className="text-[11px] text-content-muted">
              Arraste a foto direto na prévia para reposicionar. Abaixo de 100% ela fica menor que a
              área, deixando respiro para o texto.
            </p>
          </div>
        </>
      )}

      {photos.data && photos.data.length > 0 && (
        <>
          <span className="label-code text-content-muted">Fotos já enviadas</span>
          <div className="grid max-h-40 grid-cols-5 gap-2 overflow-y-auto">
            {photos.data.map((photo) => (
              <button
                key={photo.id}
                type="button"
                title={photo.name}
                onClick={() => onChange({ photoUrl: photo.downloadUrl, photoMode })}
                className={`aspect-square overflow-hidden rounded border transition ${
                  photoUrl === photo.downloadUrl ? 'border-brand' : 'border-surface-high hover:border-outline'
                }`}
              >
                {photo.previewUrl ? (
                  <img src={assetUrl(photo.previewUrl)} alt={photo.name} className="h-full w-full object-cover" />
                ) : (
                  <ImagePlus size={14} className="text-content-muted" />
                )}
              </button>
            ))}
          </div>
        </>
      )}

      {error && <p className="text-sm text-brand-soft">{error}</p>}
      <p className="text-xs text-content-muted">
        “Em destaque” coloca a foto num quadro acima do texto; “como fundo” cobre a arte inteira, no
        lugar do template.
      </p>
    </section>
  );
}

function Slider({
  label,
  value,
  min,
  max,
  step,
  format,
  onChange,
  disabled,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  format: (value: number) => string;
  onChange: (value: number) => void;
  disabled?: boolean;
}) {
  return (
    <div className="flex items-center gap-3">
      <span className="label-code w-20 shrink-0 text-content-muted">{label}</span>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(Number(event.target.value))}
        className="flex-1 accent-brand disabled:opacity-40"
      />
      <span className="w-16 text-right font-mono text-[11px] text-content-muted">{format(value)}</span>
    </div>
  );
}
