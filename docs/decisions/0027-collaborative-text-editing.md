# 0027. Collaborative editing of text files

- **Status:** Accepted (implementation pending)
- **Date:** 2026-11-21

## Context

People who share a server also keep notes, lists, setlists and small pieces of
code next to their media. It should be possible for two or more of them to edit
one of those files at the same time, from the web UI, and see each other's
typing as it happens, as in an online document editor.

This applies to plain text only: `.txt`, Markdown (`.md`) and source code.
Editing video, audio, images or PDFs together is out of scope; nothing in the
design handles their content as text.

It belongs to version 1.0, but it needs file storage and catalog persistence to
write the file back, so it is built once those exist (ADR 0009, ADR 0019). This
record fixes the design so that the parts can be written against it.

Two problems decide the design:

- **Merging concurrent edits.** Two people typing in the same paragraph must end
  with the same text, without losing either change. The established answers are
  operational transformation (Google Docs) and CRDTs (conflict-free replicated
  data types). A CRDT lets each browser merge on its own, so the server does not
  need to understand the edits.
- **Both directions.** Each keystroke goes from a browser to the others. The live
  feed of ADR 0026 only goes from the server to the browser.

## Decision

We will build collaborative editing with a CRDT in the browser and a WebSocket
relay on the server:

- **In the browser:** the editor is CodeMirror 6 and the shared state is a Yjs
  document, joined by `y-codemirror.next`. All three are MIT-licensed. They are
  bundled once into a single file under `web/vendor/`, so the web UI keeps
  working without internet access (as with its font). Yjs shows the other
  people's cursors with their names.
- **On the server, a relay:** a primary adapter serves a WebSocket at
  `/api/v1/files/{id}/edit`. Everyone editing the same file is in one room. The
  adapter passes each Yjs update to the others in the room and keeps the room's
  updates in memory, so whoever joins later receives them and catches up. It
  never decodes them: Yjs has no Java implementation, and the relay does not
  need one.
- **Seeding the document once:** when the first person opens a file, the room is
  empty and the server tells that client, and only that one, to load the file's
  text into the Yjs document. Everyone else receives the updates. This avoids the
  known problem of two clients inserting the initial text twice.
- **Saving:** the text in the file is what was last saved, not every keystroke.
  Each client saves about two seconds after the last edit, and when the page is
  closed. Since every client converges to the same text, which one saves first
  does not matter; the server skips a save whose content has not changed.
- **Domain:**
  - a driving port `ITextEditing` with `open(u, id)`, which answers the text,
    its fingerprint and whether the user may edit it, and `save(u, id, text,
    basedOn)`;
  - it checks that the file is text (by extension and size, at most 1 MiB, UTF-8),
    that the user has `WRITE` to save and `READ` to watch (watchers join the
    room read-only and the relay drops their updates), and that `basedOn` is
    still the stored fingerprint (`VersionConflict` otherwise, for example if the
    file was replaced outside the session);
  - saving writes through `IFileStorage`, computes the new fingerprint and size,
    and records them on the `MediaFile`, which raises `FileModified`. So the
    live feed (ADR 0026) and the change history see the save;
  - the audit decorators (ADR 0025) record `FILE_EDITED` for each save, not for
    each keystroke.
- **Security:** the WebSocket is authenticated with the session cookie at the
  handshake, checks the `Origin` header against the server's own, and checks
  the user's level when joining and again on each save.
- **Who is in the room** (names and cursors) lives only in the relay and in the
  browsers; it is not stored.

## Consequences

- Two or more people can edit a note or a file of code together, with the merge
  done by a proven library instead of our own algorithm.
- The server stays simple: it authorizes, relays and saves. All the merging is in
  the browser.
- The web UI gains its first third-party code (about 300 KB, minified), bundled
  and committed with its license, and a small build step to update it.
- What was typed after the last save lives only in the open browsers and in the
  room's memory. If every browser closes at once before the next save, the last
  couple of seconds are lost. That is the price of not decoding Yjs on the
  server; it can be revisited if it proves to matter.
- A room's memory grows with its updates until the last person leaves; text
  files are small, so this is bounded in practice.
- With more than one server instance, a room would have to be shared between
  them. Version 1.0 runs one instance (as in ADR 0026).
- Pieces to build, in order: text detection and `MediaFile` content
  replacement in the model; `ITextEditing` and its service; the audit
  decorator; the WebSocket relay; the editor in the web UI.
