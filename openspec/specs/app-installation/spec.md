# app-installation Specification

## Purpose
How the app offers itself for installing, and how it looks and starts once installed. The browser draws all of it from the web manifest.

## Requirements

### Requirement: The install offer on a phone shows the app's screens
When the browser on a phone offers to install the app, the offer SHALL show the app's name, its description and plain screenshots of its screens, all in the language the page speaks to that browser: the language of its system when the interface has it, else English. A language with no screenshots of its own SHALL show the English interface's. On a computer the browser's short offer SHALL stay, without screenshots.

#### Scenario: Installing on a phone
- WHEN the learner whose phone speaks Russian opens the install offer in Chrome
- THEN the offer shows the name «Шпреха», a Russian description, and screenshots of the lesson, a word with its example, the statistics, the collections and the lesson at night, in the Russian interface

#### Scenario: Installing on a Turkish phone
- WHEN the learner whose phone speaks Turkish opens the install offer in Chrome
- THEN the offer shows a Turkish name and description and screenshots of the Turkish interface

#### Scenario: A language added without screenshots
- WHEN a language joins the interface with its dictionary alone, and a learner whose phone speaks it opens the install offer
- THEN the offer speaks that language and shows the screenshots of the English interface

#### Scenario: Installing on a computer
- WHEN the learner opens the install offer in Chrome on a computer
- THEN the offer is the browser's short dialog, without screenshots

### Requirement: The installed app's icon fits the launcher's shape
The installed app's icon SHALL fill whatever shape the launcher gives it, on a white ground, with the whole «Ü» inside. The label under it SHALL be the app's word mark in the language the offer spoke: «Шпреха» in Russian, «Sprecha» in the others.

#### Scenario: A round launcher
- WHEN the launcher draws its icons as circles
- THEN the app's icon is a white circle with the whole «Ü» inside, labelled with the word mark

### Requirement: A long press on the icon offers the lesson and the words
A long press on the installed app's icon SHALL offer two shortcuts, named in the language the offer spoke: the lesson («Урок» in Russian, «Ders» in Turkish), which opens the lesson, and the words («Слова», «Kelimeler»), which opens the word list.

#### Scenario: The lesson shortcut
- WHEN the learner long-presses the app's icon and picks the lesson
- THEN the app opens on the lesson

#### Scenario: The words shortcut
- WHEN the learner long-presses the app's icon and picks the words
- THEN the app opens on the word list

### Requirement: An app installed earlier stays the same app
The manifest SHALL name the app's identity explicitly, equal to the one the browser derived before it was named.

#### Scenario: An app installed before the identity was named
- WHEN the browser reads the manifest of an app installed before the identity was named
- THEN it takes it for the same app and updates it, not offering a second one

### Requirement: The install offer has its manifest without a network
Online the browser SHALL read the manifest fresh, in the language the page speaks to it. Without a network it SHALL get the last manifest read online, from the first visit on.

#### Scenario: Installing on a computer without a network
- WHEN the learner opened the app online in Russian for the first time, then lost the network, and opens the install offer in Chrome on a computer
- THEN the offer is there, with the name «Шпреха»

#### Scenario: The system's language changed since the first visit
- WHEN the learner's browser read the manifest online in Turkish after a first visit in Russian, then lost the network
- THEN the browser gets the Turkish manifest
