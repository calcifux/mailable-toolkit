package com.github.calcifux.mailabletoolkit.spring;

import com.github.calcifux.mailabletoolkit.LogMailTransport;
import com.github.calcifux.mailabletoolkit.MailRenderer;
import com.github.calcifux.mailabletoolkit.MailTransport;
import com.github.calcifux.mailabletoolkit.Mailer;
import com.github.calcifux.mailabletoolkit.MailerRegistry;
import com.github.calcifux.mailabletoolkit.TemplateRenderer;
import com.github.calcifux.mailabletoolkit.freemarker.FreemarkerTemplateRenderer;
import com.github.calcifux.mailabletoolkit.pebble.PebbleTemplateRenderer;
import com.github.calcifux.mailabletoolkit.queue.InMemoryMailQueue;
import com.github.calcifux.mailabletoolkit.queue.MailQueue;
import com.github.calcifux.mailabletoolkit.queue.RetryPolicy;
import com.github.calcifux.mailabletoolkit.thymeleaf.ThymeleafTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

/**
 * Wires the toolkit from {@code mailable-toolkit.*}: picks the templating engine (Pebble default; any
 * adapter on the classpath, {@code template.engine} chooses when several), builds one
 * {@link SmtpMailTransport} per named mailer into the {@link MailerRegistry} (multi-SMTP), exposes the
 * {@link Mailer}, the chosen {@link MailQueue} (in-memory or Redis), and initializes the static
 * {@link Mail} facade. Everything is {@code @ConditionalOnMissingBean}, so an app can override any piece.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(MailableToolkitProperties.class)
public class MailableToolkitAutoConfiguration {

    // --- Templating engine (explicit mailable-toolkit.template.engine wins; else first on classpath) ---

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(PebbleTemplateRenderer.class)
    @ConditionalOnProperty(prefix = "mailable-toolkit.template", name = "engine", havingValue = "pebble", matchIfMissing = true)
    @Order(1)
    static class PebbleRendererConfig {
        @Bean
        @ConditionalOnMissingBean(TemplateRenderer.class)
        TemplateRenderer pebbleTemplateRenderer(MailableToolkitProperties props) {
            return new PebbleTemplateRenderer(props.getTemplate().getPrefix(), suffixOr(props, ".peb"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ThymeleafTemplateRenderer.class)
    @ConditionalOnProperty(prefix = "mailable-toolkit.template", name = "engine", havingValue = "thymeleaf", matchIfMissing = true)
    @Order(2)
    static class ThymeleafRendererConfig {
        @Bean
        @ConditionalOnMissingBean(TemplateRenderer.class)
        TemplateRenderer thymeleafTemplateRenderer(MailableToolkitProperties props) {
            return new ThymeleafTemplateRenderer(props.getTemplate().getPrefix(), suffixOr(props, ".html"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(FreemarkerTemplateRenderer.class)
    @ConditionalOnProperty(prefix = "mailable-toolkit.template", name = "engine", havingValue = "freemarker", matchIfMissing = true)
    @Order(3)
    static class FreemarkerRendererConfig {
        @Bean
        @ConditionalOnMissingBean(TemplateRenderer.class)
        TemplateRenderer freemarkerTemplateRenderer(MailableToolkitProperties props) {
            return new FreemarkerTemplateRenderer(props.getTemplate().getPrefix(), suffixOr(props, ".ftlh"));
        }
    }

    // --- Core orchestration ---

    @Bean
    @ConditionalOnMissingBean
    MailRenderer mailRenderer(TemplateRenderer renderer) {
        return new MailRenderer(renderer);
    }

    @Bean
    @ConditionalOnMissingBean
    MailerRegistry mailerRegistry(MailableToolkitProperties props) {
        List<MailTransport> transports = new ArrayList<>();
        props.getMailers().forEach((name, mailer) -> transports.add(buildSmtp(name, mailer, props.getFrom())));
        if (transports.isEmpty()) {
            log.warn("[mailable-toolkit] no SMTP mailers configured (mailable-toolkit.mailers.*) — "
                    + "falling back to a log transport: mail will be LOGGED, not sent");
            transports.add(new LogMailTransport("log"));
        }
        return new MailerRegistry(transports, props.getDefaultMailer());
    }

    @Bean
    @ConditionalOnMissingBean
    Mailer mailer(MailRenderer renderer, MailerRegistry registry, MailableToolkitProperties props) {
        return new Mailer(renderer, registry, props.getFrom().getEmail(), props.getFrom().getName());
    }

    // --- Queue: in-memory (default) or Redis Streams ---

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MailQueue.class)
    @ConditionalOnProperty(prefix = "mailable-toolkit.queue", name = "driver", havingValue = "inmemory", matchIfMissing = true)
    InMemoryMailQueue inMemoryMailQueue(Mailer mailer, MailableToolkitProperties props) {
        return new InMemoryMailQueue(mailer::dispatch, retryPolicy(props));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    static class RedisQueueConfig {
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(MailQueue.class)
        @ConditionalOnProperty(prefix = "mailable-toolkit.queue", name = "driver", havingValue = "redis")
        RedisMailQueue redisMailQueue(StringRedisTemplate redis, Mailer mailer, MailableToolkitProperties props) {
            MailableToolkitProperties.Queue queue = props.getQueue();
            return new RedisMailQueue(redis, mailer::dispatch, retryPolicy(props),
                    queue.getKeyPrefix(), queue.getConsumerGroup(), new LinkedHashSet<>(queue.getQueues()));
        }
    }

    // --- Static facade init (queue optional) ---

    @Bean
    MailFacadeInitializer mailFacadeInitializer(Mailer mailer, ObjectProvider<MailQueue> queue,
                                                MailableToolkitProperties props) {
        return new MailFacadeInitializer(mailer, queue.getIfAvailable(), props.getQueue().getDefaultQueue());
    }

    /** Pushes the wired beans into the static {@link Mail} facade on startup. */
    static final class MailFacadeInitializer {
        MailFacadeInitializer(Mailer mailer, MailQueue queue, String defaultQueue) {
            Mail.init(mailer, queue, defaultQueue);
        }
    }

    // --- helpers ---

    static RetryPolicy retryPolicy(MailableToolkitProperties props) {
        MailableToolkitProperties.Queue queue = props.getQueue();
        return new RetryPolicy(queue.getMaxAttempts(), queue.getBaseBackoffMillis(), queue.getMaxBackoffMillis());
    }

    private static String suffixOr(MailableToolkitProperties props, String engineDefault) {
        String configured = props.getTemplate().getSuffix();
        return (configured != null && !configured.isBlank()) ? configured : engineDefault;
    }

    private static SmtpMailTransport buildSmtp(String name, MailableToolkitProperties.Mailer cfg,
                                               MailableToolkitProperties.From globalFrom) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.getHost());
        sender.setPort(cfg.getPort());
        if (cfg.getUsername() != null && !cfg.getUsername().isBlank()) {
            sender.setUsername(cfg.getUsername());
        }
        if (cfg.getPassword() != null && !cfg.getPassword().isBlank()) {
            sender.setPassword(cfg.getPassword());
        }

        Properties mailProps = sender.getJavaMailProperties();
        mailProps.put("mail.transport.protocol", "smtp");
        boolean auth = cfg.getUsername() != null && !cfg.getUsername().isBlank();
        mailProps.put("mail.smtp.auth", String.valueOf(auth));
        String encryption = cfg.getEncryption() == null ? "starttls" : cfg.getEncryption().toLowerCase();
        switch (encryption) {
            case "starttls" -> mailProps.put("mail.smtp.starttls.enable", "true");
            case "ssl" -> mailProps.put("mail.smtp.ssl.enable", "true");
            default -> { /* none: plain SMTP (dev / internal relay) */ }
        }

        String fromEmail = firstNonBlank(cfg.getFromEmail(), globalFrom.getEmail());
        String fromName = firstNonBlank(cfg.getFromName(), globalFrom.getName());
        return new SmtpMailTransport(name, sender, fromEmail, fromName);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
