package com.github.calcifux.mailabletoolkit.pebble;

import com.github.calcifux.mailabletoolkit.TemplateRenderer;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.loader.StringLoader;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Pebble adapter. Templates resolve from the classpath as {@code <prefix><ref><suffix>}, so
 * {@code {% extends "mail/base" %}} / {@code {% block %}} inheritance just works (Jinja-like, the
 * closest to the milpa templates). Auto-escapes HTML by default. Supports inline (string) templates.
 *
 * <p>Both the template lookup and the {@code i18n(bundle, key)} function are bound to the thread context
 * classloader captured at construction (the consuming app's classloader). This matters under Quarkus
 * dev mode, where the app's resources (templates + {@code .properties} bundles) live in the hot-reload
 * classloader — a child of the base classloader where this toolkit (and Pebble) live; Pebble's stock
 * ClasspathLoader and i18n function use Pebble's own classloader and can't see them there. Packaged
 * JVM/native have a single classloader, so this is transparent.</p>
 */
public class PebbleTemplateRenderer implements TemplateRenderer {

    private final PebbleEngine fileEngine;
    private final PebbleEngine stringEngine;

    /** Defaults: classpath root, {@code .peb} suffix. */
    public PebbleTemplateRenderer() {
        this("", ".peb");
    }

    public PebbleTemplateRenderer(String prefix, String suffix) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = PebbleTemplateRenderer.class.getClassLoader();
        }
        ClasspathLoader loader = new ClasspathLoader(cl);
        if (prefix != null && !prefix.isBlank()) {
            loader.setPrefix(prefix);
        }
        if (suffix != null && !suffix.isBlank()) {
            loader.setSuffix(suffix);
        }
        ClassLoaderI18nExtension i18n = new ClassLoaderI18nExtension(cl);
        this.fileEngine = new PebbleEngine.Builder().loader(loader).extension(i18n).build();
        this.stringEngine = new PebbleEngine.Builder().loader(new StringLoader()).extension(i18n).build();
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

    /**
     * Overrides Pebble's built-in {@code i18n(bundle, key)} function with one that resolves the
     * {@link ResourceBundle} through an explicit classloader (the app's), instead of Pebble's own. Without
     * this, localized mail copy breaks under Quarkus dev mode with "Can't find bundle ...".
     */
    private static final class ClassLoaderI18nExtension extends AbstractExtension {
        private final ClassLoader classLoader;

        ClassLoaderI18nExtension(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public Map<String, Function> getFunctions() {
            return Map.of("i18n", new ClassLoaderI18nFunction(classLoader));
        }
    }

    private static final class ClassLoaderI18nFunction implements Function {
        private final ClassLoader classLoader;

        ClassLoaderI18nFunction(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public List<String> getArgumentNames() {
            return List.of("bundle", "key");
        }

        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context,
                              int lineNumber) {
            String bundle = (String) args.get("bundle");
            String key = (String) args.get("key");
            Locale locale = context.getLocale() != null ? context.getLocale() : Locale.getDefault();
            return loadBundle(bundle, locale).getString(key);
        }

        private ResourceBundle loadBundle(String bundle, Locale locale) {
            // Prefer the live thread-context classloader (the app CL in request threads), then the captured one.
            ClassLoader live = Thread.currentThread().getContextClassLoader();
            if (live != null && live != classLoader) {
                try {
                    return ResourceBundle.getBundle(bundle, locale, live);
                } catch (java.util.MissingResourceException ignored) {
                    // fall through to the captured classloader
                }
            }
            return ResourceBundle.getBundle(bundle, locale, classLoader);
        }
    }
}
