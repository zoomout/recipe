package com.bz.recipe.domain.model;

/**
 * Unit of measure for an ingredient amount. Each constant carries the id and
 * description of its row in the {@code unit} lookup table (which V2 seeds from
 * these values), so the enum is the source of truth for the persisted
 * {@code unit_id}.
 */
public enum Unit {
    TBSP(1, "tablespoon"), TBS(2, "teaspoon"), G(3, "gram"), KG(4, "kilogram"), ML(5, "millilitre"), L(6, "litre"), PC(7, "piece");

    private final int id;
    private final String description;

    Unit(
        int id,
        String description
    ) {
        this.id = id;
        this.description = description;
    }

    public int id() {
        return id;
    }

    public String description() {
        return description;
    }

    public static Unit fromId(
        Integer id
    ) {
        if (id == null) {
            return null;
        }
        for (var unit : values()) {
            if (unit.id == id) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown unit id: " + id);
    }
}
