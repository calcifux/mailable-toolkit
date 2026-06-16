package com.github.calcifux.mailabletoolkit.spring;

import com.github.calcifux.mailabletoolkit.Mailer;
import com.github.calcifux.mailabletoolkit.TemplateRenderer;
import com.github.calcifux.mailabletoolkit.queue.InMemoryMailQueue;
import com.github.calcifux.mailabletoolkit.queue.MailQueue;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wires the autoconfig against an embedded GreenMail SMTP: proves Pebble is auto-picked, the in-memory
 * queue is the default, and a Mailable renders + lands at the named mailer's SMTP with the global from.
 */
class MailableToolkitAutoConfigurationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailableToolkitAutoConfiguration.class))
            .withPropertyValues(
                    "mailable-toolkit.from.email=noreply@calcifux.dev",
                    "mailable-toolkit.from.name=Calcifux",
                    "mailable-toolkit.default-mailer=primary",
                    "mailable-toolkit.mailers.primary.host=127.0.0.1",
                    "mailable-toolkit.mailers.primary.port=" + ServerSetupTest.SMTP.getPort(),
                    "mailable-toolkit.mailers.primary.encryption=none");

    @Test
    void picks_pebble_and_provides_inmemory_queue_by_default() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Mailer.class);
            assertThat(context.getBean(TemplateRenderer.class).engineId()).isEqualTo("pebble");
            assertThat(context).hasSingleBean(MailQueue.class);
            assertThat(context.getBean(MailQueue.class)).isInstanceOf(InMemoryMailQueue.class);
        });
    }

    @Test
    void renders_and_sends_through_the_named_smtp_mailer() {
        runner.run(context -> {
            context.getBean(Mailer.class).send(new WelcomeMail("Calcifux"), List.of("dest@calcifux.dev"));

            assertThat(greenMail.waitForIncomingEmail(3_000, 1)).isTrue();
            MimeMessage[] received = greenMail.getReceivedMessages();
            assertThat(received).hasSize(1);
            assertThat(received[0].getSubject()).isEqualTo("Welcome, Calcifux");
            assertThat(received[0].getFrom()[0].toString()).contains("noreply@calcifux.dev");
        });
    }
}
