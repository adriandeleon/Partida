package com.partida;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules read off the sources themselves. */
class SourceRulesTest {

    /** A Javadoc comment, then only blank lines or {@code //} comments, then another Javadoc comment. */
    private static final Pattern STACKED =
            Pattern.compile("/\\*\\*(?:(?!\\*/).)*\\*/(?:\\s|//[^\\n]*\\n)*/\\*\\*", Pattern.DOTALL);

    /** DOCS-15: a Javadoc sits on its member; one stacked over another documents nothing. */
    @Test
    void noJavadocIsStackedOnAnother() throws IOException {
        List<String> stacked = new ArrayList<>();
        int files = 0;
        for (String root : List.of("src/main/java", "src/test/java")) {
            try (Stream<Path> paths = Files.walk(Path.of(root))) {
                for (Path file :
                        paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    files++;
                    String text = Files.readString(file);
                    Matcher m = STACKED.matcher(text);
                    while (m.find()) {
                        int line = (int) text.substring(0, m.start())
                                        .chars()
                                        .filter(c -> c == '\n')
                                        .count()
                                + 1;
                        stacked.add(file + ":" + line);
                    }
                }
            }
        }
        assertTrue(files > 100, "the sources were read: " + files);
        assertEquals(List.of(), stacked);
    }
}
