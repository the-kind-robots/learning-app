// The size an image really has, `1200x630`, loaded in the page the way the browser loads one a page names.
const imageSize = (page, src) => page.evaluate((src) => new Promise((resolve, reject) => {
  const image = new Image();
  image.onload = () => resolve(`${image.naturalWidth}x${image.naturalHeight}`);
  image.onerror = () => reject(new Error(`${src} did not load`));
  image.src = src;
}), src);

module.exports = { imageSize };
