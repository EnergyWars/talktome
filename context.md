# context.md – TalkToMe

Aktueller Ist-Zustand des Projekts. Enthält keine Historie – bei Widerspruch zu `todos.md` (falls dort präziser) diese bevorzugen, bei Widerspruch zu globalen Regeln haben die globalen Regeln Vorrang.

## Projektaufbau

- Git-Projekt, drei Gradle-Module: `app` (Business-Logik, DI, DB, Screens, Android), `uikit` (Theme + generische Compose-Bausteine, 1:1 aus `/home/sklein/IdeaProjects/base-project` portiert bzw. bei Bedarf erweitert, siehe `styling-exceptions.md`), `server` (reines Kotlin/JVM-Modul, Ktor-Server, kein Android).
- `verify-theme.sh` + `theme-hashes.sha256` vorhanden.
- Gradle-Wrapper 9.0-milestone-1 (aus base-project übernommen).

## Implementierungsstatus je Feature (siehe `FEATURES.md` für die Nutzersicht)

| Feature | Status | Kern-Dateien |
|---|---|---|
| Profil-Konfiguration | Fertig | `data/db/ProfileEntity.kt`, `data/profile/ProfileRepository.kt`, `ui/profile/*` |
| Gemini-API-Schlüssel-Verwaltung | Fertig | `data/security/SecureApiKeyStore.kt`, `data/gemini/*`, `ui/gemini/*` |
| Prompt-Vorlagen f–j, Modus-b, Notiz-Vorlage, Gatekeeper-Format | Fertig | `data/prompts/*` |
| Mehrfach-Turn-Chat mit Gemini | Fertig | `data/gemini/GeminiClient.kt` (`history: List<GeminiTurn>`) |
| Anzeige-Einstellungen | Fertig | `data/settings/*`, `ui/settings/DisplaySettingsScreen.kt` |
| E2E-Verschlüsselung (Identität, Ver-/Entschlüsselung) | Fertig | `data/crypto/E2eIdentity.kt`, `di/CryptoModule.kt` |
| Geräte-Identität (Server-Registrierung, Token) | Fertig | `data/crypto/DeviceIdentityStore.kt` |
| Pairing (Einladung erzeugen/annehmen, Trennen) | Fertig | `data/pairing/*`, `ui/pairing/*` |
| Ktor-Mailbox-Server (`server`-Modul) | Fertig | `server/src/main/kotlin/.../*.kt` |
| Client-Mailbox-API + Envelopes | Fertig | `data/network/MailboxApi.kt`, `MailboxEnvelopes.kt` |
| Mailbox-Sync (Pull, Entschlüsseln, Routing nach Art) | Fertig | `data/sync/MailboxSyncRepository.kt` |
| Workflow a (Entwurf → 4 Meinungen → Vermittler-Zusammenfassung → Verhandlung → Senden) | Fertig | `data/negotiation/OutgoingMessageRepository.kt`, `ui/compose/*` |
| Empfang + Filterprüfung + spurlose Ablehnung + Rückkanal | Fertig | `data/inbox/InboxRepository.kt`, `ui/inbox/*` |
| Eskalations-Hinweis bei wiederholten Ablehnungen | Fertig | `OutgoingMessageRepository.isEscalating` (Schwelle 3) |
| Workflow b (Frust ablassen, rein lokal) | Fertig | `ui/venting/*` |
| Vermittler-Notizen zurücksetzen | Fertig | `ui/notes/*` |
| Verschlüsselung der lokalen Datenbank (SQLCipher) | Fertig | `data/db/EncryptedAppDatabaseFactory.kt`, `uikit/.../database/**` |
| Mehrere Partner/Kontakte pro Nutzer | Nicht umgesetzt (siehe `todos.md`) | – |
| Kamera-QR-Scanning beim Pairing | Bewusst nicht umgesetzt (Nutzerentscheidung, siehe `todos.md`) | – |
| Push-Benachrichtigungen (statt Pull) | Nicht umgesetzt (siehe `todos.md`) | – |

## Architekturentscheidungen

### Bestehend (Fundament)

