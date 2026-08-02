package com.bz.recipe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the hexagonal architecture: the domain is the isolated core,
 * the application layer may only depend on the domain, and adapters may
 * depend on application and domain but are never depended upon.
 */
@AnalyzeClasses(packages = "com.bz.recipe", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..application..", "..adapter..")
        .because("the domain is the core of the hexagon and must not know the outside world");

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "com.fasterxml.jackson..", "org.apache.kafka..", "org.hibernate..")
        .because("the domain must be isolated from web, persistence and messaging frameworks");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..adapter..")
        .because("the application layer talks to the outside world only through ports");

    @ArchTest
    static final ArchRule layersAreRespected = layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Domain")
        .definedBy("..domain..")
        .layer("Application")
        .definedBy("..application..")
        .layer("Adapter")
        .definedBy("..adapter..")
        .whereLayer("Adapter")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Application")
        .mayOnlyBeAccessedByLayers("Adapter")
        .whereLayer("Domain")
        .mayOnlyBeAccessedByLayers("Application", "Adapter");
}
