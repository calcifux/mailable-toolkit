package com.github.calcifux.mailabletoolkit.pebble;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PebbleTemplateRendererTest {

    private final PebbleTemplateRenderer renderer = new PebbleTemplateRenderer();

    @Test
    void rendersWithTemplateInheritance() {
        // mail/welcome.peb does {% extends "mail/base" %} and fills the content block.
        String html = renderer.render("mail/welcome", Map.of("name", "Calcifux"), null);

        assertThat(html).contains("id=\"content\"");   // came from the base layout
        assertThat(html).contains("Hi Calcifux");          // came from the child block + model var
    }

    @Test
    void rendersInlineSource() {
        String html = renderer.renderInline("Hello {{ name }}", Map.of("name", "Calcifux"), null);
        assertThat(html).isEqualTo("Hello Calcifux");
    }

    @Test
    void escapesHtmlByDefault() {
        String html = renderer.renderInline("{{ value }}", Map.of("value", "<script>"), null);
        assertThat(html).doesNotContain("<script>");
    }
}
