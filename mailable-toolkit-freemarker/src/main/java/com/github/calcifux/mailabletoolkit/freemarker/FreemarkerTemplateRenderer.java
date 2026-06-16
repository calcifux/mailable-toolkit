package com.github.calcifux.mailabletoolkit.freemarker;

import com.github.calcifux.mailabletoolkit.TemplateRenderer;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;

/**
 * FreeMarker adapter. Named templates resolve from the classpath ({@code <prefix><ref><suffix>}); reuse
 * via macros + {@code <#include>}/{@code <#import>}. HTML output format is FORCED so auto-escaping is
 * the secure default (FreeMarker does NOT escape by default). A {@link StringTemplateLoader} backs
 * inline rendering.
 */
public class FreemarkerTemplateRenderer implements TemplateRenderer {

    private final Configuration configuration;
    private final StringTemplateLoader stringLoader = new StringTemplateLoader();
    private final String suffix;

    /** Defaults: classpath root, {@code .ftlh} suffix. */
    public FreemarkerTemplateRenderer() {
        this("", ".ftlh");
    }

    public FreemarkerTemplateRenderer(String prefix, String suffix) {
        this.suffix = suffix == null ? ".ftlh" : suffix;
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        ClassTemplateLoader classLoader =
                new ClassTemplateLoader(Thread.currentThread().getContextClassLoader(), prefix == null ? "" : prefix);
        cfg.setTemplateLoader(new MultiTemplateLoader(new TemplateLoader[]{classLoader, stringLoader}));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setOutputFormat(HTMLOutputFormat.INSTANCE);
        cfg.setRecognizeStandardFileExtensions(false);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        this.configuration = cfg;
    }

    @Override
    public String render(String templateRef, Map<String, Object> model, Locale locale) {
        return process(templateRef + suffix, model, locale);
    }

    @Override
    public String renderInline(String templateSource, Map<String, Object> model, Locale locale) {
        String key = "inline-" + Integer.toHexString(templateSource.hashCode());
        stringLoader.putTemplate(key, templateSource);
        return process(key, model, locale);
    }

    @Override
    public boolean supportsInline() {
        return true;
    }

    @Override
    public String engineId() {
        return "freemarker";
    }

    private String process(String name, Map<String, Object> model, Locale locale) {
        try {
            Template template = locale != null
                    ? configuration.getTemplate(name, locale)
                    : configuration.getTemplate(name);
            StringWriter writer = new StringWriter();
            template.process(model == null ? Map.of() : model, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new TerminalMailException("FreeMarker render failed: " + e.getMessage(), e);
        }
    }
}
