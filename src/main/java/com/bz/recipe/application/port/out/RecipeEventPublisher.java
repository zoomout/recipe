package com.bz.recipe.application.port.out;

import com.bz.recipe.domain.model.RecipeChangedEvent;

/**
 * Driven port: publishes recipe change events so contributors can be notified.
 * Implementations must never let a publishing failure break the calling flow.
 */
public interface RecipeEventPublisher {

    void publish(
        RecipeChangedEvent event
    );
}
