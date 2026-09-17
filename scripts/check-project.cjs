/* Static packaging and export-adapter checks; not an Android or browser test. */
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const root = path.resolve(__dirname, "..");
const assets = path.join(root, "app/src/main/assets");
const html = fs.readFileSync(path.join(assets, "index.html"), "utf8");
const bridge = fs.readFileSync(path.join(assets, "android-bridge.js"), "utf8");

async function main() {
  const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>[\s\S]*?<\/script>/g)];
  for (const script of scripts) new vm.Script(script[1] || "");
  new vm.Script(bridge);
  const photos = [...html.matchAll(/var (?:Qv|Zv|Jv|\$v|e0|t0|a0|o0)="(\.\/.+?)";/g)];
  assert.equal(photos.length, 8);
  for (const [, file] of photos) {
    const image = fs.readFileSync(path.join(assets, file));
    assert.equal(image[0], 0xff);
    assert.equal(image[1], 0xd8);
  }
  assert(html.includes('src="android-bridge.js"'));
  assert(html.indexOf('src="android-bridge.js"') < html.indexOf("<script>"));
  assert(html.includes("Content-Security-Policy"));
  assert(!html.includes("Your pulse card is downloaded"));
  assert(!html.includes("Spending export downloaded"));
  assert(fs.existsSync(path.join(root, ".github/workflows/android.yml")));

  const messages = [], alerts = [];
  let normalClicks = 0;
  class Anchor { click() { normalClicks++; } }
  class Reader {
    readAsDataURL(blob) {
      blob.arrayBuffer().then(data => {
        this.result = `data:${blob.type};base64,${Buffer.from(data).toString("base64")}`;
        this.onload();
      }).catch(error => this.onerror(error));
    }
  }
  const context = {
    HTMLAnchorElement: Anchor, FileReader: Reader,
    fetch, Blob, URL, Promise,
    window: { MomntExport: { postMessage: text => messages.push(JSON.parse(text)) } },
    alert: text => alerts.push(text)
  };
  vm.runInNewContext(bridge, context);
  const sampleExports = [
    ["momnt-my-data.json", "application/json", JSON.stringify({name:"Tester",moments:[]})],
    ["momnt-spending.csv", "text/csv", 'Date,Merchant,Amount INR\n2026-09-17,"Coffee",240'],
    ["momnt-daily-pulse.svg", "image/svg+xml", '<svg xmlns="http://www.w3.org/2000/svg"><text>Momn\'t</text></svg>']
  ];
  for (const [name,mime,body] of sampleExports) {
    const link = new Anchor();
    link.href = URL.createObjectURL(new Blob([body], {type:mime}));
    link.download = name;
    link.click();
    for (let i=0;i<100 && !messages.some(m=>m.name===name);i++) {
      await new Promise(resolve=>setTimeout(resolve,10));
    }
    const message = messages.find(m=>m.name===name);
    assert(message, "Export did not reach the native-channel mock");
    assert.equal(message.mime, mime);
    assert.equal(Buffer.from(message.base64,"base64").toString("utf8"), body);
    URL.revokeObjectURL(link.href);
  }
  const normal = new Anchor();
  normal.download = "";
  normal.href = "#moments";
  normal.click();
  assert.equal(normalClicks, 1);
  context.window.MomntExport = null;
  const unavailable = new Anchor();
  unavailable.download = "test.json";
  unavailable.href = "blob:unavailable";
  unavailable.click();
  assert.equal(alerts.length, 1);
  console.log("PASS: bundled app/adapter JavaScript syntax");
  console.log("PASS: all 8 active sample JPEG assets are packaged");
  console.log("PASS: adapter order, policy, workflow, and export wording");
  console.log("PASS: CSV, JSON, and SVG export payloads, including UTF-8");
  console.log("PASS: ordinary link fallback and missing-channel warning");
  console.log("NOT TESTED: Android compilation, native UI, browser rendering, or device installation");
}
main().catch(error => { console.error(error); process.exitCode = 1; });
