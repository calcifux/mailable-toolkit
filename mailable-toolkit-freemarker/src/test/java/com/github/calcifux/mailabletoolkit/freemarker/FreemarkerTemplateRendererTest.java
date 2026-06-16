package com.github.calcifux.mailabletoolkit.freemarker;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the Pebble renderer test: three behaviours that matter for mail templating.
 * <ol>
 *   <li>template reuse/"inheritance" (FreeMarker does it with a layout macro + {@code <#import>});</li>
 *   <li>inline (string) templates;</li>
 *   <li>HTML auto-escaping, which FreeMarker only gives us because the renderer FORCES the HTML
 *       output format (FreeMarker does not escape by default).</li>
 * </ol>
 */
class FreemarkerTemplateRendererTest {

    private final FreemarkerTemplateRenderer renderer = new FreemarkerTemplateRenderer();

    @Test
    void rendersWithTemplateInheritance() {
        // mail/welcome.ftlh imports mail/base.ftlh and calls its layout macro, filling the body.
        // render() appends the ".ftlh" suffix itself, so the ref is passed WITHOUT it.
        String html = renderer.render("mail/welcome", Map.of("name", "Calcifux"), null);

        assertThat(html).contains("id=\"content\"");   // came from the base layout shell
        assertThat(html).contains("Hi Calcifux");      // came from the child body + model var
    }

    @Test
    void rendersInlineSource() {
        assertThat(renderer.supportsInline()).isTrue();

        String html = renderer.renderInline("Hello ${name}", Map.of("name", "Calcifux"), null);
        assertThat(html).isEqualTo("Hello Calcifux");
    }

    @Test
    void escapesHtmlByDefault() {
        // The forced HTMLOutputFormat means ${...} interpolations are escaped, so markup in the
        // model can never break out into the rendered document.
        String html = renderer.renderInline("${value}", Map.of("value", "<b>Calcifux & co</b>"), null);

        assertThat(html).contains("&lt;");      // '<' was escaped
        assertThat(html).contains("&amp;");     // '&' was escaped
        assertThat(html).doesNotContain("<b>"); // the raw markup never survives
    }
}
