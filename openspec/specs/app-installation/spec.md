# app-installation Specification

## Purpose
How the app offers itself for installing, and how it looks and starts once installed. The browser draws all of it from the web manifest.

## Requirements

### Requirement: The install offer on a phone shows the app's screens
When the browser on a phone offers to install the app, the offer SHALL show the app's name and description in Russian and plain screenshots of its screens. On a computer the browser's short offer SHALL stay, without screenshots.

#### Scenario: Installing on a phone
- WHEN the learner opens the install offer in Chrome on a phone
- THEN the offer shows the name «Шпреха», a Russian description, and screenshots of the lesson, a word with its example, the statistics, the themes and the lesson at night

#### Scenario: Installing on a computer
- WHEN the learner opens the install offer in Chrome on a computer
- THEN the offer is the browser's short dialog, without screenshots

### Requirement: The installed app's icon fits the launcher's shape
The installed app's icon SHALL fill whatever shape the launcher gives it, on a white ground, with the whole «Ü» inside. The label under it SHALL read «Шпреха».

#### Scenario: A round launcher
- WHEN the launcher draws its icons as circles
- THEN the app's icon is a white circle with the whole «Ü» inside, labelled «Шпреха»

### Requirement: A long press on the icon offers the lesson and the words
A long press on the installed app's icon SHALL offer two shortcuts: «Урок», which opens the lesson, and «Слова», which opens the word list.

#### Scenario: The lesson shortcut
- WHEN the learner long-presses the app's icon and picks «Урок»
- THEN the app opens on the lesson

#### Scenario: The words shortcut
- WHEN the learner long-presses the app's icon and picks «Слова»
- THEN the app opens on the word list

### Requirement: An app installed earlier stays the same app
The manifest SHALL name the app's identity explicitly, equal to the one the browser derived before it was named.

#### Scenario: An app installed before the identity was named
- WHEN the browser reads the manifest of an app installed before the identity was named
- THEN it takes it for the same app and updates it, not offering a second one
