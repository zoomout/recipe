package com.bz.recipe.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLOrder;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * JPA aggregate mapping of the {@code recipe} table (with the
 * {@code recipe_ingredient} and {@code recipe_contributor} collection tables);
 * the persistence-side counterpart of the domain {@code Recipe}.
 */
@Entity
@Table(name = "recipe")
@Getter
@Setter
@NoArgsConstructor
public class RecipeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    private String instructions;

    @Column(nullable = false)
    private int servings;

    /**
     * Optimistic-locking version: concurrent stale updates fail instead of
     * silently overwriting each other.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recipe_ingredient", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "position")
    private List<IngredientEmbeddable> ingredients = new ArrayList<>();

    /**
     * Ordered by the insertion sequence (creator first, then updaters
     * chronologically) so updatedBy in responses is deterministic. The seq
     * column is deliberately unmapped: inserts omit it, the identity default
     * fills it.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recipe_contributor", joinColumns = @JoinColumn(name = "recipe_id"))
    @SQLOrder("seq")
    private Set<ContributorEmbeddable> contributors = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
