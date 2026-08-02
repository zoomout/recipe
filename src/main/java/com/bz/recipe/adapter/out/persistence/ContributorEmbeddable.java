package com.bz.recipe.adapter.out.persistence;

import com.bz.recipe.domain.model.ContributionType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

/**
 * JPA element-collection row of the {@code recipe_contributor} table; the
 * persistence-side counterpart of the domain {@code Contributor}.
 */
@SuppressWarnings("com.intellij.jpb.NoArgsConstructorInspection")
@Embeddable
public record ContributorEmbeddable(
    @Column(name = "user_id", nullable = false) String userId,
    @Column(name = "contribution_type", nullable = false) @Enumerated(EnumType.STRING) ContributionType contributionType,
    @Column(name = "created_at", nullable = false) Instant createdAt
) {
}
