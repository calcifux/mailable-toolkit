// Example — the "users edit the template in an admin, it lives in the DB" case (e.g. billing mails that
// change over time). The app loads the edited HTML string from its table and hands it to the mailable;
// the toolkit renders it inline (Thymeleaf / Pebble / FreeMarker all support inline source). Queue-safe:
// the mailable carries only the (small) HTML string + variables, both serializable.
package com.example.mail;

import com.github.calcifux.mailabletoolkit.Envelope;
import com.github.calcifux.mailabletoolkit.Mailable;

import java.util.Map;

public class DbTemplateMail extends Mailable {

    private final String subject;
    private final String templateHtml;            // the string your users edited, loaded from the DB
    private final Map<String, Object> variables;

    public DbTemplateMail(String subject, String templateHtml, Map<String, Object> variables) {
        this.subject = subject;
        this.templateHtml = templateHtml;
        this.variables = variables;
    }

    @Override
    public Envelope build() {
        return Envelope.builder()
                .subject(subject)
                .inlineTemplate(templateHtml)     // rendered as-is — no classpath lookup, no inheritance
                .model(variables)
                .build();
    }

    // Wiring (in your service):
    //   String html = emailTemplateRepository.activeTemplateFor(accountType);   // from Oracle / Postgres / ...
    //   Mail.send(new DbTemplateMail("Your statement", html, vars), List.of(customerEmail));
    //
    // SECURITY: this HTML is user-edited, so it is a template-injection surface. Fine for trusted admins;
    // if less-trusted users can edit it, sandbox the engine. Also document which ${variables} they may use.
}
