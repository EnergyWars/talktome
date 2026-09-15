# Styling-Ausnahmen TalkToMe

## Custom-Elemente

| Element | Datei | Kriterium (Abschnitt 7) | Begründung |
|---|---|---|---|
| – | – | – | Keine Custom-Elemente vorhanden. Alle UI-Elemente nutzen Library-Bausteine aus `uikit`. |

## Abweichungen vom Basis-Projekt

| Thema | Basis-Projekt | Diese App | Grund |
|---|---|---|---|
| `AppRadius`-Werte | `card=20dp`, `chip=6dp`, `textField=14dp` | `card=12dp`, `chip=8dp`, `textField=4dp` | Die Styling-Regeln-Datei hat laut Rangfolge Vorrang vor dem Basis-Projekt; ihre Abschnitt-2-Tabelle nennt diese Werte explizit, das Basis-Projekt weicht davon ab. |
| `AppDialogConfirmButton` | Verwendet `ButtonVariant.Filled` | Verwendet `ButtonVariant.Tonal` | Abschnitt 4 der Styling-Regeln fordert das ausdrücklich, damit Dialoge dem Primary-Tonal-Standard folgen. |
| `SettingsListRow`, `SettingsGroup`, `AppSideNavDrawer` | Unterstützen einen optionalen `inspectCode`/Element-Inspector (Showcase-Debug-Werkzeug) | Ohne Element-Inspector-Anbindung | Der Element-Inspector ist reines Debug-Tooling der Showcase-App im Basis-Projekt; TalkToMe hat keine Showcase-Seite, daher wurde die Abhängigkeit zu `uikit.showcase.*` nicht mitübernommen. |
| `AppTextField` | Kein `supportingText`, kein `visualTransformation`, kein `trailingIcon`, kein `keyboardOptions`, kein `singleLine`-Parameter | Alle fünf Parameter ergänzt | Abschnitt 5 der Styling-Regeln fordert `isError` + `supportingText` für Validierung; für das Gemini-API-Schlüssel-Feld wird zusätzlich eine Passwort-Maskierung (`visualTransformation`) und ein Sichtbarkeits-Icon (`trailingIcon`) benötigt. Der Basis-Baustein wurde dafür erweitert statt im Screen nachgebaut. |

Diese Abweichungen sind für alle WaffleHQ-Apps sinnvoll und sollten ins Basis-Projekt zurückgeführt werden.