- **DI**: Hilt. Module in `app/.../di/` (`AppModule` für Room + DataStores, `SecurityModule` für AES-GCM-Boxen, `NetworkModule` für den Ktor-Client, `CryptoModule` für die Tink-E2E-Identität).
- **Persistenz**: Room über KSP, `AppDatabase` Version **2** (`exportSchema = true`, Schemas unter `app/schemas/`, gitignored). `MIGRATION_1_2` (`data/db/Migrations.kt`) deckt alle Änderungen von Version 1 ab und ist per `Migration1To2Test` (Room `MigrationTestHelper`) abgedeckt. Kein `fallbackToDestructiveMigration`.
- **Datenbankverschlüsselung**: SQLCipher mit Android-Keystore-gewrapptem Schlüssel (`EncryptedAppDatabaseFactory`), siehe dortige Kommentare für Details zu Recovery/Schlüsselverlust.
- **Profil ist ein Singleton**, nicht an `PartnerEntity` gekoppelt.
- **API-Key- und Geräte-Token-Speicherung**: je ein eigener `AesGcmBox`-Android-Keystore-Alias (`talktome_gemini_api_key`, `talktome_device_token`), Base64 in dediziertem DataStore. Nie im Klartext in Room oder Logs. `SecureApiKeyStore.getApiKey()` fängt `GeneralSecurityException` aus dem Keystore-Decrypt (z. B. `AEADBadTagException` bei verwaistem Ciphertext nach Schlüsselverlust) ab, löscht den korrupten Eintrag und liefert `null` statt zu crashen – alle Aufrufstellen behandeln `null` bereits als „kein Key hinterlegt“.
- **Gemini-Anbindung**: eigener REST-Client (`GeminiClient`, Ktor) statt offizieller SDKs, mit Timeout, Retry/Backoff, Zeichen-Obergrenze und optionalem `history`-Parameter für Mehrfach-Turn-Gespräche (Default leer, keine Breaking Change). HTTP 401 und 403 werden beide als `GeminiErrorReason.INVALID_API_KEY` gemappt (403 tritt bei Google auch auf, wenn der Key durch eine Android-App-Restriktion – Paketname/SHA-1-Signatur – blockiert ist, z. B. wenn der Key nur für die Release-Signatur freigegeben ist und vom Debug-Build aus aufgerufen wird). HTTP 503 wird als `GeminiErrorReason.SERVICE_UNAVAILABLE` gemappt und ist retryable (Google-Modell temporär überlastet). Eine leere Kandidatenliste mit `promptFeedback.blockReason` oder `finishReason` `SAFETY`/`RECITATION` wird als `GeminiErrorReason.BLOCKED_BY_SAFETY_FILTER` erkannt statt als generisches `EMPTY_RESPONSE`. `OutgoingMessageRepository` propagiert `INVALID_API_KEY`, `SERVICE_UNAVAILABLE` und `BLOCKED_BY_SAFETY_FILTER` als eigene `OutgoingActionErrorReason`-Werte bis in `ComposeMessageScreen`/`GeminiSettingsScreen`, statt sie auf generische Fehler abzubilden.
- **Logging**: `GeminiClient`, `MailboxApi`, `SecureApiKeyStore` und `OutgoingMessageRepository` loggen jeden Request-Versuch, HTTP-Status, Retry/Backoff und Exceptions über `android.util.Log` (Tags `GeminiClient`, `MailboxApi`, `SecureApiKeyStore`, `OutgoingMessageRepo`). Fehlerantwort-Bodies von Google werden mitgeloggt (enthalten keine Nutzerdaten), Nachrichtentext/API-Key nie. `app/build.gradle.kts` setzt `testOptions.unitTests.isReturnDefaultValues = true`, wodurch `android.util.Log` in reinen JVM-Unit-Tests ohne Robolectric-Mocking nicht crasht.
- **Prompt-Sicherheit**: Nutzertext nie in die System-Instruction eingebettet, immer separat als `<nachricht>`-Block (`PromptBuilder.wrapUserMessage`), inklusive Neutralisierung von Delimiter-Kollisionen. Jede Vermittler-Prompt-Vorlage enthält `SafetyGuideline.TEXT`.
- **Gatekeeper-Format**: `MediatorPrompts.receiverGatekeeper` verlangt eine erste Zeile `ENTSCHEIDUNG: ZULASSEN`/`ENTSCHEIDUNG: ABLEHNEN`; `GatekeeperVerdict.parse` wertet das strikt aus und fällt bei Formatverstoß sicherheitshalber auf Ablehnung zurück (fail-safe statt versehentlicher Zustellung).
- **Notizen-Kontext**: `NotesContext.append` hängt vorhandene `MediatorNoteEntity`-Notizen einer Rolle an eine System-Instruction an; `MediatorPrompts.noteTaker(role)` ist eine eigene, kurze Systemanweisung für einen separaten Gemini-Aufruf, der nach einer Interaktion eine 2–3-Satz-Notiz erzeugt (sauber getrennt von der eigentlichen Chat-Antwort).
- **Freund-Meinungs-Labels und alle Mediator-Prompts sind bewusst nur auf Deutsch** (LLM-Instruktionen bzw. Domänentext, kein UI-String über i18n) – analog zur bestehenden Praxis in `MediatorPrompts`.
- **`android:allowBackup="false"`**, Zwei-Konstruktoren-Pattern bei `ProfileRepository`/`GeminiClient` für Testbarkeit ohne Dagger-Default-Value-Probleme.

