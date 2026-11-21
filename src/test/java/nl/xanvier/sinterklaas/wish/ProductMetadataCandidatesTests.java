package nl.xanvier.sinterklaas.wish;

import java.net.URI;
import java.util.Map;
import nl.xanvier.sinterklaas.wish.TypeSafeCandidateRanker.Candidate;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetadataCandidatesTests {
    @Test
    void textCandidatesContainProductTextAndPricesWithoutMarkup() {
        var document = Jsoup.parse("""
                <nav><p>Navigation</p></nav>
                <main>
                  <script>const hiddenPrice = '€ 3,50';</script>
                  <p>A <strong>wooden</strong> train</p>
                  <p>Now € 19,95</p>
                </main>
                """);

        assertThat(ProductMetadataFetcher.textCandidates(document, Map.of("description", "", "price", "")))
                .extracting(Candidate::value)
                .contains("A wooden train", "19.95")
                .noneMatch(value -> value.contains("<") || value.contains("Navigation") || value.contains("3,50"));
    }

    @Test
    void imageCandidatesResolveLazyImagesAndSkipInlineImages() {
        var document = Jsoup.parse("""
                <main>
                  <img alt="Wooden train" data-src="/train.jpg" src="data:image/gif;base64,abc">
                  <img src="/train.jpg">
                  <img srcset=",,,">
                </main>
                """);

        assertThat(ProductMetadataFetcher.imageCandidates(document, URI.create("https://shop.example/product")))
                .containsExactly(new Candidate("Wooden train", "https://shop.example/train.jpg"));
    }
}
