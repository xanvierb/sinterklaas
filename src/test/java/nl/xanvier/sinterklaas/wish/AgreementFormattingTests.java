package nl.xanvier.sinterklaas.wish;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgreementFormattingTests {
    @Test
    void rendersSupportedFormattingAndEscapesLinkAttributes() {
        assertThat(AgreementFormatting.render(
                "**Samen** *afspreken* ++vooraf++\n[Meer info](https://example.org/?a=1&b=2)"))
                .isEqualTo("<strong>Samen</strong> <em>afspreken</em> <u>vooraf</u>\n"
                        + "<a href=\"https://example.org/?a=1&amp;b=2\">Meer info</a>");
    }

    @Test
    void treatsRawHtmlAndUnsafeLinksAsText() {
        assertThat(AgreementFormatting.render(
                "<script>test</script> [Klik](javascript:alert) [Extern](//example.org)"))
                .isEqualTo("&lt;script&gt;test&lt;/script&gt; [Klik](javascript:alert) [Extern](//example.org)");
    }
}
