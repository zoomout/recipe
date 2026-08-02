CREATE TABLE recipe
(
    id           UUID PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(1000),
    instructions VARCHAR(10000) NOT NULL,
    servings     INTEGER      NOT NULL CHECK (servings >= 1),
    version      BIGINT       NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- position preserves ingredient order and serves as part of the primary key
CREATE TABLE recipe_ingredient
(
    recipe_id  UUID           NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    ingredient VARCHAR(255)   NOT NULL,
    vegetarian BOOLEAN        NOT NULL,
    quantity   NUMERIC(10, 2) NOT NULL,
    unit       VARCHAR(8)     NOT NULL,
    position   INTEGER        NOT NULL,
    PRIMARY KEY (recipe_id, position)
);

-- one row per user and recipe: their first contribution
-- seq gives the insertion order for deterministic chronological loads
CREATE TABLE recipe_contributor
(
    recipe_id         UUID         NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    user_id           VARCHAR(320) NOT NULL,
    contribution_type VARCHAR(16)  NOT NULL CHECK (contribution_type IN ('CREATED', 'UPDATED')),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    seq               BIGINT       GENERATED ALWAYS AS IDENTITY,
    PRIMARY KEY (recipe_id, user_id)
);

CREATE INDEX idx_recipe_servings ON recipe (servings);
