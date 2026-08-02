package com.bz.recipe.adapter.out.messaging;

import com.bz.recipe.domain.model.RecipeChangedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps the domain {@code RecipeChangedEvent} to the {@link RecipeChanged}
 * Kafka wire format (the action enum maps to the message type by name).
 */
@Mapper(componentModel = "spring")
interface RecipeEventMapper {

    @Mapping(target = "type", source = "action")
    RecipeChanged toMessage(
        RecipeChangedEvent event
    );
}
