// Example — assets from EVERY source in one mail. Inline images and attachments are just URIs; the
// AssetResolver chain (classpath / file / http(s) built-in, plus your s3:// and sftp: resolvers) fetches
// each one at SEND time, worker-side when queued — so the URIs ride the queue, never the bytes. Fully
// queue-safe: this mailable holds only strings.
//
// Need a PASSWORD-PROTECTED PDF (or a watermark, a signature, ...)? Do it in YOUR app and hand the final
// bytes to .attachData(new DataAttachment(name, bytes, "application/pdf")). The toolkit moves bytes; it
// does not process documents — that is the application's responsibility, not the toolkit's.
package com.example.mail;

import com.github.calcifux.mailabletoolkit.Envelope;
import com.github.calcifux.mailabletoolkit.InlineAsset;
import com.github.calcifux.mailabletoolkit.Mailable;

public class MultiSourceAssetsMail extends Mailable {

    private final String invoiceId;

    public MultiSourceAssetsMail(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    @Override
    public Envelope build() {
        return Envelope.builder()
                .subject("Statement " + invoiceId)
                .template("statement")
                // --- inline CID images (reference as <img src="cid:..."> in the template), 0..N ---
                .inlineAsset(InlineAsset.ofResource("logo", "classpath:mail/logo.png"))         // bundled
                .inlineAsset(InlineAsset.ofResource("seal", "file:/var/app/brand/seal.png"))    // another folder
                .inlineAsset(InlineAsset.ofResource("promo", "https://cdn.corp.com/promo.png")) // http(s)
                // --- attachments from any source, 0..N ---
                .attach("classpath:mail/terms.pdf")                          // bundled
                .attach("file:/var/app/another-folder/cover.pdf")            // any folder on disk
                .attach("https://files.corp.com/s3/" + invoiceId + ".pdf")   // pre-signed cloud URL (no SDK)
                .attach("s3://invoices/" + invoiceId + ".xml")               // custom s3:// resolver
                .attach("sftp:/facturas/" + invoiceId + ".pdf")              // custom sftp: resolver
                .with("invoiceId", invoiceId)
                .build();
    }
}
