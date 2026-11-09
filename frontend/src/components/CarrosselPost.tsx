import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Download } from 'lucide-react';
import { assetUrl } from '../api/assetUrl';
import type { Post } from '../types/api';

/**
 * As artes do post, uma a uma, na ordem em que vão ao Instagram.
 *
 * <p>Antes a tela mostrava só a capa: um carrossel de seis slides aparecia como uma imagem,
 * e não havia como conferir o resto antes de publicar.</p>
 */
export function CarrosselPost({ post }: { post: Post }) {
  // slides é a ordem de publicação; a capa cobre os posts antigos, salvos antes dela existir.
  const imagens = post.slides?.length ? post.slides : post.coverImageUrl ? [post.coverImageUrl] : [];
  const [atual, setAtual] = useState(0);

  // Trocar de post com o carrossel aberto deixaria o índice apontando para um slide que sumiu.
  useEffect(() => setAtual(0), [post.id]);

  useEffect(() => {
    if (imagens.length < 2) return;

    function aoTeclar(evento: KeyboardEvent) {
      if (evento.key === 'ArrowLeft') setAtual((i) => (i - 1 + imagens.length) % imagens.length);
      if (evento.key === 'ArrowRight') setAtual((i) => (i + 1) % imagens.length);
    }

    window.addEventListener('keydown', aoTeclar);
    return () => window.removeEventListener('keydown', aoTeclar);
  }, [imagens.length]);

  if (imagens.length === 0) {
    return null;
  }

  const indice = Math.min(atual, imagens.length - 1);
  const imagem = imagens[indice];
  const varios = imagens.length > 1;

  return (
    <div className="flex flex-col gap-3">
      <div className="relative mx-auto w-full max-w-[500px]">
        <img
          src={assetUrl(imagem)}
          alt={`Arte ${indice + 1} de ${imagens.length} do post ${post.code}`}
          className="w-full rounded-xl border border-outline-variant/30 shadow-2xl"
        />

        {varios && (
          <>
            <Seta lado="esquerda" aoClicar={() => setAtual((i) => (i - 1 + imagens.length) % imagens.length)} />
            <Seta lado="direita" aoClicar={() => setAtual((i) => (i + 1) % imagens.length)} />

            <span className="label-code absolute right-3 top-3 rounded bg-surface-lowest/90 px-2 py-1 text-content-variant backdrop-blur">
              {indice + 1} / {imagens.length}
            </span>
          </>
        )}
      </div>

      {varios && (
        <div className="mx-auto flex max-w-[500px] flex-wrap justify-center gap-2">
          {imagens.map((slide, i) => (
            <button
              key={`${slide}-${i}`}
              type="button"
              onClick={() => setAtual(i)}
              aria-label={`Ver a arte ${i + 1}`}
              aria-current={i === indice}
              className={`h-14 w-14 overflow-hidden rounded border transition ${
                i === indice
                  ? 'border-brand opacity-100'
                  : 'border-outline-variant/40 opacity-60 hover:opacity-100'
              }`}
            >
              <img src={assetUrl(slide)} alt="" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      )}

      <a
        href={assetUrl(imagem)}
        download={`jhcomp-post-${post.code}${varios ? `-${indice + 1}` : ''}.png`}
        className="btn-ghost mx-auto"
      >
        <Download size={12} />
        {varios ? `Baixar a arte ${indice + 1}` : 'Baixar a arte'}
      </a>

      {varios && (
        <p className="label-code mx-auto text-content-muted">
          use as setas do teclado para passar os slides
        </p>
      )}
    </div>
  );
}

function Seta({ lado, aoClicar }: { lado: 'esquerda' | 'direita'; aoClicar: () => void }) {
  const esquerda = lado === 'esquerda';
  return (
    <button
      type="button"
      onClick={aoClicar}
      aria-label={esquerda ? 'Arte anterior' : 'Próxima arte'}
      className={`absolute top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full border border-outline-variant/40 bg-surface-lowest/90 text-content-variant backdrop-blur transition hover:text-content ${
        esquerda ? 'left-3' : 'right-3'
      }`}
    >
      {esquerda ? <ChevronLeft size={18} /> : <ChevronRight size={18} />}
    </button>
  );
}
