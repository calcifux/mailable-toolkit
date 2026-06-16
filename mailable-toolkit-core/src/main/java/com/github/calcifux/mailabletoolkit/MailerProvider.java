package com.github.calcifux.mailabletoolkit;

/**
 * Dynamic source of {@link MailTransport}s — resolves a mailer NAME to a transport at runtime, e.g. from
 * a database table of SMTP servers. Plugged into {@link MailerRegistry} as a fallback: resolution is
 * <b>static yml mailers → cache → providers → default</b>, so config-declared mailers still win and a
 * provider is only consulted for names it hasn't seen.
 *
 * <pre>{@code
 * // a provider backed by your own table (build the transport with SmtpMailTransport.smtp(...)):
 * MailerProvider fromDb = name -> {
 *     SmtpRow row = repository.findByName(name);
 *     return row == null ? null
 *          : SmtpMailTransport.smtp(name, row.host(), row.port(), row.user(), row.pass(),
 *                                   row.encryption(), row.fromEmail(), row.fromName());
 * };
 * }</pre>
 *
 * <p>Return {@code null} for a name this provider doesn't own (the registry tries the next provider).
 * Results are cached by the registry (TTL configurable; {@code registry.evict(name)} on a row change).</p>
 */
@FunctionalInterface
public interface MailerProvider {

    /** The transport for this mailer name, or {@code null} if this provider doesn't know it. */
    MailTransport resolve(String name);
}
