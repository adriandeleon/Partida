package com.partida.ui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.partida.config.Settings;

/**
 * The UI zoom (WIN-12): one setting, {@link Settings#uiZoom}, scales the whole chrome. The stylesheets are
 * written at 100% in pixels; at any other zoom {@link Themes} gives every window a copy with each pixel size
 * multiplied ({@link #scale}), the root font size among them, so every font follows one root size and the
 * menus' 14 and 11 px follow too (MENU-16). The few sizes the code fixes (a glyph's box, a dialog's width)
 * are bound to {@link #factor} instead of written as plain pixels.
 */
public final class UiZoom {

    /** One step of Zoom In or Zoom Out, in percent. */
    public static final int STEP = 10;

    private static final Pattern PX = Pattern.compile("(?<![\\w.-])(-?\\d*\\.?\\d+)px\\b");

    private static final ReadOnlyDoubleWrapper FACTOR = new ReadOnlyDoubleWrapper(1);

    private UiZoom() {}

    /** The zoom as a factor, 1 at 100%. FX thread. */
    public static ReadOnlyDoubleProperty factor() {
        return FACTOR.getReadOnlyProperty();
    }

    static void setFactor(int percent) {
        FACTOR.set(Settings.clampUiZoom(percent) / 100.0);
    }

    /** One step larger, up to the largest. */
    public static int in(int percent) {
        return Settings.clampUiZoom(percent + STEP);
    }

    /** One step smaller, down to the smallest. */
    public static int out(int percent) {
        return Settings.clampUiZoom(percent - STEP);
    }

    /** {@code px} at the current zoom, following it. */
    static DoubleBinding times(double px) {
        return FACTOR.multiply(px);
    }

    /** Binds {@code property} to {@code px} at the current zoom: a fixed size of the code, zoomed. */
    static void bind(DoubleProperty property, double px) {
        property.bind(times(px));
    }

    /** A row whose gap is {@code px} at the current zoom (WIN-12): a gap the code fixes, zoomed. */
    static HBox hbox(double px, Node... children) {
        HBox box = new HBox(children);
        bind(box.spacingProperty(), px);
        return box;
    }

    /** A column whose gap is {@code px} at the current zoom (WIN-12). */
    static VBox vbox(double px, Node... children) {
        VBox box = new VBox(children);
        bind(box.spacingProperty(), px);
        return box;
    }

    /** The region's padding, in pixels at 100%, following the zoom (WIN-12). */
    static void pad(Region region, double top, double right, double bottom, double left) {
        region.paddingProperty()
                .bind(Bindings.createObjectBinding(
                        () -> {
                            double f = FACTOR.get();
                            return new Insets(top * f, right * f, bottom * f, left * f);
                        },
                        FACTOR));
    }

    /** Insets in pixels at 100%, at the zoom now: for a placement made once, as an overlay's when it opens. */
    static Insets insets(double top, double right, double bottom, double left) {
        double f = FACTOR.get();
        return new Insets(top * f, right * f, bottom * f, left * f);
    }

    /** The same padding on every side, following the zoom (WIN-12). */
    static void pad(Region region, double all) {
        pad(region, all, all, all, all);
    }

    /**
     * A stylesheet at {@code percent}: every pixel length multiplied, rounded to a hundredth. A length written
     * as a bare number in a padding, gap, radius, inset or size is a pixel length too, as JavaFX reads it, so
     * the control themes stay Editora's files unchanged (ICON-05); a border's or a stroke's width stays a bare
     * number and a hairline. An em or a percentage is relative already and is left alone. Pure.
     */
    static String scale(String css, int percent) {
        if (percent == Settings.DEFAULT_UI_ZOOM) return css;
        double factor = percent / 100.0;
        Matcher m = PX.matcher(pixels(css));
        StringBuilder out = new StringBuilder(css.length() + 256);
        while (m.find()) {
            double scaled = Math.round(Double.parseDouble(m.group(1)) * factor * 100) / 100.0;
            String number = scaled == Math.rint(scaled)
                    ? Long.toString((long) scaled)
                    : String.format(Locale.ROOT, "%s", scaled);
            m.appendReplacement(out, Matcher.quoteReplacement(number + "px"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The declarations whose bare numbers are pixel lengths the zoom scales. */
    static final Pattern LENGTHS = Pattern.compile(
            "(-fx-(?:padding|spacing|hgap|vgap|background-insets|background-radius|border-radius|border-insets"
                    + "|(?:min|max|pref)-(?:width|height)|graphic-text-gap|arrow-button-gap)\\s*:\\s*)([^;{}]+)(;)");

    /** A bare number in a declaration: no unit after it, and not part of a name, a color or another number. */
    private static final Pattern BARE = Pattern.compile("(?<![\\w.#%-])(-?\\d*\\.?\\d+)(?![\\w.%])");

    /** {@code css} with every non-zero bare number of a {@link #LENGTHS} declaration written in px. Pure. */
    static String pixels(String css) {
        Matcher m = LENGTHS.matcher(css);
        StringBuilder out = new StringBuilder(css.length() + 256);
        while (m.find()) {
            Matcher n = BARE.matcher(m.group(2));
            StringBuilder value = new StringBuilder();
            while (n.find()) {
                String number = n.group(1);
                n.appendReplacement(
                        value, Matcher.quoteReplacement(Double.parseDouble(number) == 0 ? number : number + "px"));
            }
            n.appendTail(value);
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + value + m.group(3)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** A stylesheet as a {@code data:} URL, which a scene and the user-agent sheet both take. */
    static String dataUrl(String css) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }
}
