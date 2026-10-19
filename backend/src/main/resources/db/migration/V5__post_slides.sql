-- Slides de carrossel: cada post pode ter várias artes, na ordem de publicação.
CREATE TABLE post_slides (
    post_id   BIGINT       NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    position  INTEGER      NOT NULL,
    image_url VARCHAR(1024) NOT NULL,
    PRIMARY KEY (post_id, position)
);

-- Posts que já têm arte única viram carrossel de um slide.
INSERT INTO post_slides (post_id, position, image_url)
SELECT id, 0, cover_image_url FROM posts WHERE cover_image_url IS NOT NULL;
