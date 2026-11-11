## ADDED Requirements

### Requirement: A link to the app shows a picture of the app
A link to any page of the app, shared in a messenger, SHALL show a preview with the title «Sprecha» and a wide picture of the app: the name, a line saying what the app is, and the statistics screen by day and by night, its header's stars among them a five-fold one. The preview SHALL carry no description. The page the server sends SHALL carry it, since a messenger runs no script. The preview SHALL name the app's root as the link's address for every page, and the picture by its whole address; both SHALL start at the app's public address: the one the server is configured with, a blank one counting as none, else `https://sprecha.de`. The picture SHALL be a PNG of 1200×630 under 300 KB, the most WhatsApp shows.

#### Scenario: Sharing a link in a messenger
- WHEN a learner sends a link to the app in Telegram or WhatsApp
- THEN the message shows the title «Sprecha» and the picture of the app, wide, with no description

#### Scenario: Every page
- WHEN the link leads to the word list or the lesson rather than the home screen
- THEN the preview is the same, the app's root its address

### Requirement: The picture's address changes with the picture
The picture's address SHALL name its content: a redrawn picture SHALL get a new address, an unchanged one SHALL keep its address, so a messenger or cache that keeps a picture by its address fetches a redrawn one anew.

#### Scenario: A redrawn picture
- WHEN a picture is redrawn and the app deployed
- THEN pages name it at an address no messenger has fetched before

### Requirement: The picture speaks the language the messenger asks for
The picture's line and the statistics' words SHALL be in the language of the first tag the messenger sends in `Accept-Language`, while the interface speaks that language and a picture in it is drawn. A messenger that names no language, or first names one without such a picture, SHALL get the picture in Russian.

#### Scenario: A Turkish phone
- WHEN a messenger fetches the page from a phone that speaks Turkish
- THEN the picture's line and the statistics read in Turkish

#### Scenario: No language, or one the app does not speak
- WHEN a messenger fetches the page naming no language, or German, or German first and French after it
- THEN the picture reads in Russian
