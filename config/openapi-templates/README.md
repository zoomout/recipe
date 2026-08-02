Overrides for single openapi-generator (JavaSpring) template files; anything
not present here falls back to the generator's built-in templates.

- `beanValidation.mustache`: upstream places `@Valid` on container getters
  (e.g. `@Valid ... List<@Valid Ingredient> getIngredients()`), which Hibernate
  Validator 9 deprecates (HV000271) - cascading is already guaranteed by the
  `@Valid` on the type argument, so the container-level annotation is dropped.
