package com.bz.recipe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root: bootstraps the hexagon (domain, application, adapters)
 * and lives above all three layer packages.
 */
@SpringBootApplication
public class RecipeApplication {

    public static void main(
        String[] args
    ) {
        SpringApplication.run(RecipeApplication.class, args);
    }
}
