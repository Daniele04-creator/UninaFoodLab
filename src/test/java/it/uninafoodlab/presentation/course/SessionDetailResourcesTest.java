package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;

class SessionDetailResourcesTest {
    @Test
    void frozenMenuKeepsRequirementsReachableAndSessionNavigationOutsideScrollingContent() throws Exception {
        try (var source = getClass().getResourceAsStream("/it/uninafoodlab/presentation/course/course-workspace.fxml")) {
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source);
            var xpath = XPathFactory.newInstance().newXPath();
            assertThat(xpath.evaluate("count(//*[@*[name()='fx:id']='recipeAddPanel']//*[@*[name()='fx:id']='recipeRequirementsButton'])", document)).isEqualTo("0");
            assertThat(xpath.evaluate("count(//ScrollPane//*[@*[name()='fx:id']='feedbackLabel'])", document)).isEqualTo("0");
            assertThat(xpath.evaluate("count(//ScrollPane//*[@*[name()='fx:id']='sessionDetailHeader'])", document)).isEqualTo("0");
            assertThat(xpath.evaluate("count(//*[@*[name()='fx:id']='sessionDetailHeader'])", document)).isEqualTo("1");
            assertThat(xpath.evaluate("count(//*[@*[name()='fx:id']='sessionDetailHeader']//ToggleButton)", document)).isEqualTo("3");
        }
    }
}
