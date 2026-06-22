package com.github.calcifux.mailabletoolkit.quarkus.core;

import com.github.calcifux.mailabletoolkit.Envelope;
import com.github.calcifux.mailabletoolkit.Mailable;

/** Minimal serializable test mailable: a named template + one model var, on a named queue. */
public class WelcomeMail extends Mailable {

    private final String name;

    public WelcomeMail(String name) {
        this.name = name;
    }

    @Override
    public Envelope build() {
        return Envelope.builder()
                .subject("Welcome, " + name)
                .template("mail/welcome")
                .with("name", name)
                .build();
    }

    @Override
    public String queue() {
        return "priority";
    }
}
