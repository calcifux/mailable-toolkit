package com.github.calcifux.mailabletoolkit.pebble;

import com.github.calcifux.mailabletoolkit.TemplateRenderer;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.loader.StringLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;

/**
 * Pebble adapter. Templates resolve from the classpath as {@code <prefix><ref><suffix>}, so
 * {@code {% extends "mail/base" %}} / {@code {% block %}} inheritance just works (Jinja-like, the
 * closest to the milpa templates). Auto-escapes HTML by default. Supports inline (string) templates.
 */
public class PebbleTemplateRenderer implements TemplateRenderer {

    private final PebbleEngine fileEngine;
    private final PebbleEngine stringEngine;

    /** Defaults: classpath root, {@code .peb} suffix. */
    public PebbleTemplateRenderer() {
        this("", ".peb");
    }

    public PebbleTemplateRenderer(String prefix, String suffix) {
        ClasspathLoader loader = new ClasspathLoader();
        if (prefix != null && !prefix.isBlank()) {
            loader.setPrefix(prefix);
        }
        if (suffix != null && !suffix.isBlank()) {
            loader.setSuffix(suffix);
        }
        this.fileEngine = new PebbleEngine.Builder().loader(loader).build();
        this.stringEngine = new PebbleEngine.Builder().loader(new StringLoader()).build();
    }

    @Override
    public String render(String templateRef, Map<String, Object> model, Locale locale) {
        return evaluate(fileEngine.getTemplate(templateRef), model, locale);
    }

    @Override
    public String renderInline(String templateSource, Map<String, Object> model, Locale locale) {
        return evaluate(stringEngine.getTemplate(templateSource), model, locale);
    }

    @Override
    public boolean supportsInline() {
        return true;
    }

    @Override
    public String engineId() {
        return "pebble";
    }

    private String evaluate(PebbleTemplate template, Map<String, Object> model, Locale locale) {
        Map<String, Object> context = model == null ? Map.of() : model;
        StringWriter writer = new StringWriter();
        try {
            if (locale != null) {
                template.evaluate(writer, context, locale);
            } else {
                template.evaluate(writer, context);
            }
        } catch (IOException e) {
            throw new TerminalMailException("Pebble render failed: " + e.getMessage(), e);
        }
        return writer.toString();
    }
}
