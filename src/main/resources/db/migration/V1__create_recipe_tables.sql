-- Unit lookup table (not a detail table), seeded in V2 to match the domain Unit enum.
CREATE TABLE unit
(
    id          INTEGER      PRIMARY KEY,
    name        VARCHAR(16)  NOT NULL UNIQUE,
    description VARCHAR(64)  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE recipe
(
    id           UUID PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(1000),
    instructions TEXT         NOT NULL,
    servings     INTEGER      NOT NULL CHECK (servings >= 1),
    version      BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

-- weak entity / detail table (belongs to recipe, not a separate entity)
-- position to preserve order and use in primary key instead of ingredient name
CREATE TABLE recipe_ingredient
(
    recipe_id  UUID           NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    ingredient VARCHAR(255)   NOT NULL,
    vegetarian BOOLEAN        NOT NULL,
    quantity   NUMERIC(10, 2) NOT NULL,
    unit_id    INTEGER        NOT NULL REFERENCES unit (id),
    position   INTEGER        NOT NULL,
    PRIMARY KEY (recipe_id, position)
);

-- weak entity / detail table (belongs to recipe, not a separate entity)
-- one row per user and recipe: their first contribution (semantics in ContributionType)
-- seq gives the insertion order for deterministic chronological loads (see RecipeEntity)
-- created_at is written by the application; the default serves direct SQL inserts only
CREATE TABLE recipe_contributor
(
    recipe_id           UUID         NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    user_id             VARCHAR(320) NOT NULL,
    contribution_type   VARCHAR(16)  NOT NULL CHECK (contribution_type IN ('CREATED', 'UPDATED')),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    seq                 BIGINT       GENERATED ALWAYS AS IDENTITY,
    PRIMARY KEY (recipe_id, user_id)
);

CREATE INDEX idx_recipe_servings ON recipe (servings);
CREATE INDEX idx_recipe_ingredient_lower ON recipe_ingredient (lower(ingredient));
CREATE INDEX idx_recipe_ingredient_non_veg ON recipe_ingredient (recipe_id) WHERE NOT vegetarian;
