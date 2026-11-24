// OmniStream web UI. It talks to the REST API (docs/reference/rest-api.md) like any
// other client; the session token travels in an HttpOnly cookie set at login.
"use strict";

const API = "/api/v1";
const PROGRESS_EVERY_MS = 10_000;
const SHARE_LIFETIME_SECONDS = 7 * 24 * 3600;
const SPINE_COLORS = ["#2f6f73", "#8a4f45", "#9a7a35", "#4f5a8f", "#3f7652", "#7d4f74"];
const USERNAME_KEY = "omnistream.username";
const LEVELS = [
  ["READ", "Can watch"],
  ["WRITE", "Can edit"],
  ["MANAGE", "Manages"],
];

const state = {
  trail: [], // collections opened from the top of the library: [{id, name}]
  shared: null, // the collection of a shared link, when the page was opened from one
  playing: null, // {file, session, media} of what is playing
  progressTimer: null,
  me: null, // {userId, role, username} of who is logged in
  view: "library", // "library", "people" or "history"
  users: new Map(), // id -> UserSnapshot, for administrators
  names: new Map(), // id -> {name, kind} of every file and collection seen so far
  events: null, // the EventSource of the live changes
  refreshTimer: null,
  searching: false, // whether the library shows search results
};

const isAdmin = () => state.me !== null && state.me.role === "ADMIN";
const isGuest = () => state.shared !== null;

const $ = (id) => document.getElementById(id);

// ------------------------------------------------------------------ talking to the API

class ApiError extends Error {
  constructor(status, error, message) {
    super(message || error);
    this.status = status;
    this.error = error;
  }
}

async function api(method, path, body) {
  const init = { method, credentials: "same-origin", headers: {} };
  if (body instanceof FormData) {
    init.body = body;
  } else if (body !== undefined) {
    init.headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(body);
  }
  const response = await fetch(API + path, init);
  if (!response.ok) {
    let problem = { error: "Error", message: response.statusText };
    try {
      problem = await response.json();
    } catch (ignored) {
      // the body was not JSON; keep the status text
    }
    if (response.status === 401 && problem.error === "Unauthenticated") {
      showLogin();
    }
    throw new ApiError(response.status, problem.error, problem.message);
  }
  return response.status === 204 ? null : response.json();
}

// ------------------------------------------------------------------ small helpers

function el(tag, attributes = {}, ...children) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) {
    if (name.startsWith("on")) {
      node.addEventListener(name.slice(2), value);
    } else if (name === "style") {
      for (const [property, setting] of Object.entries(value)) node.style.setProperty(property, setting);
    } else if (value !== false && value !== undefined) {
      node.setAttribute(name, value === true ? "" : value);
    }
  }
  for (const child of children) {
    if (child !== null && child !== undefined) {
      node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
  }
  return node;
}

function svg(tag, attributes = {}, ...children) {
  const node = document.createElementNS("http://www.w3.org/2000/svg", tag);
  for (const [name, value] of Object.entries(attributes)) node.setAttribute(name, value);
  node.append(...children);
  return node;
}

let toastTimer = null;
function say(text, isError = false) {
  const toast = $("toast");
  clearTimeout(toastTimer);
  toast.textContent = text;
  toast.classList.toggle("error", isError);
  toast.hidden = !text;
  if (text) toastTimer = setTimeout(() => (toast.hidden = true), isError ? 8000 : 4000);
}

function sayError(error) {
  say(error.message || String(error), true);
}

function formatDuration(seconds) {
  const total = Math.max(0, Math.round(seconds || 0));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = String(total % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${s}` : `${m}:${s}`;
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`;
}

function extension(name) {
  const dot = name.lastIndexOf(".");
  return dot > 0 ? name.slice(dot + 1).toUpperCase().slice(0, 4) : "FILE";
}

/** A number that is always the same for the same text, to give each item its own look. */
function hash(text) {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** A waveform drawn from the file's identifier: each file of sound has its own. */
function waveform(seed, bars = 28) {
  let x = hash(seed) || 1;
  const rects = [];
  for (let i = 0; i < bars; i++) {
    x ^= x << 13;
    x ^= x >>> 17;
    x ^= x << 5;
    const envelope = Math.sin(((i + 0.5) / bars) * Math.PI);
    const height = 12 + ((x >>> 0) % 70) * (0.35 + 0.65 * envelope);
    rects.push(svg("rect", { x: i * 4, y: (100 - height) / 2, width: 2.4, height, rx: 1.2 }));
  }
  return svg(
    "svg",
    { class: "wave", viewBox: `0 0 ${bars * 4} 100`, preserveAspectRatio: "none", "aria-hidden": "true" },
    ...rects,
  );
}

// ------------------------------------------------------------------ dialogs

/** Shows the dialog and resolves true when it is confirmed. */
function ask({ title, body = [], confirm = "OK", danger = false, cancel = "Cancel", wide = false }) {
  const dialog = $("dialog");
  dialog.classList.toggle("wide", wide);
  $("dialog-title").textContent = title;
  $("dialog-body").replaceChildren(...body);
  const confirmButton = $("dialog-confirm");
  confirmButton.textContent = confirm;
  confirmButton.className = danger ? "danger" : "primary";
  const cancelButton = $("dialog-cancel");
  cancelButton.textContent = cancel || "";
  cancelButton.hidden = !cancel;
  dialog.returnValue = "";
  dialog.showModal();
  const first = $("dialog-body").querySelector("input");
  if (first) first.focus();
  return new Promise((resolve) =>
    dialog.addEventListener("close", () => resolve(dialog.returnValue === "confirm"), { once: true }),
  );
}

function textField(label, value = "") {
  const input = el("input", { required: true, value, autocomplete: "off" });
  return [el("label", {}, label, input), input];
}

// ------------------------------------------------------------------ logging in and out

function showLogin() {
  stopPlaying(false);
  stopLive();
  closeEditor();
  drawLoginArt();
  document.querySelector(".topbar").hidden = true;
  $("library").hidden = true;
  $("people").hidden = true;
  $("history").hidden = true;
  $("login").hidden = false;
  $("username").focus();
}

/** A still life of the library beside the login form: a few spines and a cover. */
function drawLoginArt() {
  const art = $("login-art");
  if (art.childElementCount > 0) return;
  const books = [
    ["Films", 12.5],
    ["Live sets", 10.75],
    ["Summer 2024", 13.25],
    ["Home videos", 12.25],
  ];
  const spines = books.map(([name, height], i) =>
    el(
      "div",
      {
        class: "spine",
        style: {
          "--spine-color": SPINE_COLORS[(i * 2 + 1) % SPINE_COLORS.length],
          "--spine-height": `${height}rem`,
        },
      },
      el("span", { class: "spine-name" }, name),
    ),
  );
  spines[spines.length - 1].classList.add("leaning");
  art.append(
    el(
      "div",
      { class: "art-shelf" },
      ...spines,
      el("div", { class: "cover", "data-type": "AUDIO" }, waveform("OmniStream")),
    ),
  );
}

function showApp() {
  const guest = isGuest();
  $("login").hidden = true;
  document.querySelector(".topbar").hidden = false;
  $("search").hidden = guest;
  $("nav").hidden = guest || !isAdmin();
  $("logout").textContent = guest ? "Leave" : "Log out";
  const me = $("me");
  me.hidden = guest || !state.me;
  if (state.me) {
    me.replaceChildren(avatar(state.me.username || "?"), el("span", { class: "me-name" }, state.me.username || "You"));
    me.title = isAdmin() ? "Administrator" : "";
  }
  showView("library");
  startLive();
}

$("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const username = $("username").value.trim();
    const token = await api("POST", "/auth/login", { username, password: $("password").value });
    state.me = { userId: token.userId, role: token.role, username };
    remember(username);
    $("password").value = "";
    say("");
    showApp();
    await openLibrary();
  } catch (error) {
    sayError(error);
  }
});

$("logout").addEventListener("click", async () => {
  stopPlaying(true);
  try {
    await api("POST", "/auth/logout");
  } catch (ignored) {
    // the session may have expired already
  }
  state.trail = [];
  state.me = null;
  state.users = new Map();
  stopLive();
  if (state.shared !== null) {
    location.assign("/");
    return;
  }
  say("");
  showLogin();
});

