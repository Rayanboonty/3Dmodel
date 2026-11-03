// Bridge to the Rust backend. Inside the Vortex app this is Tauri IPC; in a
// plain browser (design previews) it falls back to a demo backend.
import { mock } from "./mock.js";

const T = window.__TAURI__;
export const isTauri = !!(T && T.core && T.core.invoke);

export function call(cmd, args = {}) {
  return isTauri ? T.core.invoke(cmd, args) : mock.invoke(cmd, args);
}

export function on(event, handler) {
  if (isTauri) return T.event.listen(event, (e) => handler(e.payload));
  return mock.listen(event, handler);
}

/** Files dropped on the window (Tauri only). */
export function onFileDrop(handler) {
  if (!isTauri || !T.webview || !T.webview.getCurrentWebview) return;
  T.webview.getCurrentWebview().onDragDropEvent((e) => {
    if (e.payload && e.payload.type === "drop") handler(e.payload.paths || []);
    else if (e.payload) handler(null, e.payload.type);
  });
}
