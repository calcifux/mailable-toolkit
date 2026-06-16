// Example — the simplest mailable: subject + template + vars.
// Template: src/main/resources/mail/welcome.peb (which {% extends "mail/layout" %}).
package com.example.mail;

import com.github.calcifux.mailabletoolkit.Envelope;
import com.github.calcifux.mailabletoolkit.Mailable;

public class WelcomeMail extends Mailable {

    private final String name;

    public WelcomeMail(String name) {
        this.name = name;
    }

    @Override
    public Envelope build() {
        return Envelope.builder()
                .subject("Welcome, " + name)
                .template("welcome")     // mailable-toolkit.template.prefix + this + suffix
                .with("name", name)
                .build();
    }
}
