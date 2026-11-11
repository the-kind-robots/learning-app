## 1. Test first

- [x] 1.1 The sharing and the download tests expect a PNG of 1080 by 1920; regenerate `test/BEHAVIOR.md`

## 2. Picture

- [x] 2.1 `pages.stats.view/picture` places the screen's sections in a `<body>` at 360 by 640, with the app's name, the date and the address
- [x] 2.2 `adapters.sharing` paints it in a `<foreignObject>` from a `data:` URL, three times its size, with the page's readable CSS as it applies now (as CDATA) and its fonts as `data:` URLs

## 3. Verification

- [x] 3.1 The statistics spec passes; the light and the dark picture looked at in Chrome and in WebKit