/** The username is only shown, never trusted: the server knows who is logged in. */
function remember(username) {
  try {
    localStorage.setItem(USERNAME_KEY, username);
  } catch (ignored) {
    // storage can be off; the name is then missing after a reload
  }
}

function remembered() {
  try {
    return localStorage.getItem(USERNAME_KEY) || "";
  } catch (ignored) {
    return "";
  }
}

// ------------------------------------------------------------------ browsing

async function openLibrary() {
  state.trail = [];
  render(await api("GET", "/library"), "Library");
}

async function openCollection(collection) {
  const at = state.trail.findIndex((step) => step.id === collection.id);
  state.trail = at >= 0 ? state.trail.slice(0, at + 1) : [...state.trail, collection];
  render(await api("GET", `/collections/${collection.id}/items`), collection.name);
}

async function refresh() {
  const current = state.trail[state.trail.length - 1];
  if (current) {
    await openCollection(current);
  } else {
    await openLibrary();
  }
}

function render(items, title, searching = false) {
  const guest = state.shared !== null;
  const current = state.trail[state.trail.length - 1];
  state.searching = searching;
  $("place-title").textContent = title;
  if (!searching) $("search-text").value = "";
  renderBreadcrumb(searching);
  $("toolbar").hidden = guest || searching;
  $("share-collection").hidden = guest || !current || searching;
  $("collection-history").hidden = guest || !current || searching;
  $("collection-access").hidden = true;
  if (!guest && current && !searching) showAccessButtonIfManager(current);

  items.forEach(know);
  const collections = items.filter((item) => item.kind === "collection");
  const files = items.filter((item) => item.kind !== "collection");
  $("shelf-section").hidden = collections.length === 0;
  $("files-section").hidden = files.length === 0;
  $("shelf").replaceChildren(...collections.map(spine));
  $("grid").replaceChildren(...files.map(tile));

  const empty = $("empty");
  empty.hidden = items.length > 0;
  empty.textContent = searching
    ? "Nothing matches. Try another word, or a tag."
    : guest
      ? "This collection is empty."
      : current
        ? "This collection is empty. Upload files here, or add them from the library."
        : "Your library is empty. Upload files to start.";
}

function renderBreadcrumb(searching) {
  const breadcrumb = $("breadcrumb");
  breadcrumb.replaceChildren();
  const steps = [];
  if (state.shared === null && (state.trail.length > 0 || searching)) {
    steps.push(el("a", { href: "#", onclick: go(() => openLibrary()) }, "Library"));
  }
  state.trail.slice(0, searching ? state.trail.length : -1).forEach((step) => {
    steps.push(el("a", { href: "#", onclick: go(() => openCollection(step)) }, step.name));
  });
  steps.forEach((step, index) => {
    if (index > 0) breadcrumb.append(" / ");
    breadcrumb.append(step);
  });
  breadcrumb.hidden = steps.length === 0;
}

function go(action) {
  return (event) => {
    event.preventDefault();
    action().catch(sayError);
  };
}

/** Varies from spine to spine, but always tall enough for the name. */
function spineHeight(name, h) {
  const varied = 10.5 + ((h >>> 8) % 30) / 10;
  const needed = 3.6 + 0.62 * name.length;
  return Math.min(16, Math.max(varied, needed));
}

function spine(collection) {
  const h = hash(collection.id);
  const count = collection.items.length;
  return el(
    "li",
    {},
    el(
      "button",
      {
        class: "spine",
        type: "button",
        "data-id": collection.id,
        title: `${collection.name}, ${count} ${count === 1 ? "item" : "items"}`,
        style: {
          "--spine-color": SPINE_COLORS[h % SPINE_COLORS.length],
          "--spine-height": `${spineHeight(collection.name, h)}rem`,
        },
        onclick: () => openCollection(collection).catch(sayError),
      },
      el("span", { class: "spine-name" }, collection.name),
      el("span", { class: "spine-count", "aria-hidden": "true" }, count),
    ),
  );
}

function cover(file) {
  let face;
  if (file.type === "AUDIO") face = waveform(file.id);
  else if (file.type === "VIDEO") face = el("span", { class: "runtime" }, formatDuration(file.duration));
  else face = el("span", { class: "extension" }, extension(file.name));
  const text = isText(file);
  const verb = file.type === "AUDIO" || file.type === "VIDEO" ? "Play" : "Open";
  return el(
    "button",
    {
      class: text ? "cover text" : "cover",
      type: "button",
      "data-type": file.type,
      "aria-label": text ? `Open ${file.name} in the editor` : `${verb} ${file.name}`,
      onclick: () => (text ? openEditor(file) : play(file)).catch(sayError),
    },
    face,
  );
}

function tile(file) {
  const meta = [];
  if (file.duration > 0 && file.type !== "VIDEO") meta.push(el("span", {}, formatDuration(file.duration)));
  meta.push(el("span", {}, formatSize(file.sizeBytes)));
  const tags = file.tags.map((tag) => el("span", { class: "tag" }, tag.name));
  const playing = state.playing && state.playing.file.id === file.id;
  return el(
    "li",
    { class: playing ? "tile playing" : "tile", "data-id": file.id },
    cover(file),
    el(
      "div",
      { class: "tile-text" },
      el("span", { class: "tile-title" }, file.name),
      el("span", { class: "tile-meta" }, ...meta),
      tags.length ? el("span", { class: "tags" }, ...tags) : null,
    ),
    state.shared === null
      ? el(
          "button",
          {
            class: "more",
            type: "button",
            "aria-label": `More for ${file.name}`,
            onclick: () => fileActions(file).catch(sayError),
          },
          svg(
            "svg",
            { viewBox: "0 0 24 24", "aria-hidden": "true" },
            svg("circle", { cx: 5, cy: 12, r: 2 }),
            svg("circle", { cx: 12, cy: 12, r: 2 }),
            svg("circle", { cx: 19, cy: 12, r: 2 }),
          ),
        )
      : null,
  );
}

// ------------------------------------------------------------------ searching

$("search").addEventListener("submit", async (event) => {
  event.preventDefault();
  const text = $("search-text").value.trim();
  if (!text) return;
  try {
    const items = await api("GET", `/search?q=${encodeURIComponent(text)}`);
    state.trail = [];
    render(items, `Results for “${text}”`, true);
  } catch (error) {
    sayError(error);
  }
});

// ------------------------------------------------------------------ changing the library

$("upload").addEventListener("change", async (event) => {
  const files = [...event.target.files];
  event.target.value = "";
  const current = state.trail[state.trail.length - 1];
  let added = 0;
  const problems = [];
  for (const file of files) {
    const form = new FormData();
    form.append("file", file);
    try {
      const result = await api("POST", "/files", form);
      if (result.duplicate) {
        problems.push(`${file.name} is already in the library`);
      } else {
        added++;
        if (current) {
          await api("PATCH", `/collections/${current.id}`, { itemsToAdd: [result.fileId] });
        }
      }
    } catch (error) {
      problems.push(`${file.name}: ${error.message}`);
    }
  }
  if (problems.length) say(problems.join(". "), true);
  else say(added === 1 ? "File added" : `${added} files added`);
  await refresh().catch(sayError);
});

$("new-collection").addEventListener("click", async () => {
  const [field, input] = textField("Name");
  const current = state.trail[state.trail.length - 1];
  const where = current ? `It goes inside ${current.name}.` : "It goes at the top of the library.";
  if (!(await ask({ title: "New collection", body: [el("p", {}, where), field], confirm: "Create" }))) {
    return;
  }
  try {
    const created = await api("POST", "/collections", { name: input.value.trim() });
    if (current) {
      await api("PATCH", `/collections/${current.id}`, { itemsToAdd: [created.id] });
    }
    say(`Collection ${created.name} created`);
    await refresh();
  } catch (error) {
    sayError(error);
  }
});

