/** Partida: one JPMS module. Opens only what Jackson's reflection needs. */
module com.partida {
    // Null annotations, read by NullAway at compile time only.
    requires static org.jspecify;
    requires javafx.controls;
    requires javafx.graphics;
    requires atlantafx.base;
    requires com.fasterxml.jackson.databind;
    // The Debug Log captures java.util.logging; the update check is one HTTPS request.
    requires java.logging;
    requires java.net.http;

    // Jackson binds settings.json onto the config POJOs, and Settings Sync's files onto SyncData.
    opens com.partida.config to
            com.fasterxml.jackson.databind;
    opens com.partida.sync to
            com.fasterxml.jackson.databind;

    exports com.partida;
}
