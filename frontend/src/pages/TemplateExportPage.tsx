import { useState } from 'react';
import { Link2, Download } from 'lucide-react';
import { assetsApi } from '../api/services';
import { useApi } from '../api/useApi';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/primitives';
import { AssetUploadForm } from '../components/AssetUploadForm';
import { useAuth } from '../auth/AuthContext';
import type { AssetKind } from '../types/api';
import { assetUrl } from '../api/assetUrl';

const FILTERS: { value: AssetKind | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Todos os assets' },
  { value: 'BACKGROUND', label: 'Backgrounds' },
  { value: 'LOGO', label: 'Kit de logos' },
  { value: 'GUIDELINE', label: 'Diretrizes' },
];

const RATIO_LABEL: Record<string, string> = {
  RATIO_4_5: '1080×1350 (4:5)',
  RATIO_1_1: '1080×1080 (1:1)',
  VECTOR: 'Vetorial',
};

export function TemplateExportPage() {
  const [kind, setKind] = useState<AssetKind | 'ALL'>('ALL');
  // As artes exportadas pelo editor (kind ARTE) pertencem aos posts, não ao catálogo de templates.
  const assets = useApi(
    async () => {
      const list = await assetsApi.list(kind === 'ALL' ? undefined : { kind });
      return list.filter((asset) => asset.kind !== 'ARTE' && asset.kind !== 'FOTO');
    },
    [kind],
  );
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const { session } = useAuth();

  return (
    <>
      <PageHeader
        eyebrow="AFFINITY SUITE READY"
        title="Central de Assets & Engenharia de Templates"
        description="Repositório de backgrounds 4:5 e 1:1, kit vetorial de marca e diretrizes de safe zones milimétricas."
        actions={
          <div className="flex flex-wrap items-center gap-1 rounded-lg bg-surface-low p-1">
            {FILTERS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setKind(option.value)}
                className={`label-code rounded px-3 py-1.5 transition ${
                  kind === option.value ? 'bg-brand text-brand-ink' : 'text-content-muted hover:text-content'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        }
      />

      <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-10 md:px-8">
        {session ? (
          <AssetUploadForm onUploaded={assets.reload} />
        ) : (
          <div className="panel p-4 text-sm text-content-muted">
            Entre com sua conta pelo botão no topo para enviar novos templates.
          </div>
        )}

        {assets.loading && !assets.data && <LoadingState label="Carregando catálogo de templates…" />}
        {assets.error && <ErrorState message={assets.error} onRetry={assets.reload} />}
        {assets.data?.length === 0 && <EmptyState message="Nenhum template cadastrado nesta categoria." />}

        {assets.data && assets.data.length > 0 && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {assets.data.map((asset) => (
              <div key={asset.id} className="panel flex flex-col overflow-hidden">
                <div
                  className="relative flex items-center justify-center bg-surface-lowest"
                  style={{ aspectRatio: asset.ratio === 'RATIO_1_1' ? '1/1' : '4/5' }}
                >
                  {asset.previewUrl ? (
                    <img src={assetUrl(asset.previewUrl)} alt={asset.name} className="h-full w-full object-cover" />
                  ) : (
                    <span className="font-display text-4xl text-brand-soft/25">JH</span>
                  )}
                  <span className="label-code absolute left-2 top-2 rounded bg-surface-lowest/90 px-2 py-0.5 text-brand-soft">
                    {asset.code} // {asset.theme}
                  </span>
                </div>

                <div className="flex flex-col gap-2 bg-surface p-4">
                  <span className="truncate text-sm font-semibold">{asset.name}</span>
                  <span className="label-code text-content-muted">
                    {RATIO_LABEL[asset.ratio]} • {asset.composition ?? '—'}
                  </span>

                  <div className="flex items-center justify-between pt-1">
                    <button
                      type="button"
                      className="btn-ghost"
                      onClick={async () => {
                        await navigator.clipboard.writeText(assetUrl(asset.downloadUrl) ?? asset.fileName ?? asset.code);
                        setCopiedCode(asset.code);
                        setTimeout(() => setCopiedCode(null), 2000);
                      }}
                    >
                      <Link2 size={12} />
                      {copiedCode === asset.code ? 'Copiado!' : 'Copiar link'}
                    </button>

                    {asset.downloadUrl && (
                      <a className="btn-primary" href={assetUrl(asset.downloadUrl)} target="_blank" rel="noreferrer">
                        <Download size={12} />
                        Baixar
                      </a>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
