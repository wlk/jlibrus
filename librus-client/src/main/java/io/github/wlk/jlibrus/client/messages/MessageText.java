package io.github.wlk.jlibrus.client.messages;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Message bodies travel as base64 UTF-8. Received bodies may be wrapped in
 * {@code <Message><Content><![CDATA[...]]></Content></Message>} and contain simple HTML; outgoing bodies are
 * HTML-escaped plain text.
 */
public final class MessageText {

    private static final Pattern CDATA = Pattern.compile("<!\\[CDATA\\[(.*?)(?:]]>|$)", Pattern.DOTALL);
    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>|</p>|</div>|</li>");
    private static final Pattern TAG = Pattern.compile("<[^>]{0,500}>");

    private MessageText() {
    }

    /** Decodes a base64 body to plain text; returns the input unchanged if it is not base64. */
    public static String decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            return "";
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            return base64;
        }
        return toPlainText(unwrap(lenientUtf8(bytes)));
    }

    /** Base64 of UTF-8 text, as Librus expects topics. */
    public static String encodeTopic(String topic) {
        return Base64.getEncoder().encodeToString(topic.getBytes(StandardCharsets.UTF_8));
    }

    /** Plain text is HTML-escaped (the reader renders HTML) and then base64 encoded. */
    public static String encodeBody(String text) {
        String escaped = text.replace("\r\n", "\n")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return Base64.getEncoder().encodeToString(escaped.getBytes(StandardCharsets.UTF_8));
    }

    /** List previews are cut at a byte limit and may end mid-character: drop the broken tail. */
    static String lenientUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            String replaced = new String(bytes, StandardCharsets.UTF_8);
            return replaced.replace("�", "");
        }
    }

    static String unwrap(String text) {
        Matcher matcher = CDATA.matcher(text);
        return matcher.find() ? matcher.group(1) : text;
    }

    static String toPlainText(String html) {
        String withBreaks = BREAK.matcher(html.replace("\r\n", "\n")).replaceAll("\n");
        String noTags = TAG.matcher(withBreaks).replaceAll("");
        return noTags.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .strip();
    }
}
