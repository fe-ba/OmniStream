# Editor bundle

Builds `src/main/resources/web/vendor/editor.js`, the code the web UI uses for
the collaborative text editor (ADR 0027): CodeMirror 6, its languages
(Markdown, JavaScript and TypeScript, JSON, Python, Java, CSS, HTML) and Yjs
with its CodeMirror binding and awareness protocol. It also writes
`vendor/LICENSES.txt`, with the license of every package in the bundle (all
MIT).

The built file is committed, so the Maven build and the server need neither
Node.js nor internet access. Rebuild it only to update a library or to add a
language:

```bash
cd src/presentation/editor-bundle
npm ci          # installs the exact versions of package-lock.json
npm run build   # writes ../src/main/resources/web/vendor/
```

To update, change the versions in `package.json` (`npm install <package>@<version>
--save-exact`), build, try the editor with the demo server (two browsers, `ana`
and `luis`, the "Notes" collection) and commit `package.json`,
`package-lock.json`, `editor.js` and `LICENSES.txt` together.

`entry.mjs` lists what the web UI uses; `app.js` finds it on the global
`OmniEditor`, which it loads the first time someone opens a text file.
