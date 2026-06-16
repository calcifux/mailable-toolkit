package com.github.calcifux.mailabletoolkit;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * A transport that LOGS the message instead of sending it — the {@code log} driver for local dev (no
 * SMTP needed). Pairs with {@code Mailer.preview(...)} for QA. Registered under its name (default
 * {@code "log"}).
 */
@Slf4j
public class LogMailTransport implements MailTransport {

    private final String name;

    public LogMailTransport() {
        this("log");
    }

    public LogMailTransport(String name) {
        this.name = name;
    }

    @Override
    public void send(RenderedMail mail) {
        try {
            var message = mail.getMimeMessage();
            log.info("[mailable-toolkit:{}] from={} to={} subject=\"{}\"\n{}",
                    name,
                    Arrays.toString(message.getFrom()),
                    Arrays.toString(message.getAllRecipients()),
                    message.getSubject(),
                    mail.getHtml());
        } catch (Exception e) {
            throw new MailToolkitException("log transport could not read the message", e);
        }
    }

    @Override
    public String name() {
        return name;
    }
}
