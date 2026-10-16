package com.xjtu.iron.foundation.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Foundation 架构边界测试。
 */
class FoundationArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.xjtu.iron.foundation");

    @Test
    void foundationShouldNotDependOnUpperComponents() {
        noClasses()
                .that().resideInAPackage("com.xjtu.iron.foundation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.xjtu.iron.cache..",
                        "com.xjtu.iron.message..",
                        "com.xjtu.iron.retry..",
                        "com.xjtu.iron.lock..")
                .check(classes);
    }

    @Test
    void serializationApiShouldNotDependOnJackson() {
        noClasses()
                .that().resideInAPackage("com.xjtu.iron.foundation.serialization..")
                .and().resideOutsideOfPackage("com.xjtu.iron.foundation.serialization.jackson..")
                .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson..")
                .check(classes);
    }
}