$("share-collection").addEventListener("click", async () => {
  const current = state.trail[state.trail.length - 1];
  if (!current) return;
  try {
    const link = await api("POST", `/collections/${current.id}/links`, {
      level: "READ",
      lifetime: SHARE_LIFETIME_SECONDS,
    });
    const address =
      `${location.origin}/s/${encodeURIComponent(link.token)}` +
      `?collection=${encodeURIComponent(current.id)}`;
    const input = el("input", { value: address, readonly: true, "aria-label": "Link" });
    const copy = el(
      "button",
      {
        type: "button",
        onclick: async () => {
          input.select();
          try {
            await navigator.clipboard.writeText(address);
            copy.textContent = "Copied";
          } catch (ignored) {
            copy.textContent = "Press Ctrl+C";
          }
        },
      },
      "Copy",
    );
    await ask({
      title: `Share ${current.name}`,
      body: [
        el("p", {}, "Anyone with this link can watch and listen for 7 days, without an account."),
        el("div", { class: "copy-row" }, input, copy),
      ],
      confirm: "Done",
      cancel: null,
    });
  } catch (error) {
    sayError(error);
  }
});

async function fileActions(file) {
  let chosen = null;
  const choose = (action) => () => {
    chosen = action;
    $("dialog").close("cancel");
  };
  await ask({
    title: file.name,
    body: [
      el(
        "div",
        { class: "actions-list" },
        el("button", { type: "button", onclick: choose(download) }, "Download"),
        el("button", { type: "button", onclick: choose(addToCollection) }, "Add to a collection"),
        el("button", { type: "button", onclick: choose(addTag) }, "Add a tag"),
        el("button", { type: "button", onclick: choose(itemHistory) }, "History"),
        el("button", { type: "button", class: "danger", onclick: choose(removeFile) }, "Delete from the library"),
      ),
    ],
    confirm: "Close",
    cancel: null,
  });
  if (chosen) await chosen(file);
}

/**
 * Saves the file on this device. The browser fetches it with the session cookie, so the server
 * checks the download permission and records the download in the history.
 */
function download(file) {
  const link = el("a", { href: `${API}/files/${file.id}/offline`, download: file.name, hidden: true });
  document.body.append(link);
  link.click();
  link.remove();
  say(`Downloading ${file.name}`);
}

async function addToCollection(file) {
  const known = new Map();
  for (const item of await api("GET", "/library")) {
    if (item.kind === "collection") known.set(item.id, item.name);
  }
  state.trail.forEach((step) => known.set(step.id, step.name));
  if (known.size === 0) {
    say("Create a collection first", true);
    return;
  }
  const choices = [...known].map(([id, name], index) =>
    el(
      "li",
      {},
      el("label", {}, el("input", { type: "radio", name: "collection", value: id, required: index === 0 }), name),
    ),
  );
  if (!(await ask({ title: `Add ${file.name} to`, body: [el("ul", { class: "choices" }, ...choices)], confirm: "Add" }))) {
    return;
  }
  const picked = $("dialog-body").querySelector("input[name=collection]:checked");
  if (!picked) return;
  await api("PATCH", `/collections/${picked.value}`, { itemsToAdd: [file.id] });
  say(`Added to ${known.get(picked.value)}`);
  await refresh();
}

async function addTag(file) {
  const [field, input] = textField("Tag");
  if (!(await ask({ title: `Tag ${file.name}`, body: [field], confirm: "Add tag" }))) return;
  await api("POST", `/files/${file.id}/tags`, { name: input.value.trim() });
  await refresh();
}

async function removeFile(file) {
  const confirmed = await ask({
    title: `Delete ${file.name}?`,
    body: [el("p", {}, "It leaves the library and every collection. This cannot be undone.")],
    confirm: "Delete",
    danger: true,
  });
  if (!confirmed) return;
  await api("DELETE", `/files/${file.id}`);
  say(`${file.name} deleted`);
  await refresh();
}

// ------------------------------------------------------------------ playing

async function play(file) {
  stopPlaying(true);
  // Starts where the last session on this file stopped, if it got somewhere
  const session = await api("POST", `/files/${file.id}/sessions/resume`);
  const source = `${API}/sessions/${session.id}/stream`;

  if (file.type === "DOCUMENT") {
    window.open(source, "_blank", "noopener");
    api("POST", `/sessions/${session.id}/finish`).catch(() => {});
    return;
  }
  if (file.type === "IMAGE") {
    state.playing = { file, session, media: null };
    openTheater(file.name, el("img", { src: source, alt: file.name }));
    return;
  }
  if (file.type === "VIDEO") {
    const video = el("video", { src: source, controls: true, playsinline: true });
    state.playing = { file, session, media: video };
    track(video, session);
    openTheater(file.name, video);
    return;
  }
  const audio = el("audio", { src: source, preload: "auto" });
  document.body.append(audio);
  state.playing = { file, session, media: audio };
  track(audio, session);
  openNowPlaying(file, session, audio);
  markPlaying();
}

/** Keeps the session in step with the player: position, pauses and the end. */
function track(media, session) {
  let wasPaused = false;
  media.addEventListener("loadedmetadata", () => {
    if (session.position > 0 && session.position < media.duration) media.currentTime = session.position;
    media.play().catch(() => {
      // the browser may block autoplay; the user presses play
    });
  });
  media.addEventListener("play", () => {
    if (wasPaused) {
      wasPaused = false;
      api("POST", `/sessions/${session.id}/resume`).catch(sayError);
    }
    stopProgress();
    state.progressTimer = setInterval(() => reportPosition().catch(sayError), PROGRESS_EVERY_MS);
  });
  media.addEventListener("pause", () => {
    if (media.ended || !state.playing || state.playing.media !== media) return;
    wasPaused = true;
    stopProgress();
    reportPosition()
      .then(() => api("POST", `/sessions/${session.id}/pause`))
      .catch(sayError);
  });
  media.addEventListener("ended", () => {
    stopProgress();
    reportPosition()
      .then(() => api("POST", `/sessions/${session.id}/finish`))
      .catch(sayError);
  });
}

function reportPosition() {
  const playing = state.playing;
  if (!playing || !playing.media) return Promise.resolve();
  return api("PUT", `/sessions/${playing.session.id}/position`, { position: playing.media.currentTime });
}

function stopProgress() {
  clearInterval(state.progressTimer);
  state.progressTimer = null;
}

/** Stops what is playing; with finish, it also saves the position and finishes the session. */
function stopPlaying(finish) {
  stopProgress();
  const playing = state.playing;
  state.playing = null;
  if (playing) {
    const media = playing.media;
    if (finish) {
      const save = media
        ? api("PUT", `/sessions/${playing.session.id}/position`, { position: media.currentTime })
        : Promise.resolve();
      save
        .then(() => api("POST", `/sessions/${playing.session.id}/finish`))
        .catch(() => {
          // the session may already be finished
        });
    }
    if (media) {
      media.pause();
      media.removeAttribute("src");
      media.remove();
    }
  }
  closeTheater();
  closeNowPlaying();
  markPlaying();
}

function markPlaying() {
  const id = state.playing ? state.playing.file.id : null;
  document.querySelectorAll(".tile").forEach((tile) => tile.classList.toggle("playing", tile.dataset.id === id));
}

// The theater: films and pictures take the whole screen

function openTheater(title, content) {
  $("theater-title").textContent = title;
  $("theater-stage").replaceChildren(content);
  $("theater").hidden = false;
  $("theater-close").focus();
}

function closeTheater() {
  $("theater").hidden = true;
  $("theater-stage").replaceChildren();
}

$("theater-close").addEventListener("click", () => stopPlaying(true));
$("theater").addEventListener("click", (event) => {
  if (event.target === $("theater")) stopPlaying(true);
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !$("theater").hidden) stopPlaying(true);
});

// The bar at the bottom: sound keeps playing while you browse

const PLAY_ICON = "M8 5v14l11-7z";
const PAUSE_ICON = "M7 5h4v14H7zM13 5h4v14h-4z";

