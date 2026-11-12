-- Os slides do Editor de Posts como o editor os montou: textos de cada slide, layout,
-- fundo, foto, posições e tamanhos. Sem isto só os PNGs eram salvos, e reabrir um
-- carrossel trazia os slides 2 em diante vazios. O backend guarda o JSON sem interpretar.
ALTER TABLE posts ADD COLUMN editor_spec TEXT;
