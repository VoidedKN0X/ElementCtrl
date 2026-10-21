// Smoke test for ElementCtrl's build of Element Call: opens it the way ElementCtrl does (as a widget, top-level,
// with the widget API messages going to the page itself) in headless Chrome, and checks that
//   - it starts up as a widget and tells the host it has loaded (the widget API's content_loaded), and
//   - it doesn't try to reach anything on the web except the homeserver it was given.
// Run from Element Call's checkout (for its @playwright/test):  node smoke-test.mjs <dist dir>
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize, resolve, sep } from "node:path";
import { chromium } from "@playwright/test";

const root = resolve(process.argv[2] ?? "dist");
const types = {
  ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".css": "text/css",
  ".json": "application/json", ".wasm": "application/wasm", ".svg": "image/svg+xml", ".png": "image/png",
  ".woff2": "font/woff2", ".woff": "font/woff", ".mp3": "audio/mpeg", ".ogg": "audio/ogg",
};
const server = createServer(async (req, res) => {
  let path = decodeURIComponent(new URL(req.url, "http://localhost").pathname);
  if (path === "/") path = "/index.html";
  // ElementCtrl serves an empty config in place of config.json.
  if (path === "/config.json") {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end("{}");
  }
  const file = normalize(join(root, path));
  if (!file.startsWith(root + sep)) {
    res.writeHead(403);
    return res.end();
  }
  try {
    const body = await readFile(file);
    res.writeHead(200, { "content-type": types[extname(file)] ?? "application/octet-stream" });
    res.end(body);
  } catch {
    res.writeHead(404);
    res.end();
  }
});
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const origin = `http://127.0.0.1:${server.address().port}`;
const homeserver = "https://matrix.example.org";

let browser;
try {
  browser = await chromium.launch({ channel: "chrome" });
} catch (e) {
  console.log(`::warning title=Element Call smoke test skipped::Couldn't start Chrome: ${String(e).split("\n")[0]}`);
  server.close();
  process.exit(0);
}

const page = await browser.newPage();
const external = [];
const toHomeserver = [];
const errors = [];
const fromWidget = new Set();
await page.route("**/*", (route) => {
  const url = new URL(route.request().url());
  if (url.origin === origin) return route.continue();
  if (url.origin === homeserver) toHomeserver.push(url.pathname);
  else external.push(url.href);
  return route.abort();
});
page.on("pageerror", (e) => errors.push(String(e).split("\n")[0]));
await page.exposeFunction("__ecSmoke", (action) => fromWidget.add(action));
// Like ElementCtrl's WidgetBridge: listen for the widget's requests posted to the page itself.
await page.addInitScript(() =>
  window.addEventListener("message", (e) => {
    const d = e.data;
    if (d && typeof d === "object" && d.api === "fromWidget" && !d.response) window.__ecSmoke(String(d.action));
  }),
);

// The same parameters ElementCtrl's widget URL carries (see CallActivity), with test values.
const params = new URLSearchParams({
  widgetId: "smoke-test",
  parentUrl: `${origin}/index.html`,
  roomId: "!smoke:example.org",
  userId: "@smoke:example.org",
  deviceId: "SMOKETEST",
  baseUrl: homeserver,
  lang: "en",
  theme: "dark",
  intent: "start_call",
  controlledAudioDevices: "true",
  confineToRoom: "true",
  appPrompt: "false",
  perParticipantE2EE: "true",
  hideHeader: "true",
  allowIceFallback: "false",
});
await page.goto(`${origin}/index.html#?${params}`);

const deadline = Date.now() + 45_000;
while (!fromWidget.has("content_loaded") && Date.now() < deadline) await page.waitForTimeout(500);
await page.waitForTimeout(3_000);
const rendered = await page.evaluate(() => document.getElementById("root")?.childElementCount ?? 0);
await browser.close();
server.close();

const summary = [
  `widget requests: ${[...fromWidget].join(", ") || "none"}`,
  `page rendered: ${rendered > 0 ? "yes" : "no"}`,
  `requests to the homeserver: ${toHomeserver.length}`,
  `requests elsewhere: ${external.length ? external.join(", ") : "none"}`,
  `page errors: ${errors.length ? errors.join(" | ") : "none"}`,
].join("%0A");
console.log(`::notice title=Element Call smoke test::${summary}`);

const failures = [];
if (!fromWidget.has("content_loaded")) failures.push("it never told the host it had loaded (content_loaded)");
if (rendered === 0) failures.push("nothing was rendered");
if (external.length) failures.push(`it tried to reach ${external.join(", ")}`);
if (failures.length) {
  console.log(`::error title=Element Call smoke test failed::${failures.join("; ")}`);
  process.exit(1);
}
