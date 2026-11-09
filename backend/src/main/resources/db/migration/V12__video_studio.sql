-- Video Studio: um projeto de vídeo e as etapas que rodaram nele.
--
-- As colunas de arquivo guardam só o nome dentro da pasta do projeto; o caminho é sempre
-- montado pelo backend. Apagar o post solta o vídeo em vez de apagá-lo: um render de
-- minutos não deve sumir por causa de um rascunho descartado.
CREATE TABLE video_projects (
    id            BIGSERIAL    PRIMARY KEY,
    title         VARCHAR(180) NOT NULL,
    script        TEXT,
    post_id       BIGINT       REFERENCES posts (id) ON DELETE SET NULL,
    base_video    VARCHAR(80),
    audio         VARCHAR(80),
    lipsync_video VARCHAR(80),
    subtitles     VARCHAR(80),
    final_video   VARCHAR(80),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_video_projects_post ON video_projects (post_id);

CREATE TABLE video_jobs (
    id          BIGSERIAL   PRIMARY KEY,
    project_id  BIGINT      NOT NULL REFERENCES video_projects (id) ON DELETE CASCADE,
    step        VARCHAR(16) NOT NULL,
    state       VARCHAR(16) NOT NULL,
    log         TEXT,
    error       TEXT,
    started_at  TIMESTAMP,
    finished_at TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE INDEX idx_video_jobs_project ON video_jobs (project_id, id DESC);
CREATE INDEX idx_video_jobs_state ON video_jobs (state);
