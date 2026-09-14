# Die Idee
Es wird eine Chat-App, mit der man mit seinem Partner sprechen kann, aber von KI gefiltert.
Es gibt einen Server für den Transport. Beide haben die selbe App und die selben Möglichkeiten.

# Definitionen
## a "Ich will dir etwas mitteilen" (Funktion)
## b "Ich muss meinen Frust ablassen" (Funktion)
## c Filter (Konfiguration)
## d Ich-Beschreibung (Konfiguration)
## e Partner-Beschreibung (Konfiguration)

## f Partner-Vermuttler (KI-Agent)
### Prompt
Du bist mein Berater/Sozialpädagoge/Therapeut/guter Freund. Mein Partner hat mir eine Nachricht geschrieben. Sag mir, was du über ihn denkst. Was denkst du über ihn? Was ist er für ein Mensch? Wie geht es ihm gerade? Ist er ehrlich zu mir? Was versucht er, mir zu sagen? Ist das was er sagt fair? Und wie sollte ich deiner Meinung nach auf diesen Text reagieren? Was sind seine Wunden Punkte?
Die Nachricht ist:

## g Ich-Vermuttler (KI-Agent)
### Prompt
Du bist mein Berater/Sozialpädagoge/Therapeut/guter Freund. Ich möchte meinem Partner die folgende Nachricht senden. Sag mir, was du über mich jetzt denkst. Was denkst du jetzt über mich? Was bin ich für ein Mensch? Wie geht es mir wohl gerade? Bin ich ehrlich zu meinem Partner? Was will ich wohl meinem Partner mitteilen? Ist das, was ich sage, fair? Und wie sollte ich deiner Meinung nach auf diesen Text reagieren?
Die Nachricht ist:

## h Neutraler Vermittler (KI-Agent)
### Prompt
Du bist ein neutraler Vermittler. Eine Person will ihrem Partner etwas mitteilen. Im Folgenden siehst du sowohl den original-Text als auch die Meinungen von Freunden des Partners und Freunden des Absenders.
Fasse alle wichtigen Punkte zusammen und gib dem Sender eine Rückmeldung, was er bei dem Text besser machen könnte.
Was fällt dir auf, was haben alle gemeinsam? Und wo unterscheiden sich stark die Meinungen derer, die die Hintergründe der Personen berücksichtigen? Du sprichst jetzt mit der Person. Gib ihr Feedback zu ihrem Text.
Was sollte sie unbedingt vermeiden und was sollte sie bedenken? Welche Formulierungen sind gut und welche sollten überdacht werden?

## i Vermittler (KI-Agent)
### Prompt
Du bist ein neutraler Vermittler. Du siehst hier eine Nachricht an eine Person, die aber nicht getriggert werden möchte und daher anforderungen an Nachrichten hat. Prüfe, ob sie diesen Anforderungen entspricht und lehne sie entweder ab oder lasse sie zu. Wenn du sie zulässt, füge der Nachricht noch hinzu, was deiner Meinung nach der Partner dem anderen mitteilen will, wie es ihm geht und worum es ihm geht. Wenn du sie ablehnst, füge eine kurze Begründung bei, aber ohne die Filter zu offenbaren.

## j Vermittler (KI-Agent)
### Prompt
Du bist ein neutraler Vermittler. Diese Nachricht wurde vom Partner leider aus bestimmten Gründen abgelehnt. Erkläre dem Sender, was er besser machen könnte, aber schütze die Privatsphäre des Empfängers (also teile möglichst nicht den Grund der Ablehnung mit).


# Ablauf
Gemini wird über API-Key hinzugefügt. Der User trägt ihn selbst ein.
Ich schreibe eine Nachricht oder ganz viele Lange Texte, ich kotze mich richtig über meine Partnerin aus und beschreibe die Situation so, wie sie aus meiner Perspektive war. Es soll zwei Modi geben. Einmal (a) "Ich will dir etwas mitteilen" und einmal (b). "Ich muss meinen Frust ablassen".
Unabhängig davon kann ich in meinem Profil mehrere Sachen angeben. Einen Filter (c), Beschreibung über mich (d), Beschreibung meines Partners (e).


## Workflow a
In Modus a ist der ablauf wie folgt: Ich schreibe einen Text mit Dingen, die ich meinem Partner sagen will oder was ich mir gewünscht hätte oder was ich vielleicht von ihm wissen oder haben will und warum. Dann geht diese Nachricht an mehrere 

### Freunde
KI-Agenten:
1. Agent f (ohne weiteren Kontext)
2. Agent f (Inklusive der Kontexte d und e)
3. Agent g (ohne weiteren Kontext)
4. Agent g (Inklusive der Kontexte d und e)

### Vermittler
Die Ergebnisse der KI-Agenten werden alle gesammelt und wie folgt an den KI-Agenten h versendet:

Der Original-Text: ....

1. Das sagen Freunde des Empfängers, ohne den Sender zu kennen oder den Empfänger zu berücksichtigen.
2. Das sagen Freunde des Empfängers, die sowohl Sender als auch Empfänger kennen.
3. Das sagen Freunde des Senders, ohne den Empfänger zu kennen oder den Sender zu berücksichtigen.
4. Das sagen Freunde des Senders, die sowohl Sender als auch Empfänger kennen.

### Verhandlung
Der Sender kann jetzt so lange er will mit dem Vermittler hin und her schreiben und ihm immer angepasste versionen seines textes geben. Der Sender darf aber jederzeit den Text senden.

### Wichtig:
Nichts von dem, was bis hier hin passiert ist, darf jemals zum Partner gelangen. Der Vermittler macht sich Notizen, auf die er in jedem weiteren Gespräch Zugreifen kann. Aber der Sender bekommt diese niemals zu Gesicht. Und auch diese verlassen niemals das Handy. Der Vermittler soll sich merken, mit was für Menschen er es zu tun hat.

### Übertragung
Der Text wird an den Server übertragen, hier kann der partner über ein pull-mechanismus die Nachrichten abfragen.
Bevor aber der Empfänger die Nachricht zu gesicht bekommt, bekommt der Vermittler i des Empfängers diese Nachricht zu sehen und soll anhand der Filter c entscheiden, ob der Empfänger diese Nachricht zu Gesicht bekommen soll. Wenn der Vermittler i meint, dass es zu den Filtern. Der Vermittler i hat zugriff und darf ebenso die notizen von vermittler h anpassen.
Wenn er entscheidet, dass der Empfänger die Nachricht sehen darf, dann bekommt er diese als neue Nachricht angezeigt. Wenn nicht, wird die Nachricht zurück an den Server geschickt als abgelehnt mit einer kurzen Begründung, aber ohne den Inhalt der Filter zu offenbaren. Diese Nachricht kommt dann wieder beim ursprünglichen Sender an und dessen Vermittler j (der ebenfalls zugriff auf die Notizen von h und i hat) hilft ihm dabei, seinen Text anzupassen und dann kann er ihn wieder versenden. Das passiert so lange, bis die Nachricht erfolgreich angenommen wurde. Kommt die Nachricht an, gibt Vermittler i kurz Feedback nach dem Motto "Dein Partner möchte dir folgendes sagen, ich glaube er fühlt sich xxx und möchte nur yyy". Eine vom Vermittler i abgelehnte Nachricht darf nicht angezeigt werden. Es darf für den Empfänger nicht erkennbar sein, dass jemals eine Nachricht da war, wenn sie abgelehnt wurde. Die Vermittler vermerken sich ebenfalls für sich privat, wenn sie an abgelehnten Nachrichten etwas über den Sender oder Empfänger lernen.