function openNowPlaying(file, session, audio) {
  $("np-cover").replaceChildren(waveform(file.id, 16));
  $("np-title").textContent = file.name;
  $("np-status").textContent =
    session.position > 0 ? `Picking up at ${formatDuration(session.position)}` : "Playing";
  $("np-time").textContent = formatDuration(session.position);
  $("np-length").textContent = formatDuration(file.duration);
  const seek = $("np-seek");
  seek.max = file.duration || 0;
  seek.value = session.position;
  $("now-playing").hidden = false;
  document.body.style.setProperty("--np-height", `${$("now-playing").offsetHeight}px`);

  audio.addEventListener("loadedmetadata", () => {
    seek.max = audio.duration;
    $("np-length").textContent = formatDuration(audio.duration);
  });
  audio.addEventListener("timeupdate", () => {
    if (document.activeElement !== seek) seek.value = audio.currentTime;
    $("np-time").textContent = formatDuration(audio.currentTime);
  });
  audio.addEventListener("play", () => setToggle(true));
  audio.addEventListener("pause", () => {
    setToggle(false);
    $("np-status").textContent = audio.ended ? "Finished" : "Paused";
  });
  audio.addEventListener("playing", () => {
    $("np-status").textContent = "Playing";
  });
}

function setToggle(playing) {
  $("np-icon").setAttribute("d", playing ? PAUSE_ICON : PLAY_ICON);
  $("np-toggle").setAttribute("aria-label", playing ? "Pause" : "Play");
}

function closeNowPlaying() {
  $("now-playing").hidden = true;
  document.body.style.setProperty("--np-height", "0px");
}

$("np-toggle").addEventListener("click", () => {
  const media = state.playing && state.playing.media;
  if (!media) return;
  if (media.paused) media.play().catch(sayError);
  else media.pause();
});

$("np-seek").addEventListener("input", (event) => {
  const media = state.playing && state.playing.media;
  if (media) media.currentTime = Number(event.target.value);
});

$("np-close").addEventListener("click", () => stopPlaying(true));

// ------------------------------------------------------------------ people and names

/** A round badge with the person's initial, in a colour of their own. */
function avatar(name) {
  const color = SPINE_COLORS[hash(name) % SPINE_COLORS.length];
  return el("span", { class: "avatar", style: { "--avatar-color": color }, "aria-hidden": "true" }, (name[0] || "?").toUpperCase());
}

/** A small block in the colour of a collection's spine. */
function swatch(id) {
  return el("span", { class: "swatch", style: { "--avatar-color": SPINE_COLORS[hash(id) % SPINE_COLORS.length] }, "aria-hidden": "true" });
}

function know(item) {
  state.names.set(item.id, { name: item.name, kind: item.kind === "collection" ? "collection" : "file" });
}

/** What a person is called: "you", their username if the administrator can see it, or "someone". */
function personName(id, capital = false) {
  let name;
  if (state.me && id === state.me.userId) name = capital ? "You" : "you";
  else if (state.users.has(id)) name = state.users.get(id).username;
  else name = capital ? "Someone" : "someone";
  return name;
}

async function loadUsers() {
  if (!isAdmin()) return;
  const users = await api("GET", "/users");
  state.users = new Map(users.map((user) => [user.id, user]));
}

/** Walks the library to learn the name of every collection and file the user can see. */
async function learnNames() {
  const pending = [null];
  const seen = new Set();
  let requests = 0;
  while (pending.length && requests < 200) {
    const collection = pending.shift();
    requests++;
    const items = await api("GET", collection ? `/collections/${collection}/items` : "/library");
    for (const item of items) {
      know(item);
      if (item.kind === "collection" && !seen.has(item.id)) {
        seen.add(item.id);
        pending.push(item.id);
      }
    }
  }
}

function itemName(id, fallback = "") {
  const known = state.names.get(id);
  return known ? known.name : fallback || "something no longer in the library";
}

// ------------------------------------------------------------------ views

function showView(view) {
  state.view = view;
  $("library").hidden = view !== "library";
  $("people").hidden = view !== "people";
  $("history").hidden = view !== "history";
  $("search").hidden = isGuest() || view !== "library";
  document.querySelectorAll("#nav [data-view]").forEach((button) => {
    if (button.dataset.view === view) button.setAttribute("aria-current", "page");
    else button.removeAttribute("aria-current");
  });
}

document.querySelectorAll("#nav [data-view]").forEach((button) => {
  button.addEventListener("click", () => {
    const view = button.dataset.view;
    showView(view);
    const open = view === "people" ? openPeople : view === "history" ? openHistory : refresh;
    open().catch(sayError);
  });
});

// ------------------------------------------------------------------ the people screen (administrators)

async function openPeople() {
  await Promise.all([loadUsers(), learnNames()]);
  renderPeople();
}

function renderPeople() {
  const rows = [...state.users.values()].map((user) => {
    const collections = user.permissions.filter((p) => state.names.get(p.resource)?.kind === "collection");
    const reach =
      user.role === "ADMIN"
        ? "Reaches everything"
        : collections.length === 0
          ? "No collections yet"
          : collections.length === 1
            ? `${itemName(collections[0].resource)}`
            : `${collections.length} collections`;
    const mine = state.me && user.id === state.me.userId;
    return el(
      "li",
      { class: user.active ? "person" : "person inactive" },
      avatar(user.username),
      el(
        "div",
        { class: "person-text" },
        el("span", { class: "person-name" }, user.username, mine ? el("span", { class: "you" }, "you") : null),
        el(
          "span",
          { class: "person-meta" },
          el("span", { class: user.role === "ADMIN" ? "role admin" : "role" }, user.role === "ADMIN" ? "Administrator" : "User"),
          user.active ? el("span", {}, reach) : el("span", { class: "inactive-label" }, "Deactivated"),
        ),
      ),
      el(
        "div",
        { class: "person-actions" },
        user.active && user.role !== "ADMIN"
          ? el("button", { type: "button", onclick: () => personAccess(user).catch(sayError) }, "Access")
          : null,
        user.active && !mine
          ? el("button", { type: "button", class: "quiet", onclick: () => personMore(user).catch(sayError) }, "More")
          : null,
      ),
    );
  });
  $("people-list").replaceChildren(...rows);
}

$("add-person").addEventListener("click", async () => {
  const [nameField, name] = textField("Username");
  const password = el("input", { type: "password", required: true, autocomplete: "new-password" });
  const role = el(
    "select",
    {},
    el("option", { value: "USER" }, "User: opens what they are given"),
    el("option", { value: "ADMIN" }, "Administrator: reaches everything"),
  );
  const added = await ask({
    title: "Add a person",
    body: [
      el("div", { class: "fields" }, nameField, el("label", {}, "Password", password), el("label", {}, "Role", role)),
    ],
    confirm: "Add",
  });
  if (!added) return;
  try {
    await api("POST", "/users", { username: name.value.trim(), password: password.value, role: role.value });
    say(`${name.value.trim()} added`);
    await openPeople();
  } catch (error) {
    sayError(error);
  }
});

async function personMore(user) {
  let chosen = null;
  const choose = (action) => () => {
    chosen = action;
    $("dialog").close("cancel");
  };
  const toAdmin = user.role !== "ADMIN";
  await ask({
    title: user.username,
    body: [
      el(
        "div",
        { class: "actions-list" },
        el(
          "button",
          { type: "button", onclick: choose(() => changeRole(user, toAdmin ? "ADMIN" : "USER")) },
          toAdmin ? "Make administrator" : "Make a regular user",
        ),
        el("button", { type: "button", onclick: choose(() => personHistory(user)) }, "What they did"),
        el("button", { type: "button", class: "danger", onclick: choose(() => deactivate(user)) }, "Deactivate"),
      ),
    ],
    confirm: "Close",
    cancel: null,
  });
  if (chosen) await chosen();
}

async function changeRole(user, role) {
  await api("PUT", `/users/${user.id}/role`, { role });
  say(role === "ADMIN" ? `${user.username} is now an administrator` : `${user.username} is now a regular user`);
  await openPeople();
}

async function deactivate(user) {
  const confirmed = await ask({
    title: `Deactivate ${user.username}?`,
    body: [el("p", {}, "They are logged out at once and cannot log in again. Their history stays.")],
    confirm: "Deactivate",
    danger: true,
  });
  if (!confirmed) return;
  await api("POST", `/users/${user.id}/deactivate`);
  say(`${user.username} deactivated`);
  await openPeople();
}

