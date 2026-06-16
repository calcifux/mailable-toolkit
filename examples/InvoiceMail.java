// Example — a richer mailable: inline CID logo, an attachment from a file path AND one from bytes,
// a reply-to, a forced locale, its own default queue, and going out from the "billing" SMTP.
package com.example.mail;

import com.github.calcifux.mailabletoolkit.DataAttachment;
import com.github.calcifux.mailabletoolkit.Envelope;
import com.github.calcifux.mailabletoolkit.InlineAsset;
import com.github.calcifux.mailabletoolkit.Mailable;

import java.util.Locale;

public class InvoiceMail extends Mailable {

    private final String invoiceId;
    private final String pdfPath;     // a file already on disk
    private final byte[] csvBytes;    // a summary built in memory

    public InvoiceMail(String invoiceId, String pdfPath, byte[] csvBytes) {
        this.invoiceId = invoiceId;
        this.pdfPath = pdfPath;
        this.csvBytes = csvBytes;
    }

    /** This mailable rides the "billing" queue unless a per-send onQueue(...) overrides it. */
    @Override
    public String queue() {
        return "billing";
    }

    @Override
    public Envelope build() {
        return Envelope.builder()
                .subject("Invoice " + invoiceId)
                .template("invoice")
                .replyTo("ar@corp.com")
                .locale(Locale.forLanguageTag("es-MX"))
                .mailer("billing")   // Envelope-level SMTP choice (a Mail.mailer(...) call still wins)
                // inline image: reference as <img src="cid:logo"> in the template
                .inlineAsset(InlineAsset.ofResource("logo", "classpath:mail/logo.png"))
                // 0..N attachments — by path...
                .attach(pdfPath)
                // ...and by bytes
                .attachData(new DataAttachment("summary-" + invoiceId + ".csv", csvBytes, "text/csv"))
                .with("invoiceId", invoiceId)
                .build();
    }
}
