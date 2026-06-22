package com.github.calcifux.mailabletoolkit.quarkus.core;

import com.github.calcifux.mailabletoolkit.MailerRegistry;
import com.github.calcifux.mailabletoolkit.quarkus.Mail;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-tests the pure wiring logic of {@link MailCdiService} without a CDI container or a running Redis:
 * mailer discovery from {@code mailable-toolkit.mailers.*}, registry assembly + default resolution, the
 * Pebble preview render, and that {@code startup()} initializes the static {@link Mail} facade. The Redis
 * Streams worker (XADD/XREADGROUP/XACK against a live broker) is exercised in the backend integration phase.
 */
class MailCdiServiceTest {

    private MailCdiService service(TestStubs.MapConfig config) {
        return service(config, "primary");
    }

    private MailCdiService service(TestStubs.MapConfig config, String defaultMailer) {
        return new MailCdiService(
                config,
                new TestStubs.EmptyRedisInstance(),
                Optional.of(""),                      // template.prefix
                ".peb",                               // template.suffix
                Optional.of("noreply@calcifux.dev"),  // from.email
                Optional.of("Calcifux"),              // from.name
                Optional.of(defaultMailer),           // default-mailer
                "inmemory",    // queue.driver
                List.of("default", "priority"),
                "default",
                3, 2_000L, 300_000L,
                "mailable", "mailable-workers", 2_000L);
    }

    private TestStubs.MapConfig twoMailers() {
        return new TestStubs.MapConfig()
                .put("mailable-toolkit.mailers.primary.host", "smtp.internal")
                .put("mailable-toolkit.mailers.primary.port", "587")
                .put("mailable-toolkit.mailers.primary.encryption", "starttls")
                .put("mailable-toolkit.mailers.billing.host", "smtp.vendor.com")
                .put("mailable-toolkit.mailers.billing.port", "465")
                .put("mailable-toolkit.mailers.billing.encryption", "ssl")
                .put("mailable-toolkit.mailers.billing.from-email", "billing@calcifux.dev");
    }

    @Test
    void discovers_mailer_names_from_config_keys() {
        MailCdiService service = service(twoMailers());
        Set<String> names = service.discoverMailerNames();
        assertThat(names).containsExactlyInAnyOrder("primary", "billing");
    }

    @Test
    void builds_a_registry_with_the_configured_default() {
        MailCdiService service = service(twoMailers());
        MailerRegistry registry = service.buildRegistry();
        assertThat(registry.defaultName()).isEqualTo("primary");
        // both named SMTP transports resolve
        assertThat(registry.resolve("primary").name()).isEqualTo("primary");
        assertThat(registry.resolve("billing").name()).isEqualTo("billing");
        // billing carries its own from-email override
        assertThat(registry.resolve("billing").fromEmail()).isEqualTo("billing@calcifux.dev");
    }

    @Test
    void falls_back_to_a_log_transport_when_no_mailers_configured() {
        MailCdiService service = service(new TestStubs.MapConfig(), "");
        MailerRegistry registry = service.buildRegistry();
        assertThat(registry.defaultName()).isEqualTo("log");
    }

    @Test
    void startup_renders_a_preview_and_wires_the_static_facade() {
        MailCdiService service = service(twoMailers());
        service.startup();

        String html = service.preview(new WelcomeMail("Calcifux"));
        assertThat(html).contains("Welcome, Calcifux");

        // the static facade is now usable (same render path)
        assertThat(Mail.preview(new WelcomeMail("Static"))).contains("Welcome, Static");

        service.onStop();
    }
}
