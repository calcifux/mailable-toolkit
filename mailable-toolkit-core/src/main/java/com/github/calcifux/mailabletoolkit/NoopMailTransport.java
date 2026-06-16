package com.github.calcifux.mailabletoolkit;

/**
 * A transport that DROPS the message (sends nothing). The {@code null}/array driver for tests where you
 * don't even want to inspect the output. Registered under its name (default {@code "noop"}).
 */
public class NoopMailTransport implements MailTransport {

    private final String name;

    public NoopMailTransport() {
        this("noop");
    }

    public NoopMailTransport(String name) {
        this.name = name;
    }

    @Override
    public void send(RenderedMail mail) {
        // intentionally a no-op
    }

    @Override
    public String name() {
        return name;
    }
}
