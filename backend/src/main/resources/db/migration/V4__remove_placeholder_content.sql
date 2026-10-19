-- Os posts e copies do V2 eram exemplos de demonstração, não conteúdo real da JH.COMP.
-- Só removemos o que continua como veio do seed (sem arte associada e ainda não publicado).

DELETE FROM copy_entries
WHERE post_code IN ('01', '02', '03', '04', '05', '06');

DELETE FROM posts
WHERE code IN ('01', '02', '03', '04', '05', '06')
  AND cover_image_url IS NULL
  AND status <> 'PUBLICADO';