/** Every collection a person can open, with its level; the administrator changes them here. */
async function personAccess(user) {
  const collections = [...state.names].filter(([, item]) => item.kind === "collection");
  const body = el("div", { class: "access" });
  const draw = () => {
    const current = state.users.get(user.id) || user;
    const rows = current.permissions
      .filter((p) => state.names.has(p.resource))
      .map((p) =>
        accessRow(swatch(p.resource), itemName(p.resource), p.level, true, {
          change: (level) => grant(user.id, p.resource, level),
          remove: () => revoke(user.id, p.resource, p.level),
        }),
      );
    const given = new Set(current.permissions.map((p) => p.resource));
    const choices = collections.filter(([id]) => !given.has(id));
    const add = choices.length
      ? addRow(
          el("select", { "aria-label": "Collection" }, ...choices.map(([id, item]) => el("option", { value: id }, item.name))),
          (select, level) => grant(user.id, select.value, level),
          "Give access",
        )
      : null;
    body.replaceChildren(
      rows.length ? el("ul", { class: "access-list" }, ...rows) : el("p", {}, `${user.username} cannot open any collection yet.`),
      add,
    );
  };
  const grant = async (userId, resource, level) => {
    await api("POST", `/users/${userId}/permissions`, { resource, level });
    await loadUsers();
    draw();
  };
  const revoke = async (userId, resource, level) => {
    await api("DELETE", `/users/${userId}/permissions/${resource}?level=${level}`);
    await loadUsers();
    draw();
  };
  draw();
  await ask({ title: `What ${user.username} can open`, body: [body], confirm: "Done", cancel: null, wide: true });
  renderPeople();
}

/** One person on an access list: their level can be changed, or taken away. */
function accessRow(badge, label, level, editable, { change, remove }, canManage = true) {
  const options = LEVELS.filter(([value]) => canManage || value !== "MANAGE");
  const control = editable
    ? el(
        "select",
        {
          "aria-label": `Level of ${label}`,
          onchange: (event) => change(event.target.value).catch(sayError),
        },
        ...options.map(([value, text]) => el("option", { value, selected: value === level }, text)),
      )
    : el("span", { class: "level-fixed" }, LEVELS.find(([value]) => value === level)?.[1] || level);
  return el(
    "li",
    {},
    badge,
    el("span", { class: "access-name" }, label),
    control,
    editable
      ? el(
          "button",
          { type: "button", class: "quiet", "aria-label": `Remove ${label}`, onclick: () => remove().catch(sayError) },
          "Remove",
        )
      : el("span", { class: "access-spacer" }),
  );
}

/** The last line of an access list: who or what to add, at which level. */
function addRow(chooser, add, label, canManage = true) {
  const level = el(
    "select",
    { "aria-label": "Level" },
    ...LEVELS.filter(([value]) => canManage || value !== "MANAGE").map(([value, text]) => el("option", { value }, text)),
  );
  const button = el("button", { type: "button", class: "primary" }, label);
  button.addEventListener("click", () => add(chooser, level.value).catch(sayError));
  return el("div", { class: "access-add" }, chooser, level, button);
}

// ------------------------------------------------------------------ who has access to a collection

/** Managers and administrators see the button; for anyone else the list answers 403. */
async function showAccessButtonIfManager(collection) {
  try {
    await api("GET", `/items/${collection.id}/access`);
    const current = state.trail[state.trail.length - 1];
    if (current && current.id === collection.id) $("collection-access").hidden = false;
  } catch (ignored) {
    // not a manager of this collection
  }
}

$("collection-access").addEventListener("click", async () => {
  const current = state.trail[state.trail.length - 1];
  if (current) await collectionAccess(current).catch(sayError);
});

async function collectionAccess(collection) {
  const admin = isAdmin();
  const body = el("div", { class: "access" });
  const draw = async () => {
    const grants = await api("GET", `/items/${collection.id}/access`);
    const rows = grants.map((g) => {
      const mine = state.me && g.user === state.me.userId;
      const editable = !mine && (admin || g.level !== "MANAGE");
      return accessRow(avatar(g.username), mine ? `${g.username} (you)` : g.username, g.level, editable, {
        change: async (level) => {
          await api("POST", `/users/${g.user}/permissions`, { resource: collection.id, level });
          await draw();
        },
        remove: async () => {
          await api("DELETE", `/users/${g.user}/permissions/${collection.id}?level=${g.level}`);
          await draw();
        },
      }, admin);
    });
    const name = el("input", { "aria-label": "Username", placeholder: "Username", autocomplete: "off" });
    body.replaceChildren(
      el(
        "p",
        {},
        admin
          ? "Everything inside this collection follows these levels."
          : "As a manager you can let people watch or edit; only an administrator names other managers.",
      ),
      rows.length ? el("ul", { class: "access-list" }, ...rows) : el("p", {}, "Nobody has been given access yet."),
      addRow(
        name,
        async (input, level) => {
          const username = input.value.trim();
          if (!username) return;
          const found = await api("GET", `/users/by-name/${encodeURIComponent(username)}`);
          await api("POST", `/users/${found.id}/permissions`, { resource: collection.id, level });
          say(`${username} can now open ${collection.name}`);
          await draw();
        },
        "Give access",
        admin,
      ),
    );
  };
  await draw();
  await ask({ title: `Who has access to ${collection.name}`, body: [body], confirm: "Done", cancel: null, wide: true });
}

// ------------------------------------------------------------------ history

const ACTION_GROUPS = {
  files: ["FILE_ADDED", "FILE_DELETED", "FILE_EDITED", "METADATA_EDITED", "FILE_TAGGED", "FILE_DOWNLOADED"],
  collections: ["COLLECTION_CREATED", "COLLECTION_MODIFIED", "LINK_CREATED", "LINK_USED"],
  access: ["USER_CREATED", "ROLE_CHANGED", "PERMISSION_GRANTED", "PERMISSION_REVOKED", "USER_DEACTIVATED"],
  logins: ["LOGGED_IN", "LOGIN_FAILED", "LOGGED_OUT"],
  admin: ["LIBRARY_REINDEXED", "LIBRARY_PATH_CHANGED", "BACKUP_CREATED"],
};

/** The group an action belongs to, which gives its mark its colour. */
function groupOf(action) {
  return Object.keys(ACTION_GROUPS).find((group) => ACTION_GROUPS[group].includes(action)) || "admin";
}

const LEVEL_VERBS = { READ: "watch", WRITE: "edit", MANAGE: "manage" };

/** One entry of the trail, said in a sentence. Names are links when the item is still there. */
function sentence(entry) {
  const who = personName(entry.actor, true);
  const whom = entry.subject ? personName(entry.subject) : "";
  const thing = () => itemLink(entry.resource, entry.detail);
  const detail = entry.detail;
  switch (entry.action) {
    case "LOGGED_IN":
      return [who, " logged in"];
    case "LOGIN_FAILED":
      return ["Someone tried to log in as ", personName(entry.actor), " with a wrong password"];
    case "LOGGED_OUT":
      return [who, " logged out"];
    case "LINK_CREATED":
      return [who, " shared ", thing(), " with a link", untilDate(detail)];
    case "LINK_USED":
      return ["A guest opened a shared link"];
    case "USER_CREATED":
      return [who, " added ", el("strong", {}, detail.split(" as ")[0] || whom), detail.endsWith("ADMIN") ? " as an administrator" : ""];
    case "ROLE_CHANGED":
      return [who, " made ", whom, detail === "ADMIN" ? " an administrator" : " a regular user"];
    case "PERMISSION_GRANTED":
      return [who, " let ", whom, ` ${LEVEL_VERBS[detail] || "open"} `, thing()];
    case "PERMISSION_REVOKED":
      return [who, " took away ", whom === "you" ? "your" : `${whom}’s`, " access to ", thing()];
    case "USER_DEACTIVATED":
      return [who, " deactivated ", whom];
    case "FILE_ADDED":
      return [who, " added ", itemLink(entry.resource, detail.split("/").pop())];
    case "FILE_DELETED":
      return [who, " deleted ", el("strong", { class: "gone" }, detail || "a file")];
    case "FILE_EDITED":
      // the detail is "<name>, <n> characters"
      return [who, " edited ", itemLink(entry.resource, detail.slice(0, Math.max(0, detail.lastIndexOf(", "))))];
    case "METADATA_EDITED": {
      const [key, ...value] = detail.split(" = ");
      return [who, " changed the ", key, " of ", itemLink(entry.resource), ` to “${value.join(" = ")}”`];
    }
    case "FILE_TAGGED":
      return [who, " tagged ", itemLink(entry.resource), " ", el("span", { class: "tag" }, detail)];
    case "COLLECTION_CREATED":
      return [who, " created the collection ", itemLink(entry.resource, detail)];
    case "COLLECTION_MODIFIED":
      return [who, " changed ", itemLink(entry.resource, detail.split(", ")[0]), detail.includes(", ") ? `: ${detail.split(", ").slice(1).join(", ")}` : ""];
    case "FILE_DOWNLOADED":
      return [who, " downloaded ", itemLink(entry.resource)];
    case "LIBRARY_REINDEXED":
      return [who, " indexed the library again (", detail, ")"];
    case "LIBRARY_PATH_CHANGED":
      return [who, " moved the library to ", el("code", {}, detail)];
    case "BACKUP_CREATED":
      return [who, " backed up the catalog to ", el("code", {}, detail)];
    default:
      return [who, ` ${entry.action.toLowerCase().replaceAll("_", " ")}`];
  }
}

