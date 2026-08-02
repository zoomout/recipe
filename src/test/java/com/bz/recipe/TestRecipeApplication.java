package com.bz.recipe;

import org.springframework.boot.SpringApplication;

public class TestRecipeApplication {

    public static void main(
        String[] args
    ) {
        SpringApplication.from(RecipeApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
