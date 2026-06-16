package com.globo.subscription;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Architecture tests verifying hexagonal architecture dependency rules.
 * <p>
 * Validates: Requirements 1.4, 1.5
 */
class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        Path classesDir = Paths.get(
                ArchitectureTest.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .getParent()
                .resolve("classes");

        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(classesDir.toString());

        assertFalse(importedClasses.isEmpty(),
                "No classes imported — ensure the project is compiled before running architecture tests");
    }

    @Nested
    @DisplayName("Domain Layer Rules — Requirement 1.4")
    class DomainLayerRules {

        @Test
        @DisplayName("Domain layer should not depend on Spring framework")
        void domainShouldNotDependOnSpring() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.globo.subscription.domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Domain layer should not depend on Jakarta Persistence (JPA)")
        void domainShouldNotDependOnJakartaPersistence() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.globo.subscription.domain..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Domain layer should not depend on javax Persistence (legacy JPA)")
        void domainShouldNotDependOnJavaxPersistence() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.globo.subscription.domain..")
                    .should().dependOnClassesThat().resideInAPackage("javax.persistence..")
                    .allowEmptyShould(true);

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Application Layer Rules — Requirement 1.5")
    class ApplicationLayerRules {

        @Test
        @DisplayName("Application layer should not depend on adapter layer")
        void applicationShouldNotDependOnAdapters() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.globo.subscription.application..")
                    .should().dependOnClassesThat().resideInAPackage("com.globo.subscription.adapter..");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Outbound Adapter Rules — Port Implementation")
    class OutboundAdapterRules {

        @Test
        @DisplayName("Persistence adapters should implement port interfaces")
        void persistenceAdaptersShouldImplementPorts() {
            ArchRule rule = classes()
                    .that().resideInAPackage("com.globo.subscription.adapter.outbound.persistence")
                    .and().haveSimpleNameEndingWith("Adapter")
                    .should().implement(
                            com.tngtech.archunit.core.domain.JavaClass.Predicates
                                    .resideInAPackage("com.globo.subscription.application.port.."));

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Cache adapters should implement port interfaces")
        void cacheAdaptersShouldImplementPorts() {
            ArchRule rule = classes()
                    .that().resideInAPackage("com.globo.subscription.adapter.outbound.cache")
                    .and().haveSimpleNameEndingWith("Adapter")
                    .should().implement(
                            com.tngtech.archunit.core.domain.JavaClass.Predicates
                                    .resideInAPackage("com.globo.subscription.application.port.."));

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Event publisher adapters should implement port interfaces")
        void eventAdaptersShouldImplementPorts() {
            ArchRule rule = classes()
                    .that().resideInAPackage("com.globo.subscription.adapter.outbound.event")
                    .and().haveSimpleNameEndingWith("Adapter")
                    .should().implement(
                            com.tngtech.archunit.core.domain.JavaClass.Predicates
                                    .resideInAPackage("com.globo.subscription.application.port.."));

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Lock manager adapters should implement port interfaces")
        void lockAdaptersShouldImplementPorts() {
            ArchRule rule = classes()
                    .that().resideInAPackage("com.globo.subscription.adapter.outbound.lock")
                    .and().haveSimpleNameEndingWith("Adapter")
                    .should().implement(
                            com.tngtech.archunit.core.domain.JavaClass.Predicates
                                    .resideInAPackage("com.globo.subscription.application.port.."));

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Payment adapters should implement port interfaces")
        void paymentAdaptersShouldImplementPorts() {
            ArchRule rule = classes()
                    .that().resideInAPackage("com.globo.subscription.adapter.outbound.payment")
                    .and().haveSimpleNameEndingWith("Adapter")
                    .should().implement(
                            com.tngtech.archunit.core.domain.JavaClass.Predicates
                                    .resideInAPackage("com.globo.subscription.application.port.."));

            rule.check(importedClasses);
        }
    }
}
