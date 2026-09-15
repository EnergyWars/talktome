# TalkToMe – Umsetzungsplan

Entscheidungen aus der Klärung (siehe `project.md` für die volle Idee):

- **Pairing**: Einladungscode/QR – ein Partner generiert, der andere gibt ihn ein (Kamera-Scannen bewusst nicht umgesetzt, siehe „Offene Punkte“).
- **Server**: Eigener Ktor-Server im selben Repo (`server/`), reiner verschlüsselter Transport, kennt keine Klartext-Inhalte.
- **Modus b ("Frust ablassen")**: Bleibt vollständig lokal. Der Nutzer schreibt sich frei, ein Vermittler-Chat (mit Zugriff auf alle bisherigen Notizen/Kontexte zu diesem Partner) reflektiert mit – es wird nie etwas versendet oder verlässt das Gerät in Richtung Server/Partner.
- **Verschlüsselung**: Ende-zu-Ende zwischen den Partner-Geräten; der Server sieht nur Chiffretext.

Wichtiges Architekturprinzip (aus der Idee abgeleitet): **Alle KI-Aufrufe (Gemini) und alle Vermittler-Notizen laufen ausschließlich lokal auf dem jeweiligen Gerät.** Notizen verlassen das Gerät nie in Richtung Server oder Partner – sie werden nur an die Gemini-API übertragen (Ausnahme, da für die Funktion nötig; im Onboarding transparent gemacht).

---

## Phase 0 – Grundgerüst

- [x] Android-App-Grundgerüst angelegt
- [x] `base-project`-Theme übernommen, `verify-theme.sh` + `theme-hashes.sha256` mitgenommen
- [x] `verify-theme.sh` ausgeführt – grün
- [x] `FEATURES.md` und `context.md` im Projektstamm angelegt und befüllt
- [x] `styling-exceptions.md` angelegt und befüllt

## Phase 1 – Datenmodell & lokale Persistenz (Room)

- [x] Entität `Partner` (Pairing-Status, öffentlicher Schlüssel, Geräte-ID, Server-Adresse) – jetzt vollständig an Pairing/Versand angebunden
- [x] Entität `Profile`
- [x] Entität `MediatorNotes`
- [x] Entität `OutgoingMessage` (inkl. Freund-Meinungen, Ablehnungszähler/-grund, Server-Message-ID) – vollständig an Workflow a angebunden
- [x] Entität `InboxMessage` (inkl. Server-Message-ID, Sender-Geräte-ID)
- [x] Entität `NegotiationTurn` (neu, Schema-Version 2) – Verhandlungs-Chat-Verlauf je Nachricht
- [x] Datenbankmigrationen mit Tests: Version 1→2 (`MIGRATION_1_2` + `Migration1To2Test`)
- [x] Sicherer Storage für Gemini-API-Key und Geräte-Token (Android Keystore)
- [x] Room-Datenbank per SQLCipher verschlüsselt

## Phase 2 – Verschlüsselung & Pairing

- [x] Schlüsselpaar pro Gerät (X25519 über Tink-HPKE) im Android Keystore-gestützten Keyset
- [x] Pairing-Flow: Einladungscode/QR enthält Server-Adresse + Public Key des Einladenden
- [x] Schlüsselaustausch beim Pairing (HPKE statt klassischem ECDH-Sitzungsschlüssel, siehe `context.md`)
- [x] Alle Server-Payloads (Nachrichtentext, Pairing-Ack, Ablehnung) client-seitig verschlüsselt
- [x] Unit-Tests für Verschlüsselung/Entschlüsselung, Pairing-Fehlerfälle (ungültiger Code, Netzwerkfehler)

## Phase 3 – Server (Ktor, `server/`-Modul)

- [x] Ktor-Projekt aufgesetzt
- [x] Endpunkt: Geräte-Registrierung (`POST /devices`) – Pairing-Code selbst läuft Peer-zu-Peer über QR/Text, siehe `context.md`
- [x] Endpunkt: Nachricht einstellen (`POST /mailbox/{recipientDeviceId}`)
- [x] Endpunkt: Pull-Mailbox (`GET /mailbox`)
- [x] Endpunkt: Quittieren/Löschen nach Verarbeitung (`DELETE /mailbox/{messageId}`, dient auch der Ablehnungs-Rückmeldung über denselben Mechanismus mit `kind = REJECTION`)
- [x] Kein Persistieren von Klartext, effiziente Queries (Index auf Empfänger-ID)
- [x] Rate-Limiting (In-Memory-Token-Bucket) + Bearer-Token pro Gerät
- [x] Tests: Endpunkte, Rundlauf, Fehlerfälle (unbekannte ID, fehlende Auth, Rate-Limit)

## Phase 4 – Gemini-Integration

