// Example — every way to dispatch, via the static Mail facade (wired by the Spring starter).
package com.example.mail;

import com.github.calcifux.mailabletoolkit.spring.Mail;

import java.util.List;

public class SendingExamples {

    public void demo(byte[] csvBytes) {
        String to = "calcifux@example.com";

        // ── synchronous ──────────────────────────────────────────────────────────────
        Mail.send(new WelcomeMail("Calcifux"), List.of(to));

        // with cc / bcc
        Mail.send(new WelcomeMail("Calcifux"), List.of(to), List.of("cc@example.com"), List.of());

        // ── asynchronous (queued) ────────────────────────────────────────────────────
        // rides the mailable's queue() (or the default if it declares none)
        Mail.queue(new WelcomeMail("Calcifux"), List.of(to));

        // ── pick the SMTP server per send ────────────────────────────────────────────
        Mail.mailer("billing").send(new InvoiceMail("INV-1024", "/var/app/inv/1024.pdf", csvBytes), List.of(to));

        // ── pick the queue per send ──────────────────────────────────────────────────
        Mail.onQueue("priority").queue(new WelcomeMail("Calcifux"), List.of(to));

        // ── both ─────────────────────────────────────────────────────────────────────
        Mail.mailer("billing").onQueue("priority")
                .queue(new InvoiceMail("INV-1025", "/var/app/inv/1025.pdf", csvBytes), List.of(to));

        // ── preview (render only, no SMTP) — for a /preview endpoint or QA ────────────
        String html = Mail.preview(new WelcomeMail("Calcifux"));
        System.out.println(html);
    }
}
