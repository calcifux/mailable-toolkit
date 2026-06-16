package com.github.calcifux.mailabletoolkit.thymeleaf;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the pebble engine's three-test shape, adapted to Thymeleaf's mechanics:
 * (1) template inheritance, (2) inline source, (3) HTML escaping by default.
 */
class ThymeleafTemplateRendererTest {

    private final ThymeleafTemplateRenderer renderer = new ThymeleafTemplateRenderer();

    @Test
    void rendersWithTemplateInheritance() {
        // mail/welcome.html "inherits" mail/base.html via a parameterized layout fragment
        // (Thymeleaf's real inheritance mechanism — it has no {% extends %}).
        String html = renderer.render("mail/welcome", Map.of("name", "Calcifux"), null);

        assertThat(html).contains("id=\"content\""); // the shell came from the base layout
        assertThat(html).contains("Hi Calcifux");     // the child fragment filled with the model var
    }

    @Test
    void rendersInlineSource() {
        // No named template exists, so the StringTemplateResolver renders the raw markup.
        // The source must be valid Thymeleaf HTML (a th:text attribute), not Pebble {{ }} syntax.
        assertThat(renderer.supportsInline()).isTrue();

        String html = renderer.renderInline("<p th:text=\"${name}\"></p>", Map.of("name", "Calcifux"), null);
        assertThat(html).contains("Calcifux");
    }

    @Test
    void escapesHtmlByDefault() {
        // th:text escapes its output, so markup in the model value is neutralized rather than
        // injected as live HTML — the on-by-default protection we want from the email engine.
        String html = renderer.renderInline("<p th:text=\"${value}\"></p>", Map.of("value", "<b>Calcifux & co</b>"), null);

        assertThat(html).contains("&lt;");
        assertThat(html).contains("&amp;");
        assertThat(html).doesNotContain("<b>");
    }
}
