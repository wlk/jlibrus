package io.github.wlk.jlibrus.client.messages;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTextTest {

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void decodesPlainBase64Text() {
        assertThat(MessageText.decode(b64("Dzień dobry"))).isEqualTo("Dzień dobry");
    }

    @Test
    void unwrapsXmlShellAndFlattensHtml() {
        String wrapped = "<Message><Content><![CDATA[Linia&nbsp;1<br/>Linia 2<p>A &amp; B</p>]]></Content></Message>";

        assertThat(MessageText.decode(b64(wrapped))).isEqualTo("Linia 1\nLinia 2A & B");
    }

    @Test
    void unterminatedCdataIsStillUnwrapped() {
        assertThat(MessageText.decode(b64("<Message><Content><![CDATA[Ucięte"))).isEqualTo("Ucięte");
    }

    @Test
    void dropsCharacterCutInHalfByPreviewLimit() {
        byte[] full = "Zażółć".getBytes(StandardCharsets.UTF_8);
        byte[] cut = Arrays.copyOf(full, full.length - 1);

        assertThat(MessageText.decode(Base64.getEncoder().encodeToString(cut))).isEqualTo("Zażół");
    }

    @Test
    void returnsInputWhenNotBase64AndEmptyForBlank() {
        assertThat(MessageText.decode("not base64!")).isEqualTo("not base64!");
        assertThat(MessageText.decode(" ")).isEmpty();
        assertThat(MessageText.decode(null)).isEmpty();
    }

    @Test
    void encodesTopicAndEscapedBody() {
        assertThat(new String(Base64.getDecoder().decode(MessageText.encodeTopic("Pytanie ą")), StandardCharsets.UTF_8))
                .isEqualTo("Pytanie ą");
        assertThat(new String(Base64.getDecoder().decode(MessageText.encodeBody("a < b & c\r\nd")), StandardCharsets.UTF_8))
                .isEqualTo("a &lt; b &amp; c\nd");
    }

    @Test
    void outgoingMessageValidation() {
        OutgoingMessage ok = new OutgoingMessage(List.of(" 12 ", "12", "13"), "  Temat ", " Treść ", "99");
        assertThat(ok.recipientIds()).containsExactly("12", "13");
        assertThat(ok.topic()).isEqualTo("Temat");
        assertThat(ok.text()).isEqualTo("Treść");

        assertThatThrownBy(() -> OutgoingMessage.newThread(List.of(), "t", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutgoingMessage.newThread(List.of("x1"), "t", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutgoingMessage.newThread(List.of("1"), " ", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutgoingMessage.newThread(List.of("1"), "t", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutgoingMessage.newThread(List.of("1"), "t".repeat(201), "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutgoingMessage(List.of("1"), "t", "x", "abc")).isInstanceOf(IllegalArgumentException.class);
    }
}