### Neu (E2E-Verschlüsselung, Pairing, Server, Workflows)

- **Asymmetrische E2E-Verschlüsselung**: Google Tink (`tink-android`), HPKE-Hybrid-Template `DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`. `E2eIdentity` kapselt Verschlüsseln an einen fremden öffentlichen Schlüssel und Entschlüsseln mit dem eigenen privaten Schlüssel; ist reine Konstruktor-Injektion eines `KeysetHandle` und dadurch ohne Android-Keystore vollständig unit-testbar (`KeysetHandle.generateNew(...)` in Tests). Die Android-spezifische Schlüsselpersistenz (`AndroidKeysetManager` + Android-Keystore-Master-Key, `CryptoModule`) ist bewusst **nicht** über die vorhandene `AesGcmBox`-Abstraktion gelöst, da Tink sein eigenes, offiziell empfohlenes KMS-Muster mitbringt.
- **Pairing ohne Server-Broker für den Code selbst**: Einladungscode = Base64-JSON (`InvitePayload`: Server-Adresse, eigene Geräte-ID, eigener öffentlicher Schlüssel, Anzeigename), angezeigt als Text und QR (ZXing-Core, reine Bitmap-Erzeugung – **kein** Kamera-Scannen, siehe `todos.md`). Der Annehmende registriert sich beim selben Server, verschlüsselt eine `PairingAckPayload` mit dem öffentlichen Schlüssel des Einladenden und schickt sie über den Server (`kind = PAIRING_ACK`) – der Server sieht dabei nie Klartext, auch nicht die Pairing-Metadaten.
- **Geräte-Identität**: Ein Gerät registriert sich lazily per `POST /devices` beim ersten Pairing-Vorgang; `DeviceIdentity(deviceId, token, serverBaseUrl)` wird lokal gespeichert (`DeviceIdentityStore`). Aktuell **ein Server pro Gerät** (keine Mehrfach-Server-Unterstützung), passend zur 1:1-Partnerbeziehung.
- **Ktor-Server (`server/`-Modul)**: Reines Kotlin/JVM, Netty-Engine, kotlinx-serialization. Persistenz über SQLite via JDBC (`MailboxDatabase`, eine synchronisierte Connection, kein ORM nötig für zwei Tabellen `devices`/`mailbox`, Index auf `recipient_device_id`). Endpunkte: `POST /devices`, `POST /mailbox/{recipientDeviceId}` (Bearer-Auth, prüft `senderDeviceId`-Übereinstimmung gegen Spoofing), `GET /mailbox`, `DELETE /mailbox/{messageId}` (Pull-dann-Ack-Modell, keine dauerhafte Server-Historie). Einfaches In-Memory-Token-Bucket-Rate-Limiting pro Geräte-Token (`RateLimiter`). Kein TLS im Modul selbst (Annahme: Reverse-Proxy übernimmt das im Deployment).
- **Mailbox-Sync (`MailboxSyncRepository`)** ist die einzige Stelle, die pullt/entschlüsselt/acked; routet nach `MailboxKind` (`PAIRING_ACK` → Partner speichern, `MESSAGE` → `InboxRepository`, `REJECTION` → `OutgoingMessageRepository`). Ein Verarbeitungsschritt, der `RETRY_LATER` zurückgibt (z. B. fehlender API-Key), wird **nicht** quittiert und beim nächsten Sync erneut versucht; nicht entschlüsselbare/fremde Einträge werden verworfen und trotzdem quittiert (kein Poison-Pill-Loop). Sync läuft ausschließlich manuell (Pull-Button im Empfangs-Screen) – bewusst kein Hintergrunddienst/WorkManager, passend zum in der Idee vorgesehenen Pull-Modell.
- **Nachrichtenkorrelation**: `OutgoingMessageEnvelope.senderMessageRef`/`RejectionEnvelope.senderMessageRef` verwenden die **lokale** `OutgoingMessageEntity.id` (nicht die Server-`messageId`), da diese schon vor dem Versand feststeht und Rejections immer an dasselbe Gerät zurücklaufen, auf dem diese ID eindeutig ist. `OutgoingMessageEntity.serverMessageId` wird nach erfolgreichem Versand nur als Metadaten-Referenz gespeichert, nicht für die Korrelation gebraucht.
- **Workflow a – „Agent f/g (2x)“**: bedeutet laut `project.md` je einmal ohne und einmal mit Profil-Kontext (nicht zwei zufällige Varianten). `OutgoingMessageRepository.startNegotiation` ruft alle vier Kombinationen parallel auf, toleriert einzelne Fehlschläge (mindestens eine Meinung muss gelingen) und speichert sie als `List<FriendOpinion>`-JSON in `OutgoingMessageEntity.friendOpinionsJson`.
- **Verhandlungs-Chat**: `NegotiationTurnEntity` (neue Tabelle) speichert USER-/MEDIATOR-Turns je Nachricht; jeder `sendReply`-Aufruf schreibt den aktuellen Nutzertext gleichzeitig als neue `draftText`-Version (der Nutzer „gibt dem Vermittler immer angepasste Versionen seines Textes“ – jede Chat-Nachricht des Nutzers ist zugleich die neue Entwurfsversion).
- **Ablehnungsschleife**: Alle Rollen (h/i/j) laufen lokal auf dem jeweiligen Gerät und teilen sich dieselben `MediatorNoteEntity`-Notizen (kein geräteübergreifender Notizenaustausch). Bei Ablehnung schreibt `OutgoingMessageRepository.handleRejection` einen neuen MEDIATOR-Turn (Sender-Coach) und erhöht `rejectionCount`; `isEscalating` (Schwelle 3) steuert einen reinen Hinweis-Banner ohne Sperre.
- **Nur ein aktiver Entwurf gleichzeitig** (bewusste Scope-Vereinfachung): `OutgoingMessageRepository.activeMessage` liefert die zuletzt aktualisierte Nachricht mit Status ≠ `SENT`. Der Compose-Screen zeigt entweder ein neues leeres Eingabefeld oder – falls vorhanden – die laufende Verhandlung/Ablehnungsschleife.
- **Frust ablassen bleibt bewusst ungespeichert**: Der rohe Chatverlauf existiert nur im ViewModel-State; beim Verlassen wird ausschließlich eine kurze, über einen eigenen Gemini-Aufruf erzeugte private Notiz persistiert – zusätzliche Datensparsamkeit für den sensibelsten Anwendungsfall.

