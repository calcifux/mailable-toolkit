package com.github.calcifux.mailabletoolkit.quarkus.core;

import com.github.calcifux.mailabletoolkit.MailTransport;
import com.github.calcifux.mailabletoolkit.RenderedMail;
import com.github.calcifux.mailabletoolkit.RetryableMailException;
import com.github.calcifux.mailabletoolkit.TerminalMailException;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import java.util.Properties;

/**
 * A named SMTP transport backed by jakarta.mail {@link Transport} directly (the portable twin of the
 * Spring starter's {@code SmtpMailTransport}, which wraps a {@code JavaMailSender}). One instance per
 * configured {@code mailable-toolkit.mailers.<name>}; built once by {@link MailCdiService}.
 *
 * <p>The {@link com.github.calcifux.mailabletoolkit.MailRenderer} already assembles a jakarta
 * {@link MimeMessage}; this transport only needs an SMTP {@link Session} and to push the message. It
 * classifies failures so the queue knows whether to retry: bad credentials / malformed recipients →
 * terminal; everything else (network, SMTP timeout) → retryable.</p>
 */
@RequiredArgsConstructor
public class JakartaMailTransport implements MailTransport {

    private final String name;
    private final Session session;
    private final String fromEmail;
    private final String fromName;

    /**
     * Build a transport from raw SMTP settings. {@code encryption} is {@code starttls} (default) |
     * {@code ssl} | {@code none}. Mirrors {@code SmtpMailTransport.smtp(...)}.
     */
    public static JakartaMailTransport smtp(String name, String host, int port, String username, String password,
                                            String encryption, String fromEmail, String fromName) {
        boolean auth = username != null && !username.isBlank();

        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", host == null ? "localhost" : host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(auth));

        String mode = encryption == null ? "starttls" : encryption.toLowerCase();
        switch (mode) {
            case "starttls" -> props.put("mail.smtp.starttls.enable", "true");
            case "ssl" -> {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.port", String.valueOf(port));
            }
            default -> { /* none: plain SMTP (dev / internal relay) */ }
        }

        Session session;
        if (auth) {
            String user = username;
            String pass = password == null ? "" : password;
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pass);
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        return new JakartaMailTransport(name, session, fromEmail, fromName);
    }

    @Override
    public void send(RenderedMail mail) {
        try {
            MimeMessage source = mail.getMimeMessage();
            // The MimeMessage was assembled against a bare Session; re-bind it to this transport's Session
            // so SMTP host/port/auth are honoured, then send.
            MimeMessage outgoing = new MimeMessage(session, sourceStream(source));
            Transport.send(outgoing);
        } catch (AuthenticationFailedException terminal) {
            throw new TerminalMailException("SMTP auth failed (no retry): " + terminal.getMessage(), terminal);
        } catch (SendFailedException terminal) {
            throw new TerminalMailException("SMTP send rejected (no retry): " + terminal.getMessage(), terminal);
        } catch (MessagingException transientFailure) {
            throw new RetryableMailException("SMTP send failed (will retry): " + transientFailure.getMessage(), transientFailure);
        } catch (RuntimeException unexpected) {
            throw new RetryableMailException("SMTP send failed (will retry): " + unexpected.getMessage(), unexpected);
        }
    }

    private static java.io.InputStream sourceStream(MimeMessage source) throws MessagingException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            source.writeTo(out);
        } catch (java.io.IOException e) {
            throw new MessagingException("Could not serialize MIME message for re-binding", e);
        }
        return new java.io.ByteArrayInputStream(out.toByteArray());
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String fromEmail() {
        return fromEmail;
    }

    @Override
    public String fromName() {
        return fromName;
    }

    /** Convenience for callers that want recipient typing in tests. */
    static Message.RecipientType to() {
        return Message.RecipientType.TO;
    }
}
