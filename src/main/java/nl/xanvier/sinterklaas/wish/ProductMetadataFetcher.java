package nl.xanvier.sinterklaas.wish;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import nl.xanvier.sinterklaas.wish.TypeSafeCandidateRanker.Candidate;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class ProductMetadataFetcher {
    private static final Logger log = LoggerFactory.getLogger(ProductMetadataFetcher.class);
    record Metadata(String title, String description, String price, String imageUrl,
                    String imageBase64, String imageContentType) {}

    private static final int MAX_REDIRECTS = 3;
    // Retail product pages can exceed 1 MB due to embedded scripts and structured metadata.
    // Keep a firm cap before parsing the page locally.
    private static final int MAX_BODY = 5_000_000;
    private static final int MAX_IMAGE_BYTES = 4_800_000;
    private static final List<String> PRODUCT_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/avif");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final TypeSafeCandidateRanker ranker;

    ProductMetadataFetcher(TypeSafeCandidateRanker ranker) {
        this.ranker = ranker;
    }

    Metadata fetch(String suppliedUrl) {
        URI uri = null;
        try {
            uri = URI.create(suppliedUrl.strip());
            uri = validate(uri);
            byte[] html = retrieve(uri);
            String pageHtml = new String(html, StandardCharsets.UTF_8);
            Document document = Jsoup.parse(pageHtml, uri.toString());
            if (isChallengePage(document)) {
                throw new IOException("The retailer returned a bot-check page");
            }
            JsonNode product = structuredProduct(document);
            String title = first(document, "meta[property=og:title]", "content",
                    "meta[name=twitter:title]", "content", "#productTitle", "text", "#title", "text");
            String description = first(document, "meta[property=og:description]", "content",
                    "meta[name=description]", "content", "meta[name=twitter:description]", "content",
                    "#feature-bullets", "text", "#productDescription", "text");
            String price = first(document, "meta[property=product:price:amount]", "content",
                    "meta[property=og:price:amount]", "content", "meta[itemprop=price]", "content",
                    ".a-price .a-offscreen", "text", "#priceblock_ourprice", "text",
                    "#priceblock_dealprice", "text");
            String image = first(document, "#landingImage", "data-old-hires");
            if (image.isBlank()) image = amazonDynamicImage(document);
            if (image.isBlank()) image = first(document, "#landingImage", "src", "meta[property=og:image]", "content",
                    "meta[name=twitter:image]", "content");
            if (title.isBlank()) title = text(product, "name");
            if (description.isBlank()) description = text(product, "description");
            JsonNode offer = firstNode(product.path("offers"));
            if (price.isBlank() && (text(offer, "priceCurrency").isBlank()
                    || text(offer, "priceCurrency").equalsIgnoreCase("EUR"))) {
                price = text(offer, "price");
            }
            if (image.isBlank()) image = firstImage(product.path("image"));
            if (title.isBlank()) {
                title = first(document, "meta[itemprop=name]", "content", "meta[name=title]", "content");
            }
            if (title.isBlank()) {
                title = document.select("h1").stream().map(Element::text).filter(text -> !text.isBlank())
                        .findFirst().orElse("");
            }
            if (title.isBlank()) title = document.title().strip();
            price = safePrice(price);
            String safeImageUrl = safeImage(uri, image);
            if (ranker.isConfigured()) {
                Map<String, String> missing = new LinkedHashMap<>();
                if (title.isBlank()) missing.put("title", "the product title");
                if (description.isBlank()) missing.put("description", "the product description");
                if (price.isBlank()) missing.put("price", "the current EUR price of one product, excluding shipping");
                if (!missing.isEmpty()) {
                    Map<String, String> selected = ranker.choose("text", limit(title, 120), textCandidates(document, missing), missing);
                    if (title.isBlank()) title = selected.getOrDefault("title", "");
                    if (description.isBlank()) description = selected.getOrDefault("description", "");
                    if (price.isBlank()) price = safePrice(selected.get("price"));
                }
                if (safeImageUrl.isBlank()) {
                    var selected = ranker.choose("image", limit(title, 120), imageCandidates(document, uri),
                            Map.of("image", "the main product photo, excluding logos, icons and recommendations"));
                    safeImageUrl = safeImage(uri, selected.getOrDefault("image", ""));
                }
            }
            if (title.isBlank() && description.isBlank() && price.isBlank() && safeImageUrl.isBlank()) {
                throw new IOException("The page did not contain readable product details");
            }
            ImageData imageData = downloadImage(safeImageUrl);
            return new Metadata(limit(title, 120), limit(description, 2000), price,
                    safeImageUrl, imageData.imageBase64(), imageData.imageContentType());
        } catch (Exception exception) {
            log.warn("Product metadata retrieval failed for {}: {}: {}",
                    safeLogUri(uri), exception.getClass().getSimpleName(), exception.getMessage());
            throw new MetadataUnavailable(exception);
        }
    }

    record ImageData(String imageBase64, String imageContentType) {}

    ImageData downloadImage(String imageUrl) {
        try {
            if (!imageUrl.isBlank()) {
                DownloadedImage downloaded = retrieveImage(validate(URI.create(imageUrl)));
                return new ImageData(Base64.getEncoder().encodeToString(downloaded.content()),
                        downloaded.contentType());
            }
        } catch (IOException | IllegalArgumentException exception) {
            log.info("Product image could not be attached ({})", exception.getClass().getSimpleName());
        }
        return new ImageData("", "");
    }

    DownloadedImage favicon(String productUrl) throws IOException {
        URI origin = URI.create(productUrl).resolve("/favicon.ico");
        DownloadedImage icon = request("shop favicon", validate(origin), "image/x-icon,image/vnd.microsoft.icon,image/png",
                "image/", 64 * 1024);
        if (!List.of("image/x-icon", "image/vnd.microsoft.icon", "image/png").contains(icon.contentType())) {
            throw new IOException("Unsupported favicon type");
        }
        return icon;
    }

    record DownloadedImage(byte[] content, String contentType) {}

    private static final Pattern EUR_PRICE = Pattern.compile(
            "(?:€|\\bEUR)\\s*(\\d+[\\d.,]*)|(\\d+[\\d.,]*)\\s*(?:€|EUR\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final String TEXT_BLOCKS = "h1, h2, h3, p, li, [itemprop=description], "
            + "[class*=description], [id*=description]";
    private static final String PRICE_ELEMENTS = "[itemprop=price], [class*=price], [id*=price]";

    static List<Candidate> textCandidates(Document document, Map<String, String> missing) {
        Element body = document.body().clone();
        body.select("script, style, noscript, template, nav, footer, [hidden], [aria-hidden=true]").remove();
        Element main = body.selectFirst("main, [role=main]");
        if (main == null) main = body;
        Set<Candidate> candidates = new LinkedHashSet<>();
        if (missing.containsKey("price")) {
            for (Element element : main.select(PRICE_ELEMENTS)) {
                if (candidates.size() >= 12) break;
                String value = element.hasAttr("content") ? element.attr("content") : element.text();
                String amount = value.length() <= 32 ? safePrice(value) : "";
                if (!amount.isBlank() && value.matches("(?i)[\\s€\\d.,]+|EUR\\s*[\\d.,]+|[\\d.,]+\\s*EUR")) {
                    candidates.add(new Candidate(limit("Price element " + element.id() + " " + element.className(), 120), amount));
                }
            }
            var prices = EUR_PRICE.matcher(main.text());
            while (candidates.size() < 16 && prices.find()) {
                String amount = safePrice(prices.group(1) != null ? prices.group(1) : prices.group(2));
                if (!amount.isBlank()) candidates.add(new Candidate("EUR amount shown on the page", amount));
            }
        }
        if (missing.containsKey("title") || missing.containsKey("description")) {
            Set<String> seen = new LinkedHashSet<>();
            var blocks = main.select("[itemprop=description], [class*=description], [id*=description]");
            blocks.addAll(main.select(TEXT_BLOCKS));
            for (Element element : blocks) {
                if (candidates.size() >= TypeSafeCandidateRanker.MAX_CANDIDATES) break;
                String value = limit(element.text(), 650);
                if (!value.isBlank() && seen.add(value)) {
                    candidates.add(new Candidate(limit(element.tagName() + " " + element.className(), 120), value));
                }
            }
        }
        return List.copyOf(candidates);
    }

    static List<Candidate> imageCandidates(Document document, URI page) {
        Element main = document.selectFirst("main, [role=main]");
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        var images = document.select("img[itemprop=image], #landingImage, img[class*=product]");
        if (main != null) images.addAll(main.select("img"));
        images.addAll(document.select("img"));
        for (Element image : images) {
            for (String attribute : List.of("data-old-hires", "data-src", "data-original", "src", "srcset", "data-srcset")) {
                if (candidates.size() >= TypeSafeCandidateRanker.MAX_CANDIDATES) return List.copyOf(candidates.values());
                String value = image.attr(attribute).strip();
                if (value.isBlank() || value.length() > 2048) continue;
                if (attribute.endsWith("srcset")) {
                    String[] entries = value.split(",", -1);
                    value = entries[entries.length - 1].strip().split("\\s+", 2)[0];
                }
                if (value.isBlank()) continue;
                try {
                    URI url = page.resolve(value);
                    if (!validateImageUri(url)) continue;
                    String context = limit(image.attr("alt") + " " + image.id() + " " + image.className(), 160).strip();
                    candidates.putIfAbsent(url.toString(), new Candidate(context, url.toString()));
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed candidate URLs.
                }
            }
        }
        return List.copyOf(candidates.values());
    }

    private static boolean isChallengePage(Document document) {
        String text = (document.title() + " " + document.body().text()).toLowerCase(Locale.ROOT);
        return text.contains("robot check") || text.contains("captcha")
                || text.contains("sorry, we just need to make sure you're not a robot")
                || text.contains("enter the characters you see below");
    }

    private static String amazonDynamicImage(Document document) {
        Element image = document.selectFirst("#landingImage[data-a-dynamic-image]");
        if (image == null) return "";
        try {
            JsonNode images = JSON.readTree(image.attr("data-a-dynamic-image"));
            String bestUrl = "";
            long bestArea = 0;
            for (var field : images.properties()) {
                JsonNode dimensions = field.getValue();
                long area = dimensions.isArray() && dimensions.size() >= 2
                        ? dimensions.get(0).asLong() * dimensions.get(1).asLong() : 0;
                if (area > bestArea) {
                    bestArea = area;
                    bestUrl = field.getKey();
                }
            }
            return bestUrl;
        } catch (Exception ignored) {
            return "";
        }
    }

    private byte[] retrieve(URI initial) throws IOException {
        return request("product page", initial, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "text/html", MAX_BODY).content();
    }

    private DownloadedImage retrieveImage(URI initial) throws IOException {
        DownloadedImage image = request("product image", initial,
                "image/jpeg,image/png,image/webp;q=0.9,image/avif;q=0.9",
                "image/", MAX_IMAGE_BYTES);
        if (!PRODUCT_IMAGE_TYPES.contains(image.contentType())) {
            throw new IOException("Unsupported product image format: " + image.contentType());
        }
        return image;
    }

    private DownloadedImage request(String purpose, URI initial, String accept, String expectedContentType, int maxBytes)
            throws IOException {
        URI current = initial;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            InetAddress[] pinned = resolvePublic(current.getHost());
            String pinnedHost = current.getHost();
            DnsResolver resolver = new DnsResolver() {
                @Override
                public InetAddress[] resolve(String host) throws UnknownHostException {
                    if (!host.equalsIgnoreCase(pinnedHost)) {
                        throw new UnknownHostException("Host changed");
                    }
                    return Arrays.copyOf(pinned, pinned.length);
                }

                @Override
                public String resolveCanonicalHostname(String host) throws UnknownHostException {
                    if (!host.equalsIgnoreCase(pinnedHost)) {
                        throw new UnknownHostException("Host changed");
                    }
                    return host;
                }
            };
            var manager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(resolver)
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.ofSeconds(3))
                            .setSocketTimeout(Timeout.ofSeconds(5)).build())
                    .build();
            RequestConfig config = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofSeconds(3))
                    .setResponseTimeout(Timeout.ofSeconds(5)).build();
            try (CloseableHttpClient client = HttpClients.custom().setConnectionManager(manager)
                    .setDefaultRequestConfig(config).disableRedirectHandling().build()) {
                log.info("Shop fetch request purpose={} origin={} GET {}",
                        purpose, safeLogUri(initial), safeLogUri(current));
                var request = new org.apache.hc.client5.http.classic.methods.HttpGet(current);
                request.setHeader("Accept", accept);
                request.setHeader("Accept-Language", "nl-NL,nl;q=0.9,en-US;q=0.8,en;q=0.7");
                request.setHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
                try (var response = client.execute(request)) {
                    int status = response.getCode();
                    log.info("Shop fetch response purpose={} origin={} GET {} status={} content-type={}",
                            purpose, safeLogUri(initial), safeLogUri(current), status,
                            response.getEntity() == null ? "" : response.getEntity().getContentType());
                    if (status >= 300 && status < 400) {
                        if (redirects == MAX_REDIRECTS || response.getFirstHeader(HttpHeaders.LOCATION) == null) {
                            throw new IOException("Too many redirects");
                        }
                        current = validate(current.resolve(response.getFirstHeader(HttpHeaders.LOCATION).getValue()));
                        continue;
                    }
                    if (status != HttpStatus.SC_OK || response.getEntity() == null
                            || response.getEntity().getContentType() == null
                            || !response.getEntity().getContentType().toLowerCase(Locale.ROOT)
                                    .startsWith(expectedContentType)) {
                        throw new IOException("Unsupported product page");
                    }
                    String contentType = response.getEntity().getContentType().split(";", 2)[0]
                            .strip().toLowerCase(Locale.ROOT);
                    try (var input = response.getEntity().getContent();
                         var output = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int total = 0;
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            total += read;
                            if (total > maxBytes) {
                                throw new IOException("Downloaded content too large");
                            }
                            output.write(buffer, 0, read);
                        }
                        return new DownloadedImage(output.toByteArray(), contentType);
                    }
                }
            }
        }
        throw new IOException("Too many redirects");
    }

    private static URI validate(URI uri) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != (scheme.equals("https") ? 443 : 80))) {
            throw new IOException("Only public HTTP(S) product links are accepted");
        }
        resolvePublic(uri.getHost());
        return uri;
    }

    private static String safeLogUri(URI uri) {
        if (uri == null) return "<invalid-url>";
        return (uri.getScheme() == null ? "" : uri.getScheme() + "://")
                + (uri.getHost() == null ? "<invalid-host>" : uri.getHost())
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + (uri.getPath() == null ? "" : uri.getPath());
    }

    private static InetAddress[] resolvePublic(String host) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !isPublic(address))) {
            throw new IOException("Host resolves to a non-public address");
        }
        return addresses;
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && (b == 168 || (b == 0 && c == 0) || (b == 0 && c == 2)))
                    && !(a == 198 && (b == 18 || b == 19 || b == 51))
                    && !(a == 203 && b == 0 && c == 113);
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 255;
            return (first & 0xe0) == 0x20 && !(bytes[0] == 0x20 && bytes[1] == 0x01
                    && bytes[2] == 0x0d && bytes[3] == (byte) 0xb8);
        }
        return false;
    }

    private static JsonNode structuredProduct(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode product = findProduct(JSON.readTree(script.data()));
                if (product != null) return product;
            } catch (Exception ignored) {
                // A malformed structured-data block should not prevent reading page metadata.
            }
        }
        return JSON.nullNode();
    }

    private static JsonNode findProduct(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode product = findProduct(child);
                if (product != null) return product;
            }
            return null;
        }
        JsonNode type = node.path("@type");
        boolean isProduct = type.isTextual() && type.asText().equalsIgnoreCase("Product");
        if (type.isArray()) {
            for (JsonNode item : type) {
                isProduct |= item.asText().equalsIgnoreCase("Product");
            }
        }
        if (isProduct) return node;
        if (node.has("@graph")) return findProduct(node.path("@graph"));
        return null;
    }

    private static JsonNode firstNode(JsonNode node) {
        return node.isArray() ? (node.isEmpty() ? JSON.nullNode() : node.get(0)) : node;
    }

    private static String firstImage(JsonNode node) {
        JsonNode image = firstNode(node);
        if (image.isTextual()) return image.asText();
        return text(image, "url");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isTextual() || value.isNumber()) return value.asText();
        return "";
    }

    private static String first(Document document, String... selectorAndAttribute) {
        for (int index = 0; index < selectorAndAttribute.length; index += 2) {
            Element element = document.selectFirst(selectorAndAttribute[index]);
            if (element != null) {
                String value = selectorAndAttribute[index + 1].equals("text")
                        ? element.text() : element.attr(selectorAndAttribute[index + 1]);
                if (!value.isBlank()) return value.strip();
            }
        }
        return "";
    }

    private static String safePrice(String value) {
        if (value == null) return "";
        String amount = value.replace('\u00a0', ' ').replaceAll("[^0-9,.]", "");
        if (amount.matches("\\d{1,3}(?:\\.\\d{3})+,\\d{1,2}")) {
            amount = amount.replace(".", "").replace(',', '.');
        } else if (amount.matches("\\d{1,3}(?:,\\d{3})+\\.\\d{1,2}")) {
            amount = amount.replace(",", "");
        } else if (amount.matches("\\d{1,8},\\d{1,2}")) {
            amount = amount.replace(',', '.');
        }
        return amount.matches("\\d{1,8}(?:\\.\\d{1,2})?") ? amount : "";
    }

    private static String safeImage(URI page, String value) {
        if (value.isBlank()) return "";
        try {
            URI image = page.resolve(value);
            if (validateImageUri(image) && resolvePublic(image.getHost()).length > 0) return image.toString();
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private static boolean validateImageUri(URI uri) {
        return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null && uri.getPort() == -1;
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    static final class MetadataUnavailable extends RuntimeException {
        MetadataUnavailable(Throwable cause) {
            super("Productgegevens konden niet worden opgehaald. Vul de velden zelf in.", cause);
        }
    }
}
