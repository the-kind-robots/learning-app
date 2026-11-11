## Why

The statistics' shared picture was laid out on its own, shape by shape, and at 4:5 it squeezed what the screen shows
(#788). The owner wants it as airy as the screen, and made of the screen's own markup.

## Changes

- The picture is the statistics screen's own markup on a phone 360 by 640, drawn three times over: 1080 by 1920,
  9:16. Its sections are the screen's; it adds the app's name and today's date over them and the address under
  them, and shows the last fourteen days across its width.
- The sharing adapter paints the markup in a `<foreignObject>` with the page's CSS and fonts in it; the page's CSS
  as it applies now carries the scheme.

## Capabilities

### New Capabilities

### Modified Capabilities

- `statistics`: the shared picture is 1080 by 1920 pixels.

## Decisions

- 9:16 at three times the screen, over 4:5 at twice it: the text stays as large as before, and the picture reads
  as a phone's screen. An Instagram feed crops 9:16 to 4:5; stories and messengers show it whole. Chosen by the
  owner from both drawn side by side.
- The screen's markup in a `<foreignObject>`, over SVG charts drawn for both: one layout, no coordinates in code.
  Measured in WebKit 27.2 and Chrome 144: an SVG image with a `<foreignObject>` leaves the canvas clean from a
  `data:` URL and taints it from a `blob:` URL; WebKit paints a faded or positioned box in a `<foreignObject>`
  without a `viewBox`'s scale, so the SVG stays 360 by 640 and the canvas draws it three times its size, which
  both engines paint as vectors. The markup sits in a `<body>`, so the page's rules for it reach the picture.
