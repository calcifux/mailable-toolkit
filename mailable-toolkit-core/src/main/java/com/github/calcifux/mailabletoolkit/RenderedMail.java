package com.github.calcifux.mailabletoolkit;

import jakarta.mail.internet.MimeMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The output of the render+MIME step that a {@link MailTransport} sends: a fully-assembled jakarta
 * {@link MimeMessage} (recipients, subject, from, multipart body, inline images and attachments all set)
 * plus the rendered HTML kept aside for preview/logging. The transport just hands the message to its
 * SMTP; it does not build it.
 */
@Getter
@RequiredArgsConstructor
public class RenderedMail {

    private final MimeMessage mimeMessage;
    private final String html;
}
