package com.github.calcifux.mailabletoolkit.thymeleaf;

import com.github.calcifux.mailabletoolkit.TemplateRenderer;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Locale;
import java.util.Map;

/**
 * Thymeleaf adapter. Named templates resolve from the classpath ({@code <prefix><ref><suffix>}, HTML
 * mode, escapes by default); layout/inheritance via {@code th:insert}/{@code th:replace} fragments,
 * which the same resolver finds. A {@link StringTemplateResolver} (lower priority) handles inline
 * sources, so {@link #renderInline} works too.
 */
public class ThymeleafTemplateRenderer implements TemplateRenderer {

    private final TemplateEngine engine;

    /** Defaults: classpath root, {@code .html} suffix. */
    public ThymeleafTemplateRenderer() {
        this("", ".html");
    }

    public ThymeleafTemplateRenderer(String prefix, String suffix) {
        ClassLoaderTemplateResolver fileResolver = new ClassLoaderTemplateResolver();
        fileResolver.setPrefix(prefix == null ? "" : prefix);
        fileResolver.setSuffix(suffix == null ? ".html" : suffix);
        fileResolver.setTemplateMode(TemplateMode.HTML);
        fileResolver.setCharacterEncoding("UTF-8");
        fileResolver.setCacheable(true);
        fileResolver.setCheckExistence(true);
        fileResolver.setOrder(1);

        StringTemplateResolver stringResolver = new StringTemplateResolver();
        stringResolver.setOrder(2);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(fileResolver);
        templateEngine.addTemplateResolver(stringResolver);
        this.engine = templateEngine;
    }

    @Override
    public String render(String templateRef, Map<String, Object> model, Locale locale) {
        return engine.process(templateRef, context(model, locale));
    }

    @Override
    public String renderInline(String templateSource, Map<String, Object> model, Locale locale) {
        // The file resolver checks existence and misses on a raw source → the StringTemplateResolver renders it.
        return engine.process(templateSource, context(model, locale));
    }

    @Override
    public boolean supportsInline() {
        return true;
    }

    @Override
    public String engineId() {
        return "thymeleaf";
    }

    private Context context(Map<String, Object> model, Locale locale) {
        Context context = new Context(locale == null ? Locale.getDefault() : locale);
        if (model != null) {
            context.setVariables(model);
        }
        return context;
    }
}
