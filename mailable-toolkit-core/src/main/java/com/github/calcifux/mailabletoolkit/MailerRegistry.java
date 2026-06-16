package com.github.calcifux.mailabletoolkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named {@link MailTransport}s ("mailers"). ONE resolution rule so a mail's SMTP is picked the
 * same way everywhere: an explicit per-send name &gt; the {@code Envelope.mailer} pin &gt; the configured
 * default. Only the NAME is ever passed around; credentials live in each transport.
 *
 * <p>Mailers come from two places, resolved in order: <b>static</b> (declared once in config) → a short-TTL
 * <b>cache</b> → <b>{@link MailerProvider}s</b> (dynamic, e.g. a DB table of SMTP servers). So config
 * mailers always win, and a provider is consulted only for a name nobody declared statically. Call
 * {@link #evict(String)} when a dynamic mailer's backing row changes.</p>
 */
public class MailerRegistry {

    private final Map<String, MailTransport> transports = new LinkedHashMap<>();
    private final List<MailerProvider> providers;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final long cacheTtlMillis;
    private final String defaultName;

    /** Static mailers only (no dynamic providers, no cache). */
    public MailerRegistry(List<MailTransport> transports, String configuredDefault) {
        this(transports, List.of(), configuredDefault, 0L);
    }

    public MailerRegistry(List<MailTransport> transports, List<MailerProvider> providers,
                          String configuredDefault, long cacheTtlMillis) {
        for (MailTransport transport : transports) {
            this.transports.put(transport.name(), transport);
        }
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.cacheTtlMillis = cacheTtlMillis;
        this.defaultName = resolveDefault(configuredDefault);
    }

    /** Resolve a transport by name; null/blank → the default. Unknown name → terminal failure. */
    public MailTransport resolve(String name) {
        String resolved = (name != null && !name.isBlank()) ? name : defaultName;
        if (resolved == null || resolved.isBlank()) {
            throw new TerminalMailException("No mailer name given and no default-mailer configured");
        }

        MailTransport statically = transports.get(resolved);
        if (statically != null) {
            return statically;
        }

        if (!providers.isEmpty()) {
            Cached cached = cache.get(resolved);
            if (cached != null && !cached.expired()) {
                return cached.transport;
            }
            for (MailerProvider provider : providers) {
                MailTransport dynamic = provider.resolve(resolved);
                if (dynamic != null) {
                    if (cacheTtlMillis > 0) {
                        cache.put(resolved, new Cached(dynamic, System.currentTimeMillis() + cacheTtlMillis));
                    }
                    return dynamic;
                }
            }
        }

        throw new TerminalMailException("No mailer named '" + resolved + "' — static mailers: "
                + transports.keySet() + (providers.isEmpty() ? "" : " (and no provider resolved it)"));
    }

    /** Drop a cached dynamic mailer so the next send re-resolves it (call on a config/row change). */
    public void evict(String name) {
        cache.remove(name);
    }

    public void evictAll() {
        cache.clear();
    }

    public String defaultName() {
        return defaultName;
    }

    // Effective default: explicit if set; else the single static mailer; else (only providers) none —
    // the caller must name a mailer; else (several static, none chosen) it's ambiguous.
    private String resolveDefault(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (transports.size() == 1) {
            return transports.keySet().iterator().next();
        }
        if (transports.isEmpty()) {
            if (!providers.isEmpty()) {
                return null;
            }
            throw new MailToolkitException("No mail transports configured");
        }
        throw new MailToolkitException(
                "Multiple mailers configured but no default-mailer set: " + transports.keySet());
    }

    private static final class Cached {
        private final MailTransport transport;
        private final long expiryEpochMs;

        private Cached(MailTransport transport, long expiryEpochMs) {
            this.transport = transport;
            this.expiryEpochMs = expiryEpochMs;
        }

        private boolean expired() {
            return System.currentTimeMillis() > expiryEpochMs;
        }
    }
}
