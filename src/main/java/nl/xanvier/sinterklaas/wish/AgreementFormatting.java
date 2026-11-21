package nl.xanvier.sinterklaas.wish;

import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

final class AgreementFormatting {
    private static final Pattern FORMATTING = Pattern.compile(
            "\\*\\*([^*\\r\\n]+)\\*\\*|\\*([^*\\r\\n]+)\\*|\\+\\+([^+\\r\\n]+)\\+\\+|\\[([^\\]\\r\\n]+)\\]\\(([^\\s()]+)\\)");

    private AgreementFormatting() {}

    static String render(String text) {
        if (text == null) return null;
        var matcher = FORMATTING.matcher(text);
        var html = new StringBuilder();
        int end = 0;
        while (matcher.find()) {
            html.append(escape(text.substring(end, matcher.start())));
            if (matcher.group(1) != null) {
                html.append("<strong>").append(escape(matcher.group(1))).append("</strong>");
            } else if (matcher.group(2) != null) {
                html.append("<em>").append(escape(matcher.group(2))).append("</em>");
            } else if (matcher.group(3) != null) {
                html.append("<u>").append(escape(matcher.group(3))).append("</u>");
            } else if (isWebLink(matcher.group(5))) {
                html.append("<a href=\"").append(escape(matcher.group(5))).append("\">")
                        .append(escape(matcher.group(4))).append("</a>");
            } else {
                html.append(escape(matcher.group()));
            }
            end = matcher.end();
        }
        return html.append(escape(text.substring(end))).toString();
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }

    private static boolean isWebLink(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null && uri.getUserInfo() == null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException invalidUrl) {
            return false;
        }
    }
}
