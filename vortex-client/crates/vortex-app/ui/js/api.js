// Bridge to the backend. In the Windows app this is Tauri IPC to Rust; in
// a plain browser (design previews) it falls back to a demo backend.
import { mock } from "./mock.js";

const T = window.__TAURI__;
export const isTauri = !!(T && T.core && T.core.invoke);
const backend = mock;

export function call(cmd, args = {}) {
  return isTauri ? T.core.invoke(cmd, args) : backend.invoke(cmd, args);
}

export function on(event, handler) {
  if (isTauri) return T.event.listen(event, (e) => handler(e.payload));
  return backend.listen(event, handler);
}

/** Files dropped on the window (Tauri only). */
export function onFileDrop(handler) {
  if (!isTauri || !T.webview || !T.webview.getCurrentWebview) return;
  T.webview.getCurrentWebview().onDragDropEvent((e) => {
    if (e.payload && e.payload.type === "drop") handler(e.payload.paths || []);
    else if (e.payload) handler(null, e.payload.type);
  });
}
