package it.uninafoodlab.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ChefDesktopBoundaryTest {
    @Test
    void chefWorkspacesDoNotDependOnParticipantSelfServiceUseCases() {
        var classes = new ClassFileImporter().importPackages("it.uninafoodlab.presentation");
        noClasses().that().resideInAnyPackage("it.uninafoodlab.presentation.shell..",
                        "it.uninafoodlab.presentation.course..", "it.uninafoodlab.presentation.dashboard..")
                .should().dependOnClassesThat()
                .resideInAPackage("it.uninafoodlab.application.enrollment..")
                .check(classes);
    }

    @Test
    void obsoleteParticipantWorkspacesAreNotPackaged() {
        for (var resource : new String[] {
                "/it/uninafoodlab/presentation/dashboard/participant-dashboard-workspace.fxml",
                "/it/uninafoodlab/presentation/course/published-course-workspace.fxml",
                "/it/uninafoodlab/presentation/dashboard/ParticipantDashboardWorkspaceController.class",
                "/it/uninafoodlab/presentation/course/PublishedCourseWorkspaceController.class"}) {
            assertThat(getClass().getResource(resource)).as(resource).isNull();
        }
    }
}
