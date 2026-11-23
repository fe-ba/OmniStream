// Builds ../src/main/resources/web/vendor/editor.js and its LICENSES.txt.
// Run with: npm ci && npm run build
import * as esbuild from "esbuild";
import { readFileSync, writeFileSync, readdirSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const vendor = join(here, "..", "src", "main", "resources", "web", "vendor");

const result = await esbuild.build({
  entryPoints: [join(here, "entry.mjs")],
  outfile: join(vendor, "editor.js"),
  bundle: true,
  minify: true,
  format: "iife",
  globalName: "OmniEditor",
  target: ["es2022"],
  legalComments: "none",
  metafile: true,
  banner: { js: "/* OmniStream text editor: CodeMirror 6 and Yjs. Licenses in LICENSES.txt. */" },
});

// The license of every package that ended up in the bundle
const packages = new Set();
for (const input of Object.keys(result.metafile.inputs)) {
  const match = input.match(/node_modules\/((?:@[^/]+\/)?[^/]+)\//);
  if (match) packages.add(match[1]);
}
let text = "Third-party code in editor.js, bundled by src/presentation/editor-bundle.\n";
for (const name of [...packages].sort()) {
  const folder = join(here, "node_modules", name);
  const { version, license } = JSON.parse(readFileSync(join(folder, "package.json"), "utf8"));
  const file = readdirSync(folder).find((f) => /^licen[cs]e/i.test(f));
  text += `\n${"=".repeat(72)}\n${name} ${version} (${license})\n${"=".repeat(72)}\n\n`;
  text += file && existsSync(join(folder, file)) ? readFileSync(join(folder, file), "utf8").trim() + "\n" : `${license}\n`;
}
writeFileSync(join(vendor, "LICENSES.txt"), text);
console.log(`editor.js: ${packages.size} packages`);
