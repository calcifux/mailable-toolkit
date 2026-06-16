package com.github.calcifux.mailabletoolkit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailerRegistryTest {

    @Test
    void static_mailer_wins_and_the_provider_is_not_consulted() {
        AtomicInteger providerCalls = new AtomicInteger();
        MailerProvider provider = name -> {
            providerCalls.incrementAndGet();
            return new LogMailTransport(name);
        };
        MailerRegistry registry = new MailerRegistry(
                List.of(new LogMailTransport("primary")), List.of(provider), "primary", 60_000L);

        assertThat(registry.resolve("primary").name()).isEqualTo("primary");
        assertThat(providerCalls.get()).isZero();
    }

    @Test
    void provider_resolves_an_unknown_name_then_caches_until_evicted() {
        AtomicInteger providerCalls = new AtomicInteger();
        MailerProvider provider = name -> {
            providerCalls.incrementAndGet();
            return "db".equals(name) ? new LogMailTransport("db") : null;
        };
        MailerRegistry registry = new MailerRegistry(
                List.of(new LogMailTransport("primary")), List.of(provider), "primary", 60_000L);

        assertThat(registry.resolve("db").name()).isEqualTo("db");
        assertThat(registry.resolve("db").name()).isEqualTo("db");
        assertThat(providerCalls.get()).isEqualTo(1);   // second hit served from cache

        registry.evict("db");
        assertThat(registry.resolve("db").name()).isEqualTo("db");
        assertThat(providerCalls.get()).isEqualTo(2);   // re-resolved after evict
    }

    @Test
    void unknown_name_no_provider_match_is_terminal() {
        MailerRegistry registry = new MailerRegistry(
                List.of(new LogMailTransport("primary")), List.of(name -> null), "primary", 0L);

        assertThatThrownBy(() -> registry.resolve("nope"))
                .isInstanceOf(TerminalMailException.class)
                .hasMessageContaining("No mailer named 'nope'");
    }

    @Test
    void all_dynamic_with_no_default_requires_an_explicit_name() {
        MailerRegistry registry = new MailerRegistry(
                List.of(), List.of(name -> new LogMailTransport(name)), null, 0L);

        assertThat(registry.resolve("anything").name()).isEqualTo("anything");
        assertThatThrownBy(() -> registry.resolve(null)).isInstanceOf(TerminalMailException.class);
    }
}
