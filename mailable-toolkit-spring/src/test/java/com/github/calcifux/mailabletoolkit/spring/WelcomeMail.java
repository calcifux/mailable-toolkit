package com.github.calcifux.mailabletoolkit.spring;

import com.github.calcifux.mailabletoolkit.Envelope;
import com.github.calcifux.mailabletoolkit.Mailable;

/** Test fixture: the jr's view of a mailable — a subject, a template, and its vars. */
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
}
