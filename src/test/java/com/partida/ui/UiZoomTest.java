package com.partida.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.partida.config.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WIN-12: the UI zoom's steps, its range, and the stylesheet it scales. */
class UiZoomTest {

    @Test
    void stepsStayInTheRange() {
        assertEquals(110, UiZoom.in(100));
        assertEquals(90, UiZoom.out(100));
        assertEquals(Settings.MAX_UI_ZOOM, UiZoom.in(Settings.MAX_UI_ZOOM));
        assertEquals(Settings.MIN_UI_ZOOM, UiZoom.out(Settings.MIN_UI_ZOOM));
        assertEquals(Settings.MAX_UI_ZOOM, Settings.clampUiZoom(900));
        assertEquals(Settings.MIN_UI_ZOOM, Settings.clampUiZoom(-5));
    }

    @Test
    void everyPixelLengthIsScaledAndNothingElse() {
        String css = ".root { -fx-font-size: 14px; } .a { -fx-padding: 4px 8.5px; -fx-border-width: 1; "
                + "-fx-font-size: 1.2em; -fx-background-color: #ababab; } .b-2px { -fx-min-width: 10px; }";
        String scaled = UiZoom.scale(css, 150);
        assertTrue(scaled.contains("-fx-font-size: 21px;"), scaled);
        assertTrue(scaled.contains("-fx-padding: 6px 12.75px;"), scaled);
        assertTrue(scaled.contains("-fx-border-width: 1;"), "a length with no unit is left alone");
        assertTrue(scaled.contains("1.2em"), "an em follows the root size already");
        assertTrue(scaled.contains("#ababab"));
        assertTrue(scaled.contains(".b-2px"), "a selector is never scaled");
        assertTrue(scaled.contains("-fx-min-width: 15px;"), scaled);
        assertEquals(css, UiZoom.scale(css, Settings.DEFAULT_UI_ZOOM));
    }

    @Test
    void theSheetTravelsAsADataUrl() {
        String url = UiZoom.dataUrl(".a { -fx-font-size: 9px; }");
        assertTrue(url.startsWith("data:text/css;base64,"));
        assertEquals(
                ".a { -fx-font-size: 9px; }",
                new String(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)), StandardCharsets.UTF_8));
    }

    @Test
    void anOutOfRangeZoomInTheFileIsClampedWhenRead() {
        Settings s = new Settings();
        s.uiZoom = 1000;
        assertEquals(Settings.MAX_UI_ZOOM, s.clamped().uiZoom);
        s.uiZoom = 0;
        assertEquals(Settings.MIN_UI_ZOOM, s.clamped().uiZoom);
    }

    /**
     * Every size the zoomed sheets fix is written in px, so the zoom multiplies it (WIN-12): a bare number in a
     * padding, gap, radius, inset or width is a pixel length JavaFX takes as is, and the zoom would leave it alone.
     * Only a border's or a stroke's width stays a bare number: a hairline stays one pixel at every zoom.
     */
    @Test
    void theZoomedSheetsWriteEveryLengthInPixels() throws IOException {
        Pattern declaration = Pattern.compile(
                "-fx-(?:padding|spacing|hgap|vgap|background-insets|background-radius|border-radius|border-insets"
                        + "|(?:min|max|pref)-(?:width|height)|graphic-text-gap|arrow-button-gap)\\s*:\\s*([^;{}]+);");
        Pattern bare = Pattern.compile("(?<![\\w.#%-])-?\\d*\\.?\\d+(?![\\w.%])");
        List<String> unscaled = new ArrayList<>();
        for (String sheet : List.of("app.css", "editora-dark.css", "editora-light.css")) {
            String css;
            try (InputStream in = Themes.class.getResourceAsStream("/com/partida/styles/" + sheet)) {
                assertTrue(in != null, sheet);
                css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher m = declaration.matcher(css);
            while (m.find()) {
                Matcher n = bare.matcher(m.group(1));
                while (n.find()) {
                    if (Double.parseDouble(n.group()) != 0) unscaled.add(sheet + ": " + m.group());
                }
            }
        }
        assertEquals(List.of(), unscaled);
    }
}
