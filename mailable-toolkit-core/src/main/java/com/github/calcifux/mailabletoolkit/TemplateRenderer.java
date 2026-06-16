package com.github.calcifux.mailabletoolkit;

import java.util.Locale;
import java.util.Map;

/**
 * The multi-templating SEAM. One port; each engine (Pebble, Thymeleaf, FreeMarker, …) is an adapter
 * module. The core and the Mailable never import an engine — switching engines is a dependency swap +
 * one property, the Mailable code never changes. Template INHERITANCE ({@code extends}/layout/blocks,
 * à la Jinja/Blade) is the engine's job: it resolves {@code template} by name from the classpath so
 * parents/layouts are found.
 */
public interface TemplateRenderer {

    /** Render a template referenced by logical name (engine resolves it, inheritance included). */
    String render(String templateRef, Map<String, Object> model, Locale locale);

    /** Render a raw inline template source. Only engines where {@link #supportsInline()} is true. */
    default String renderInline(String templateSource, Map<String, Object> model, Locale locale) {
        throw new UnsupportedOperationException(engineId() + " does not support inline templates");
    }

    /** Whether this engine can render a raw source string (for DB-stored / inline templates). */
    default boolean supportsInline() {
        return false;
    }

    /** Stable id of the engine ({@code "pebble"}, {@code "thymeleaf"}, {@code "freemarker"}, …). */
    String engineId();
}
