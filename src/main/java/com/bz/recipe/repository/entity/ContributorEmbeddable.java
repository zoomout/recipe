package com.bz.recipe.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

@SuppressWarnings("com.intellij.jpb.NoArgsConstructorInspection")
@Embeddable
public record ContributorEmbeddable(
    @Column(name = "user_id", nullable = false) String userId,
    @Column(name = "contribution_type", nullable = false) @Enumerated(EnumType.STRING) ContributionType contributionType,
    @Column(name = "created_at", nullable = false) Instant createdAt
) {
}