function untilDate(detail) {
  const at = detail.split(" until ")[1];
  if (!at) return "";
  return ` until ${new Date(at).toLocaleDateString(undefined, { day: "numeric", month: "short" })}`;
}

/** The name of a file or collection, linked to it when it is a collection the user can open. */
function itemLink(id, fallback) {
  if (!id) return el("strong", {}, fallback || "something");
  const known = state.names.get(id);
  const name = known ? known.name : fallback || "something no longer in the library";
  if (known && known.kind === "collection") {
    return el(
      "a",
      {
        href: "#",
        onclick: go(async () => {
          if ($("dialog").open) $("dialog").close("cancel");
          showView("library");
          state.trail = [];
          await openCollection({ id, name });
        }),
      },
      name,
    );
  }
  return el("strong", { class: known ? "" : "gone" }, name);
}

/** Entries grouped by day, newest first. */
function timeline(entries) {
  const days = new Map();
  for (const entry of entries) {
    const day = new Date(entry.at).toDateString();
    if (!days.has(day)) days.set(day, []);
    days.get(day).push(entry);
  }
  const today = new Date().toDateString();
  const yesterday = new Date(Date.now() - 86_400_000).toDateString();
  return [...days].map(([day, list]) =>
    el(
      "section",
      { class: "day" },
      el(
        "h2",
        { class: "day-title" },
        day === today
          ? "Today"
          : day === yesterday
            ? "Yesterday"
            : new Date(day).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" }),
      ),
      el(
        "ol",
        { class: "entries" },
        ...list.map((entry) =>
          el(
            "li",
            { class: `entry ${groupOf(entry.action)}` },
            el("time", { datetime: entry.at }, new Date(entry.at).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", hourCycle: "h23" })),
            el("span", { class: "entry-mark", "aria-hidden": "true" }),
            el("p", { class: "entry-text" }, ...sentence(entry)),
          ),
        ),
      ),
    ),
  );
}

async function openHistory() {
  await Promise.all([loadUsers(), learnNames()]);
  const who = $("filter-who");
  const chosen = who.value;
  who.replaceChildren(
    el("option", { value: "" }, "Everyone"),
    ...[...state.users.values()].map((user) => el("option", { value: user.id, selected: user.id === chosen }, user.username)),
  );
  await drawHistory();
}

async function drawHistory() {
  const query = new URLSearchParams({ limit: "300" });
  if ($("filter-who").value) query.set("actor", $("filter-who").value);
  const group = $("filter-what").value;
  if (group) ACTION_GROUPS[group].forEach((action) => query.append("action", action));
  const days = $("filter-when").value;
  if (days) {
    const from = new Date();
    from.setHours(0, 0, 0, 0);
    from.setDate(from.getDate() - (Number(days) - 1));
    query.set("from", from.toISOString());
  }
  const entries = await api("GET", `/audit?${query}`);
  $("history-list").replaceChildren(...timeline(entries));
  $("history-empty").hidden = entries.length > 0;
}

["filter-who", "filter-what", "filter-when"].forEach((id) =>
  $(id).addEventListener("change", () => drawHistory().catch(sayError)),
);

/** The history of one file or collection, in the dialog. */
async function itemHistory(item) {
  await loadUsers().catch(() => {});
  const entries = await api("GET", `/audit?resource=${item.id}&limit=100`);
  await ask({
    title: `History of ${item.name}`,
    body: [
      entries.length
        ? el("div", { class: "timeline compact" }, ...timeline(entries))
        : el("p", {}, "Nothing has been recorded about it yet."),
    ],
    confirm: "Close",
    cancel: null,
    wide: true,
  });
}

$("collection-history").addEventListener("click", () => {
  const current = state.trail[state.trail.length - 1];
  if (current) itemHistory(current).catch(sayError);
});

async function personHistory(user) {
  showView("history");
  await openHistory();
  $("filter-who").value = user.id;
  $("filter-when").value = "";
  await drawHistory();
}

// ------------------------------------------------------------------ live changes (ADR 0026)

/** Follows the catalog: what others change shows up without reloading. */
function startLive() {
  stopLive();
  if (typeof EventSource === "undefined") return;
  const live = $("live");
  live.hidden = false;
  setLive("connecting");
  const events = new EventSource(`${API}/events`);
  state.events = events;
  // On every (re)connection: catch up with whatever happened while away
  events.addEventListener("ready", () => {
    setLive("on");
    scheduleRefresh([]);
  });
  events.addEventListener("change", (event) => {
    try {
      scheduleRefresh([JSON.parse(event.data)]);
    } catch (ignored) {
      // a malformed event changes nothing
    }
  });
  events.addEventListener("error", () => setLive("connecting"));
}

function stopLive() {
  if (state.events) state.events.close();
  state.events = null;
  clearTimeout(state.refreshTimer);
  $("live").hidden = true;
}

function setLive(mode) {
  const live = $("live");
  live.dataset.state = mode;
  live.textContent = mode === "on" ? "Live" : "Reconnecting";
  live.title =
    mode === "on"
      ? "Changes made by others appear here as they happen"
      : "Lost the connection to the server; trying again";
}

let pendingChanges = [];

/** Changes come in bursts (an upload adds a file and changes a collection): refresh once. */
function scheduleRefresh(changes) {
  pendingChanges.push(...changes);
  clearTimeout(state.refreshTimer);
  state.refreshTimer = setTimeout(applyChanges, 350);
}

async function applyChanges() {
  const changes = pendingChanges;
  pendingChanges = [];
  try {
    if (state.view === "library" && !$("library").hidden) {
      if (!state.searching) await refresh();
      const changed = new Set(changes.filter((c) => c.change !== "REMOVED").map((c) => c.item));
      document.querySelectorAll(".tile, .spine").forEach((node) => {
        if (changed.has(node.dataset.id)) {
          node.classList.remove("fresh");
          void node.offsetWidth; // restart the animation
          node.classList.add("fresh");
        }
      });
    } else if (state.view === "history") {
      await drawHistory();
    }
  } catch (error) {
    if (error.status !== 401) sayError(error);
  }
}

// ------------------------------------------------------------------ editing text together (ADR 0027)
//
// Everyone who opens the same text file is in one room of the server's relay. The browsers merge
// the edits with Yjs (a CRDT); the server passes them on without reading them, keeps them for
// whoever joins later, and saves the text when an editor sends it. Protocol: docs/reference/rest-api.md.

const TEXT_EXTENSIONS = new Set([
  "txt", "text", "md", "markdown", "log", "srt", "vtt", "csv", "tsv", "json", "xml", "yaml", "yml",
  "toml", "ini", "properties", "env", "html", "htm", "css", "js", "mjs", "ts", "jsx", "tsx", "java",
  "kt", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cs", "php", "sql", "sh", "bash", "tex",
]);
const PROSE_EXTENSIONS = new Set(["txt", "text", "md", "markdown", "log", "srt", "vtt"]);
const TEXT_MAX_BYTES = 1024 * 1024;
const SAVE_AFTER_MS = 2000;
const FRAME = { UPDATE: 0, AWARENESS: 1, STATE: 2, SAVE: 3 };
const CLOSED_BECAUSE = {
  4400: "This is not a file of the library.",
  4403: "You cannot open this file.",
  4404: "This file is no longer in the library.",
};

/** Whether a file opens in the editor: the same rule as the server's TextFilePolicy. */
function isText(file) {
  const dot = file.name.lastIndexOf(".");
  const ext = dot > 0 ? file.name.slice(dot + 1).toLowerCase() : "";
  return file.type === "DOCUMENT" && TEXT_EXTENSIONS.has(ext) && file.sizeBytes <= TEXT_MAX_BYTES;
}

let editorLibrary = null;

/** CodeMirror and Yjs, about 280 KB compressed: loaded the first time someone opens a text file. */
function loadEditorLibrary() {
  if (!editorLibrary) {
    editorLibrary = new Promise((resolve, reject) => {
      const script = el("script", {
        src: "/vendor/editor.js",
        onload: () => resolve(window.OmniEditor),
        onerror: () => {
          editorLibrary = null;
          reject(new Error("The editor could not be loaded"));
        },
      });
      document.head.append(script);
    });
  }
  return editorLibrary;
}

let editing = null; // the open editor: see openEditor

async function openEditor(file) {
  closeEditor();
  const E = await loadEditorLibrary();
  const doc = new E.Y.Doc();
  const awareness = new E.Awareness(doc);
  const me = state.me ? state.me.username || "you" : "Guest";
  const color = SPINE_COLORS[hash(me) % SPINE_COLORS.length];
  awareness.setLocalStateField("user", {
    name: me,
    id: state.me ? state.me.userId : "",
    color,
    colorLight: `${color}55`,
  });
  const scheme = location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(`${scheme}//${location.host}${API}/files/${file.id}/edit`);
  socket.binaryType = "arraybuffer";
  const session = {
    E, file, doc, awareness, socket,
    text: doc.getText("content"),
    view: null,
    live: false, // whether the view follows the shared document
    loaded: false, // whether the document has its text: seeded here or received
    editable: false,
    savedText: "", // the text of the file when it was opened
    dirty: false,
    saveTimer: null,
    conflict: false,
    closing: false,
  };
  editing = session;

  $("editor-title").textContent = file.name;
  $("editor-notice").hidden = true;
  $("editor-surface").replaceChildren();
  $("editor-people").replaceChildren();
  setEditorStatus("connecting", "Connecting…");
  $("editor").hidden = false;

  socket.addEventListener("message", (event) => {
    if (editing !== session) return;
    if (typeof event.data === "string") onEditorMessage(session, JSON.parse(event.data));
    else onEditorFrame(session, new Uint8Array(event.data));
  });
  socket.addEventListener("close", (event) => onEditorClosed(session, event));
  doc.on("update", (update, origin) => {
    if (origin === "remote") return;
    session.loaded = true;
    if (!session.editable) return;
    sendFrame(session, FRAME.UPDATE, update);
    if (origin !== "seed") markDirty(session);
  });
  awareness.on("update", ({ added, updated, removed }, origin) => {
    if (origin === "local") {
      sendFrame(session, FRAME.AWARENESS, E.encodeAwarenessUpdate(awareness, [...added, ...updated, ...removed]));
    } else if (added.length > 0) {
      // Presence is only sent when it changes: tell whoever just arrived that we are here
      announce(session);
    }
    if (editing === session) drawEditorPeople(session);
  });
}

function onEditorMessage(session, message) {
  switch (message.type) {
    case "welcome":
      announce(session);
      session.editable = message.editable;
      session.savedText = message.text;
      if (message.seed) seed(session);
      if (session.editable) {
        mountEditor(session, true);
        setEditorStatus("saved", session.loaded ? "Saved" : "Loading…");
      } else {
        // Until an editor is here, a reader sees the saved text; edits replace it when they come
        mountEditor(session, false);
        setEditorStatus("readonly", "Read only");
      }
      drawEditorPeople(session);
      break;
    case "seed":
      seed(session);
      break;
    case "saved":
      if (!session.dirty) {
        const mine = state.me && message.by === state.me.userId;
        setEditorStatus("saved", mine ? "Saved" : `Saved by ${editorName(session, message.by)}`);
      }
      break;
    case "compact":
      sendFrame(session, FRAME.STATE, session.E.Y.encodeStateAsUpdate(session.doc));
      break;
    case "error":
      onEditorError(session, message);
      break;
    default:
      break;
  }
}

/** Loads the file's text into the empty shared document: only the editor the room picks does it. */
function seed(session) {
  if (session.text.length > 0 || session.loaded) return;
  session.doc.transact(() => session.text.insert(0, session.savedText), "seed");
  if (session.view && !session.live) mountEditor(session, true);
}

function onEditorFrame(session, frame) {
  const kind = frame[0];
  const body = frame.subarray(1);
  if (kind === FRAME.UPDATE) {
    session.E.Y.applyUpdate(session.doc, body, "remote");
    session.loaded = true;
    if (!session.live) mountEditor(session, true);
    if (session.editable && !session.dirty) setEditorStatus("saved", "Saved");
  } else if (kind === FRAME.AWARENESS) {
    session.E.applyAwarenessUpdate(session.awareness, body, "remote");
  }
}

function onEditorError(session, message) {
  if (message.error === "VersionConflict") {
    session.conflict = true;
    clearTimeout(session.saveTimer);
    editorNotice(
      "Someone changed this file outside the editor after it was opened. Your edits here are not saved: " +
        "copy what you need, then close the editor and open the file again.",
    );
    setEditorStatus("error", "Not saved");
  } else if (message.error === "AccessDenied") {
    session.editable = false;
    mountEditor(session, session.live);
    editorNotice("You can no longer change this file. What you see is still up to date.");
    setEditorStatus("readonly", "Read only");
  } else if (message.error !== "NotReady") {
    setEditorStatus("error", `Not saved: ${message.message}`);
  }
}

function onEditorClosed(session, event) {
  if (session.closing || editing !== session) return;
  clearTimeout(session.saveTimer);
  const known = CLOSED_BECAUSE[event.code] || (event.code === 4422 ? event.reason : null);
  if (known) {
    $("editor-surface").replaceChildren();
    editorNotice(known);
    setEditorStatus("error", "Not opened");
    return;
  }
  session.editable = false;
  if (session.view) mountEditor(session, session.live);
  editorNotice(
    session.dirty
      ? "Lost the connection to the server. Your last edits were not saved: copy them, then open the file again."
      : "Lost the connection to the server. Everything up to here is saved; open the file again to keep editing.",
  );
  setEditorStatus("offline", "Offline");
}

/** (Re)builds the view: following the shared document, or showing the saved text until it can. */
function mountEditor(session, live) {
  const { E } = session;
  const extensions = [E.basicSetup, editorTheme(E), ...editorLanguage(E, session.file.name)];
  if (live) {
    // Undo goes to Yjs, which only undoes this person's edits; CodeMirror's history would undo
    // everyone's
    extensions.unshift(E.Prec.high(E.keymap.of(E.yUndoManagerKeymap)));
    extensions.push(E.yCollab(session.text, session.awareness));
  }
  if (!session.editable) extensions.push(E.EditorState.readOnly.of(true));
  if (session.view) session.view.destroy();
  session.live = live;
  session.view = new E.EditorView({
    state: E.EditorState.create({ doc: live ? session.text.toString() : session.savedText, extensions }),
    parent: $("editor-surface"),
  });
  if (session.editable) session.view.focus();
}

function editorLanguage(E, name) {
  const ext = name.slice(name.lastIndexOf(".") + 1).toLowerCase();
  const language = {
    md: () => E.markdown(),
    markdown: () => E.markdown(),
    js: () => E.javascript(),
    mjs: () => E.javascript(),
    jsx: () => E.javascript({ jsx: true }),
    ts: () => E.javascript({ typescript: true }),
    tsx: () => E.javascript({ jsx: true, typescript: true }),
    json: () => E.json(),
    py: () => E.python(),
    java: () => E.java(),
    css: () => E.css(),
    html: () => E.html(),
    htm: () => E.html(),
  }[ext];
  const extensions = language ? [language()] : [];
  if (PROSE_EXTENSIONS.has(ext)) extensions.push(E.EditorView.lineWrapping);
  return extensions;
}

let editorThemeCache = null;

/** The editor in the colours of the app: paper on lagoon, one colour per kind of token. */
function editorTheme(E) {
  if (editorThemeCache) return editorThemeCache;
  const t = E.tags;
  editorThemeCache = [
    E.EditorView.theme(
      {
        "&": { height: "100%", color: "var(--paper)", backgroundColor: "transparent" },
        ".cm-scroller": { fontFamily: "var(--mono)", lineHeight: "1.6" },
        ".cm-content": { padding: "1rem 0", caretColor: "var(--amber)" },
        ".cm-line": { padding: "0 1.25rem 0 0.75rem" },
        "&.cm-focused": { outline: "none" },
        ".cm-cursor, .cm-dropCursor": { borderLeftColor: "var(--amber)", borderLeftWidth: "2px" },
        "&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection": {
          backgroundColor: "color-mix(in srgb, var(--video) 28%, transparent)",
        },
        ".cm-activeLine": { backgroundColor: "color-mix(in srgb, var(--paper) 4%, transparent)" },
        ".cm-gutters": { backgroundColor: "transparent", color: "var(--mist)", border: "none" },
        ".cm-activeLineGutter": { backgroundColor: "transparent", color: "var(--paper)" },
        ".cm-lineNumbers .cm-gutterElement": { padding: "0 0.4rem 0 0.75rem", minWidth: "2.75rem" },
        ".cm-foldGutter .cm-gutterElement": { color: "var(--mist)" },
        ".cm-matchingBracket": { backgroundColor: "var(--lagoon-3)", outline: "none" },
        ".cm-searchMatch": { backgroundColor: "color-mix(in srgb, var(--document) 30%, transparent)" },
        ".cm-panels": { backgroundColor: "var(--lagoon)", color: "var(--paper)" },
        ".cm-tooltip": { backgroundColor: "var(--lagoon)", border: "1px solid var(--lagoon-3)" },
        ".cm-ySelectionInfo": {
          fontFamily: "Bricolage Grotesque, system-ui, sans-serif",
          fontSize: "0.75rem",
          fontWeight: "640",
          padding: "0.05rem 0.35rem",
          borderRadius: "4px 4px 4px 0",
          top: "-1.35em",
          opacity: "1",
        },
      },
      { dark: true },
    ),
    E.syntaxHighlighting(
      E.HighlightStyle.define([
        { tag: [t.keyword, t.operatorKeyword, t.modifier], color: "var(--video)" },
        { tag: [t.string, t.special(t.string), t.regexp], color: "var(--image)" },
        { tag: [t.number, t.bool, t.null, t.atom], color: "var(--document)" },
        { tag: [t.comment, t.meta], color: "var(--mist)", fontStyle: "italic" },
        { tag: [t.typeName, t.className, t.definition(t.variableName)], color: "var(--audio)" },
        { tag: [t.function(t.variableName), t.function(t.propertyName)], color: "var(--paper)", fontWeight: "620" },
        { tag: [t.propertyName, t.attributeName], color: "#c9dcd9" },
        { tag: [t.tagName, t.angleBracket], color: "var(--video)" },
        { tag: t.heading, color: "var(--paper)", fontWeight: "760" },
        { tag: t.strong, fontWeight: "720" },
        { tag: t.emphasis, fontStyle: "italic" },
        { tag: [t.link, t.url], color: "var(--video)", textDecoration: "underline" },
        { tag: [t.list, t.quote], color: "var(--document)" },
        { tag: t.monospace, color: "var(--image)" },
        { tag: t.processingInstruction, color: "var(--mist)" },
        { tag: t.invalid, color: "var(--error)" },
      ]),
    ),
  ];
  return editorThemeCache;
}

/** Sends this browser's presence: name, colour and cursor. */
function announce(session) {
  sendFrame(session, FRAME.AWARENESS, session.E.encodeAwarenessUpdate(session.awareness, [session.doc.clientID]));
}

function sendFrame(session, kind, bytes) {
  if (session.socket.readyState !== WebSocket.OPEN) return;
  const frame = new Uint8Array(bytes.length + 1);
  frame[0] = kind;
  frame.set(bytes, 1);
  session.socket.send(frame);
}

function markDirty(session) {
  if (session.conflict) return;
  session.dirty = true;
  setEditorStatus("unsaved", "Editing…");
  clearTimeout(session.saveTimer);
  session.saveTimer = setTimeout(() => saveEditor(session), SAVE_AFTER_MS);
}

function saveEditor(session) {
  clearTimeout(session.saveTimer);
  if (!session.dirty || !session.editable || session.conflict || !session.loaded) return;
  session.dirty = false;
  setEditorStatus("saving", "Saving…");
  sendFrame(session, FRAME.SAVE, new TextEncoder().encode(session.text.toString()));
}

/** Saves what is pending, says goodbye to the others and closes the editor. */
function closeEditor() {
  const session = editing;
  if (!session) return;
  editing = null;
  session.closing = true;
  saveEditor(session);
  session.E.removeAwarenessStates(session.awareness, [session.doc.clientID], "local");
  session.socket.close(1000, "Closed");
  if (session.view) session.view.destroy();
  session.awareness.destroy();
  session.doc.destroy();
  $("editor").hidden = true;
}

/** Who is in the room, from the presence each browser shares: you first, then the others. */
function drawEditorPeople(session) {
  const seen = new Map();
  for (const [client, presence] of session.awareness.getStates()) {
    const user = presence.user;
    if (!user) continue;
    const key = user.id || user.name;
    const mine = client === session.doc.clientID;
    if (!seen.has(key) || mine) seen.set(key, { ...user, mine });
  }
  const people = [...seen.values()].sort((a, b) => (b.mine ? 1 : 0) - (a.mine ? 1 : 0));
  $("editor-people").replaceChildren(
    ...people.map((user) =>
      el(
        "li",
        { title: user.mine ? `${user.name} (you)` : user.name },
        el("span", { class: "avatar", style: { "--avatar-color": user.color }, "aria-hidden": "true" }, (user.name[0] || "?").toUpperCase()),
        el("span", { class: "visually-hidden" }, user.mine ? `${user.name} (you)` : user.name),
      ),
    ),
  );
  const others = people.filter((user) => !user.mine).map((user) => user.name);
  $("editor-with").textContent =
    others.length === 0 ? "Only you" : `With ${others.length > 2 ? `${others.slice(0, 2).join(", ")} and ${others.length - 2} more` : others.join(" and ")}`;
}

function editorName(session, userId) {
  for (const presence of session.awareness.getStates().values()) {
    if (presence.user && presence.user.id === userId) return presence.user.name;
  }
  return personName(userId);
}

function setEditorStatus(mode, text) {
  const status = $("editor-status");
  status.dataset.state = mode;
  status.textContent = text;
}

function editorNotice(text) {
  const notice = $("editor-notice");
  notice.textContent = text;
  notice.hidden = false;
}

$("editor-close").addEventListener("click", closeEditor);
$("editor").addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !event.defaultPrevented) closeEditor();
});
window.addEventListener("pagehide", () => {
  if (editing) saveEditor(editing);
});

// ------------------------------------------------------------------ starting

async function start() {
  const shared = location.pathname.match(/^\/s\/([^/]+)$/);
  if (shared) {
    const collection = new URLSearchParams(location.search).get("collection");
    try {
      await api("POST", `/links/${shared[1]}/access`);
      state.shared = collection || "";
      state.me = null;
      showApp();
      if (collection) {
        state.trail = [];
        await openCollection({ id: collection, name: "Shared with you" });
      } else {
        render(await api("GET", "/library"), "Shared with you");
      }
    } catch (error) {
      document.querySelector(".topbar").hidden = true;
      $("login").hidden = true;
      $("library").hidden = true;
      say(error.status === 410 ? "This link has expired. Ask for a new one." : `This link does not work: ${error.message}`, true);
    }
    return;
  }
  try {
    const session = await api("GET", "/auth/session");
    state.me = { userId: session.userId, role: session.role, username: remembered() };
    showApp();
    await openLibrary();
  } catch (error) {
    if (error.status !== 401) sayError(error);
    showLogin();
  }
}

start();
