package net.ofatech.controlcore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.domain.ports.platform.ModPlatform;

@AnalyzeClasses(packages = "net.ofatech.controlcore", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "net.ofatech.controlcore";
    private static final String DOMAIN = ROOT + ".domain..";
    private static final String API = ROOT + ".api..";
    private static final String APPLICATION = ROOT + ".application..";
    private static final String INFRASTRUCTURE = ROOT + ".infrastructure..";
    private static final String PLATFORMS = ROOT + ".infrastructure.platforms..";
    private static final String MAIN = ROOT + ".Main";
    private static final String AUTO_SERVICE = "com.google.auto.service.AutoService";

    private static final List<Class<?>> COMPONENT_TYPES =
            List.of(ModCommand.class, ModEventHandler.class, FeatureModule.class, ModTask.class, ModPlatform.class);

    @ArchTest
    static final ArchRule domainDependsOnNothing = classes()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", DOMAIN);

    @ArchTest
    static final ArchRule apiDependsOnlyOnDomain = classes()
            .that()
            .resideInAPackage(API)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", API, DOMAIN);

    @ArchTest
    static final ArchRule applicationDependsOnlyOnDomainAndApi = classes()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", API, DOMAIN, APPLICATION, "com.google.auto.service..");

    @ArchTest
    static final ArchRule systemAndPersistenceDependOnlyOnDomain = classes()
            .that()
            .resideInAnyPackage(ROOT + ".infrastructure.system..", ROOT + ".infrastructure.persistence..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    DOMAIN,
                    ROOT + ".infrastructure.system..",
                    ROOT + ".infrastructure.persistence..",
                    "com.google.gson..");

    @ArchTest
    static final ArchRule hytaleOnlyInPlatformsAndMain = noClasses()
            .that()
            .resideOutsideOfPackage(PLATFORMS)
            .and()
            .doNotHaveFullyQualifiedName(MAIN)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.hypixel..");

    @ArchTest
    static final ArchRule nothingDependsOnPlatforms = noClasses()
            .that()
            .resideOutsideOfPackage(PLATFORMS)
            .and()
            .doNotHaveFullyQualifiedName(MAIN)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PLATFORMS);

    @ArchTest
    static final ArchRule platformsAreIndependent =
            slices().matching(ROOT + ".infrastructure.platforms.(*)..").should().notDependOnEachOther();

    @ArchTest
    static final ArchRule onlyTheHytalePlatformUsesHytale = noClasses()
            .that()
            .resideInAPackage(PLATFORMS)
            .and()
            .resideOutsideOfPackage(ROOT + ".infrastructure.platforms.hytale..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.hypixel..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modPlatformDescriptorsAreSafeToLoad = classes()
            .that()
            .implement(ModPlatform.class)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", ROOT + "..", "com.google.auto.service..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule mainOnlyDelegates = classes()
            .that()
            .haveFullyQualifiedName(MAIN)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "javax.annotation..",
                    "com.hypixel.hytale.server.core.plugin..",
                    ROOT + ".application.runtime..",
                    ROOT + ".infrastructure.platforms.hytale")
            .orShould()
            .haveFullyQualifiedName(MAIN);

    @ArchTest
    static final ArchRule componentsAreIndexedAndInjectable =
            classes().that(areConcreteComponents()).should(beAnnotatedWithAutoServiceAndHaveOnePublicConstructor());

    @ArchTest
    static final ArchRule classesAreFinal = classes()
            .that()
            .resideInAPackage(ROOT + "..")
            .and()
            .areNotInterfaces()
            .and()
            .areNotEnums()
            .and()
            .areNotAnonymousClasses()
            .should()
            .haveModifier(JavaModifier.FINAL);

    @ArchTest
    static final ArchRule noMutableStaticState = fields().that()
            .areDeclaredInClassesThat()
            .resideInAPackage(ROOT + "..")
            .and()
            .areStatic()
            .should()
            .beFinal();

    private static DescribedPredicate<JavaClass> areConcreteComponents() {
        return DescribedPredicate.describe(
                "are concrete components",
                javaClass -> !javaClass.isInterface()
                        && !javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
                        && COMPONENT_TYPES.stream().anyMatch(javaClass::isAssignableTo));
    }

    private static ArchCondition<JavaClass> beAnnotatedWithAutoServiceAndHaveOnePublicConstructor() {
        return new ArchCondition<>("be annotated with @AutoService and have exactly one public constructor") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!javaClass.isAnnotatedWith(AUTO_SERVICE)) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " is not annotated with @AutoService, so it never registers"));
                }
                long publicConstructors = javaClass.getConstructors().stream()
                        .filter(c -> c.getModifiers().contains(JavaModifier.PUBLIC))
                        .count();
                if (publicConstructors != 1) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass, javaClass.getName() + " has " + publicConstructors + " public constructors"));
                }
            }
        };
    }
}
