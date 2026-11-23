// What the web UI needs from CodeMirror 6 and Yjs, gathered on one global: OmniEditor.
// app.js loads web/vendor/editor.js the first time someone opens a text file.
export { EditorView, keymap } from "@codemirror/view";
export { EditorState, Compartment, Prec } from "@codemirror/state";
export { basicSetup } from "codemirror";
export { HighlightStyle, syntaxHighlighting } from "@codemirror/language";
export { tags } from "@lezer/highlight";
export { markdown } from "@codemirror/lang-markdown";
export { javascript } from "@codemirror/lang-javascript";
export { json } from "@codemirror/lang-json";
export { python } from "@codemirror/lang-python";
export { java } from "@codemirror/lang-java";
export { css } from "@codemirror/lang-css";
export { html } from "@codemirror/lang-html";
export * as Y from "yjs";
export { yCollab, yUndoManagerKeymap } from "y-codemirror.next";
export {
  Awareness,
  encodeAwarenessUpdate,
  applyAwarenessUpdate,
  removeAwarenessStates,
} from "y-protocols/awareness";
