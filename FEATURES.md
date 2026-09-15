# Features von TalkToMe

Diese Datei beschreibt alle fertiggestellten Features der App vollständig. Noch nicht umgesetzte Teile der Idee stehen in `todos.md`.

## Profil-Konfiguration

Screen unter „Profil“ (erreichbar über Startseite, Menü oder Einstellungen). Enthält drei mehrzeilige Textfelder, die automatisch bei jeder Änderung lokal gespeichert werden:

- **Ich-Beschreibung**: Wie der Nutzer sich selbst beschreibt. Wird den KI-Beratern als Kontext mitgegeben.
- **Partner-Beschreibung**: Wie der Nutzer seinen Partner beschreibt. Wird den KI-Beratern als Kontext mitgegeben.
- **Filter**: Anforderungen an Nachrichten, die der Nutzer nicht lesen möchte. Bleibt geheim – der Partner erfährt nie den Inhalt, nur ob eine Nachricht deswegen abgelehnt wurde.

Es gibt aktuell genau ein Profil pro Gerät (kein Verwalten mehrerer Partner, siehe „Offene Punkte“ in `todos.md`).

## Gemini-Verbindung

Screen unter „Gemini-Verbindung“. Der Nutzer trägt seinen eigenen Gemini-API-Schlüssel ein:

- Eingabefeld mit Ein-/Ausblenden-Funktion (wie ein Passwortfeld).
- „Speichern & testen“ prüft den Schlüssel per echtem API-Aufruf, bevor er gespeichert wird.
- Der Schlüssel wird ausschließlich verschlüsselt auf dem Gerät gespeichert (Android Keystore), niemals im Klartext.
- Ein Hinweisbanner erklärt, dass Texte zur Verarbeitung an Google Gemini gesendet werden, aber nie an den Partner oder einen Server dieser App.
- Fehleranzeige für ungültigen Schlüssel, Rate-Limit und fehlende Internetverbindung.
- „Schlüssel entfernen“ mit Bestätigungsdialog.

## Partner koppeln

Screen unter „Partner koppeln“. Verbindet zwei Geräte Ende-zu-Ende-verschlüsselt über den TalkToMe-Server, ohne dass der Server jemals Klartext oder private Schlüssel zu sehen bekommt:

- **Einladung erstellen**: Erzeugt einen Einladungscode (Server-Adresse, eigene Geräte-ID, eigener öffentlicher Schlüssel, Anzeigename), kodiert als Text und zusätzlich als QR-Code zum Anzeigen/Teilen.
- **Einladung annehmen**: Der andere Partner tippt den Code ein oder fügt ihn ein (z. B. aus der Zwischenablage). Der eigene öffentliche Schlüssel wird daraufhin verschlüsselt an den Server übertragen, adressiert an den Einladenden.
- Der Einladende bekommt beim nächsten Abgleich die Bestätigung, entschlüsselt sie und speichert den Partner lokal – ab dann sind beide Geräte gekoppelt.
- Verbindungsstatus-Karte zeigt den Namen des gekoppelten Partners, mit „Partner trennen“ (Bestätigungsdialog). Die Notizen der KI-Berater bleiben beim Trennen erhalten.
- Fehleranzeige für Netzwerkprobleme, ungültige Codes und vom Server abgelehnte Anfragen.
- Kein Kamera-Scannen (bewusste Entscheidung): Der QR-Code wird nur angezeigt, nie über die Kamera gescannt.

## Nachricht formulieren (Modus a – „Ich will dir etwas mitteilen“)

Screen unter „Nachricht formulieren“:

1. Der Nutzer schreibt einen Text, den er seinem Partner mitteilen möchte.
2. Vier KI-Berater analysieren den Text parallel: Partner-Vermuttler und Ich-Vermuttler, jeweils einmal ohne und einmal mit dem Profil-Kontext (Ich-/Partner-Beschreibung). Ihre Einschätzungen werden angezeigt.
3. Ein neutraler Vermittler fasst alle vier Einschätzungen zusammen und gibt Feedback zum Text.
4. Verhandlungs-Chat: Der Nutzer kann beliebig oft mit dem Vermittler hin und her schreiben und dabei immer wieder angepasste Versionen seines Textes einreichen.
5. „Trotzdem senden“ ist jederzeit möglich, unabhängig vom Stand der Verhandlung.
6. Beim Senden wird der aktuelle Text Ende-zu-Ende verschlüsselt an den Partner über den Server übertragen.
7. Lehnt der Empfänger-Vermittler die Nachricht ab, kommt eine verschlüsselte Ablehnung zurück: Ein Sender-Coach hilft bei der Überarbeitung, die Verhandlung geht automatisch weiter. Nach mehreren Ablehnungen derselben Nachricht erscheint ein Hinweisbanner (keine Sperre).
8. Der Vermittler merkt sich private, gerätelokale Notizen über die Beziehung, die nie angezeigt werden und den nächsten Gesprächen als Kontext dienen.

## Nachrichten von deinem Partner (Empfang)

Screen unter „Nachrichten von deinem Partner“:

- Manueller Abgleich („Nach neuen Nachrichten suchen“) ruft die Mailbox vom Server ab.
- Jede eingehende Nachricht wird vor der Anzeige von einem Empfänger-Vermittler anhand der eigenen Filter-Konfiguration geprüft.
- Zugelassene Nachrichten erscheinen in der Liste inklusive kurzem Vermittler-Feedback („Dein Partner möchte dir sagen …“).
- Abgelehnte Nachrichten werden **nie** angezeigt und hinterlassen keine Spur – stattdessen geht eine verschlüsselte, die Filter nicht offenlegende Ablehnungsbegründung zurück an den Sender.

## Frust ablassen (Modus b)

Screen unter „Frust ablassen“:

- Freier Chat mit einem KI-Begleiter, der sich nur ums Zuhören und Sortieren der Gedanken kümmert.
- Bleibt vollständig lokal: nichts wird an den Partner oder einen Server gesendet.
- Beim Verlassen des Screens wird nur eine kurze private Notiz (kein Wortlaut) für künftige Gespräche gespeichert; der eigentliche Gesprächsverlauf wird nicht dauerhaft gespeichert.
- Button „Stattdessen eine Nachricht formulieren“ wechselt manuell in Modus a, ohne den Inhalt zu übernehmen.

## Vermittler-Notizen verwalten

Screen unter „Vermittler-Notizen“: Erklärt, dass die KI-Berater sich über die Zeit private Notizen zur Beziehung merken, die niemand einsehen kann. Einzige Aktion: „Notizen zurücksetzen“ (mit Bestätigungsdialog) löscht alle Notizen unwiderruflich.

## Anzeige-Einstellungen

Screen unter „Anzeige“: Umschalten zwischen hellem, dunklem und System-Design. Die Wahl wird gespeichert und beim nächsten App-Start wiederhergestellt.

## Navigation

Startseite mit kurzer Einführung und Schnellzugriff auf alle Kernfunktionen (Nachricht formulieren, Frust ablassen, Nachrichten vom Partner, Profil, Partner koppeln, Gemini-Verbindung). Seitenmenü (Drawer) und Einstellungsseite bieten dieselben Ziele zusätzlich strukturiert an.

## Datenschutz- und Sicherheitsarchitektur (technische Grundlage, kein eigener Screen)

- Alle Profildaten und Vermittler-Notizen liegen ausschließlich lokal in einer Room-Datenbank, die per SQLCipher mit einem Android-Keystore-geschützten Schlüssel verschlüsselt ist.
- Der Gemini-API-Schlüssel und das Geräte-Token liegen zusätzlich einzeln Android-Keystore-verschlüsselt.
- Nachrichten werden Ende-zu-Ende mit dem öffentlichen Schlüssel des Partners verschlüsselt (Hybrid-Verschlüsselung, X25519/HKDF/AES-256-GCM); der Server sieht ausschließlich Chiffretext, auch beim Pairing-Handshake.
- Ein eigener Ktor-Server (`server/`-Modul) dient nur als blinder Pull-Mailbox-Transport: Er speichert Chiffretext-Nachrichten adressiert an eine Geräte-ID und liefert sie auf Abruf aus.
- Automatisches Cloud-Backup ist für die App deaktiviert, damit keine sensiblen Beziehungsinhalte unbeabsichtigt in einer Geräte-Cloud landen.
