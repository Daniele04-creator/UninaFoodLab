package it.uninafoodlab.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayeredArchitectureTest {

    private static final String ROOT_PACKAGE = "it.uninafoodlab";

    @Test
    void functionalPackagesWithinEachLayerHaveNoDependencyCycles() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT_PACKAGE);
        for (String layer : new String[] {"domain", "application", "infrastructure", "presentation"}) {
            slices().matching(ROOT_PACKAGE + "." + layer + ".(*)..")
                    .should().beFreeOfCycles().check(classes);
        }
    }

    @Test
    void domainRemainsIndependentOfUiAndPersistenceLibraries() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT_PACKAGE);

        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("javafx..", "java.sql..", "javax.sql..", "org.postgresql..")
                .check(classes);
    }

    @Test
    void useCasesAccessPersistenceThroughPorts() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT_PACKAGE);

        noClasses().that().resideInAPackage("..application..")
                .and().resideOutsideOfPackage("..application.bootstrap..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "..presentation..", "javafx..", "java.sql..", "javax.sql..")
                .check(classes);
    }

    @Test
    void controllersDoNotBypassApplicationUseCases() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT_PACKAGE);

        noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "java.sql..", "javax.sql..")
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnOuterLayers() {
        var classes = new ClassFileImporter().importPackages(ROOT_PACKAGE);

        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..application..", "..infrastructure..", "..presentation..")
                .check(classes);
    }

    @Test
    void presentationDoesNotAccessJdbcDirectly() {
        var classes = new ClassFileImporter().importPackages(ROOT_PACKAGE);

        noClasses().that().resideInAPackage("..presentation..")
                .should().dependOnClassesThat()
                .resideInAPackage("java.sql..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
