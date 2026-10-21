// Removes the reCAPTCHA strings (used only by Element Call's sign-up page, and naming
// Google) from every translation in Element Call's locales/ folder.
//
//   node strip-locales.mjs <element-call>/locales
import { readdirSync, readFileSync, writeFileSync, statSync } from "node:fs";
import { join } from "node:path";

const root = process.argv[2];
if (!root) throw new Error("usage: strip-locales.mjs <locales dir>");

const strip = (obj) => {
  let removed = 0;
  for (const key of Object.keys(obj)) {
    if (/recaptcha/i.test(key)) {
      delete obj[key];
      removed++;
    } else if (obj[key] && typeof obj[key] === "object") {
      removed += strip(obj[key]);
    }
  }
  return removed;
};

let total = 0;
for (const lang of readdirSync(root)) {
  const dir = join(root, lang);
  if (!statSync(dir).isDirectory()) continue;
  for (const file of readdirSync(dir).filter((f) => f.endsWith(".json"))) {
    const path = join(dir, file);
    const json = JSON.parse(readFileSync(path, "utf8"));
    const removed = strip(json);
    if (removed) writeFileSync(path, JSON.stringify(json, null, 2) + "\n");
    total += removed;
  }
}
console.log(`   removed ${total} reCAPTCHA strings from the translations`);