- [x] Einstellungs-Screen: API-Key eintragen, Verbindungstest, Fehleranzeige
- [x] Gemini-Client-Wrapper (Timeouts, Retry mit Backoff, Zeichen-Obergrenze, jetzt zusätzlich Mehrfach-Turn-Unterstützung für Chats)
- [x] Klarer Hinweis im Screen zur Datenweitergabe an Gemini

## Phase 5 – KI-Agenten (Prompt-Templates)

- [x] Agent **f**, **g**, **h**, **i**, **j**, Modus-b-Vermittler – alle Vorlagen fertig
- [x] Vermittler-Notiz-Vorlage (`noteTaker`) für private, geräteseitige Beziehungsnotizen
- [x] Strukturiertes Gatekeeper-Antwortformat (`ENTSCHEIDUNG: ZULASSEN/ABLEHNEN`) inkl. Fail-safe-Parser
- [x] Prompt-Vorlagen als Kotlin-Konstanten, Kontext-Injection sauber getrennt von Nutzereingabe
- [x] Sicherheits-Leitplanke in allen Prompts

## Phase 6 – Workflow a ("Ich will dir etwas mitteilen")

- [x] UI: Texteingabe für die Nachricht
- [x] Parallele Anfrage an Agenten f (2x) und g (2x), Ergebnisanzeige
- [x] Zusammenfassung durch Vermittler h anhand aller 4 Ergebnisse + Originaltext
- [x] Verhandlungs-Chat mit Vermittler h, jederzeit "Trotzdem senden" möglich
- [x] Versand: Verschlüsselung, Übertragung an Server
- [x] Empfänger-Seite: Pull vom Server, lokale Entschlüsselung, Prüfung durch Vermittler i anhand Filter c
- [x] Bei Freigabe: Anzeige der Nachricht + Kurzfeedback von i
- [x] Bei Ablehnung: Nachricht dem Empfänger nicht anzeigen, verschlüsselte Ablehnungsbegründung zurück an Sender
- [x] Sender-Seite bei Ablehnung: Vermittler j hilft bei Überarbeitung, Rückführung in den Verhandlungs-/Sende-Schritt
- [x] Eskalationsschutz: Hinweisbanner ab 3 Ablehnungen derselben Nachricht (kein Hard-Block)
- [x] Fehlerfälle: fehlender API-Key, fehlender Partner, Netzwerkfehler jeweils mit eigener Fehlermeldung

## Phase 7 – Workflow b ("Ich muss meinen Frust ablassen")

- [x] UI: freier Schreibbereich, keine Versandoption
- [x] Reflexions-Chat mit dem Modus-b-Vermittler, bezieht bestehende Notizen ein
- [x] Ergebnis bleibt vollständig lokal (kein Server-Kontakt)
- [x] Manueller, expliziter Wechsel zu Modus a (kein automatischer Übergang, keine Inhaltsübernahme)

## Phase 8 – Profil & Konfiguration

- [x] Screen für Filter (c), Ich-Beschreibung (d), Partner-Beschreibung (e)
- [x] Screen für Pairing-Verwaltung (Einladung erzeugen/QR anzeigen, Code eingeben, Partner trennen)
- [x] Screen für Notizen-Verwaltung: "Notizen zurücksetzen" mit Bestätigungsdialog

## Phase 9 – Qualität

- [x] Unit-Tests für alle Features dieses Durchlaufs (Krypto, Pairing, Server, Mailbox-Sync, Workflow a/b, Prompts, Migration)
- [x] Datenbankmigrationen: Version 1→2 vollständig getestet
- [x] `verify-theme.sh` grün, Styling-Regeln eingehalten (keine neuen Custom-Elemente, keine neuen Abweichungen)
- [x] `FEATURES.md` und `context.md` aktualisiert
- [ ] **Noch offen**: Da in dieser Umgebung keine Gradle-Befehle ausgeführt werden dürfen, wurde kein einziger Build/Testlauf tatsächlich ausgeführt. Vor dem ersten Release zwingend `./gradlew build` + volle Testsuite laufen lassen.

## Offene Punkte für später (nicht MVP-blockierend)

- Mehrere Partner/Kontakte pro Nutzer (aktuell: 1:1-Beziehung wie im Konzept beschrieben)
- Kamera-basiertes QR-Scannen beim Pairing (aktuell bewusst nur Anzeige + manuelle Text-Eingabe, siehe `context.md`)
- Push-Benachrichtigungen statt reinem Pull (aktuell laut Idee bewusst Pull-basiert)
- Mehrsprachigkeit der KI-Antworten über Deutsch/Englisch hinaus
- Passwortbasierte Backup-Wiederherstellung der lokalen DB ist im `uikit`-Modul vorhanden, aber nicht an eine UI angebunden