## Offene Risiken / vor echtem Build zu prüfen

- **Bibliotheksversionen konnten nicht live verifiziert werden** (kein Gradle-/Netzwerkzugriff): `ktor` (`3.1.3`), `kotlinx-serialization-json` (`1.7.3`), `tink-android` (`1.15.0`), `zxing-core` (`3.5.3`), `sqlite-jdbc` (`3.47.1.0`), `logback-classic` (`1.5.16`), SQLCipher/`androidx.sqlite`. Vor dem ersten echten Build gegen Maven Central prüfen und ggf. auf die dann aktuelle stabile Version anheben.
- **Gesamtes neues Modul (`server/`) sowie Krypto-/Pairing-/Workflow-Code wurden ohne Gradle-Build geschrieben** (Umgebungsvorgabe: keine Gradle-Ausführung). Vor dem ersten echten Release zwingend `./gradlew build`/Tests laufen lassen; Root-Cause-Fixes bei Kompilierfehlern nachreichen.
- **Tink-Registrierung**: `HybridConfig.register()` wird sowohl in `CryptoModule` (Produktion) als auch in jedem Krypto-Testfall separat aufgerufen (Tink-Registrierung ist idempotent) – falls sich das API-Verhalten der gepinnten Tink-Version unterscheidet, hier zuerst nachsehen.
- **Gemini-Modellname** `gemini-2.0-flash` sollte gegen die aktuelle Google-Modellliste geprüft werden.
- `EncryptedAppDatabaseFactory.create()` bleibt bewusst ungetestet (natives SQLCipher, nicht Robolectric-fähig); alle zusammengesetzten Bausteine sind unit-getestet.
