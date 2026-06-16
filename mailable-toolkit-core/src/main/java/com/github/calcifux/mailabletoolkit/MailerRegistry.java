package com.github.calcifux.mailabletoolkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of named {@link MailTransport}s ("mailers"). ONE resolution rule so a mail's SMTP is picked
 * the same way everywhere (the single-resolver lesson): an explicit per-send name &gt; the
 * {@code Envelope.mailer} pin &gt; the configured default. Only the NAME is ever passed around;
 * credentials live in each transport, configured once.
 */
public class MailerRegistry {

    private final Map<String, MailTransport> transports = new LinkedHashMap<>();
    private final String defaultName;

    public MailerRegistry(List<MailTransport> transports, String configuredDefault) {
        for (MailTransport t : transports) {
            this.transports.put(t.name(), t);
        }
        this.defaultName = resolveDefault(configuredDefault);
    }

    /** Resolve a transport by name; null/blank → the default. Unknown name → terminal failure. */
    public MailTransport resolve(String name) {
        String resolved = (name != null && !name.isBlank()) ? name : defaultName;
        MailTransport transport = transports.get(resolved);
        if (transport == null) {
            throw new TerminalMailException("No mailer named '" + resolved + "' — configured: " + transports.keySet());
        }
        return transport;
    }

    public String defaultName() {
        return defaultName;
    }

    // effective default: explicit if set; else the single mailer; else it's ambiguous.
    private String resolveDefault(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (transports.size() == 1) {
            return transports.keySet().iterator().next();
        }
        if (transports.isEmpty()) {
            throw new MailToolkitException("No mail transports configured");
        }
        throw new MailToolkitException(
                "Multiple mailers configured but no default-mailer set: " + transports.keySet());
    }
}
