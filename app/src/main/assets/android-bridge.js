/* Android-only download adapter. No visual redesign or remote service is added. */
(() => {
  "use strict";
  const originalClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    const anchor = this;
    if (!anchor.download || !anchor.href.startsWith("blob:")) {
      return originalClick.call(anchor);
    }
    if (!window.MomntExport) {
      alert("Update Android System WebView to enable exports.");
      return;
    }
    fetch(anchor.href).then(response => response.blob()).then(blob => {
      if (blob.size > 24 * 1024 * 1024) throw new Error("Export exceeds 24 MB.");
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve({
          name: anchor.download,
          mime: blob.type.split(";")[0],
          base64: String(reader.result).split(",")[1]
        });
        reader.onerror = reject;
        reader.readAsDataURL(blob);
      });
    }).then(payload => window.MomntExport.postMessage(JSON.stringify(payload)))
      .catch(() => alert("Could not prepare this export. Please try again."));
  };
})();
