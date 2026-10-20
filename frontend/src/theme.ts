export type Theme = "light" | "dark";
const key = "furocash_theme";
const changed = "furocash:theme-changed";
export function initializeTheme() {
  let theme: Theme = window.matchMedia?.("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
  try {
    const saved = localStorage.getItem(key);
    if (saved === "dark" || saved === "light") theme = saved;
  } catch {
    /* Storage may be blocked. */
  }
  applyTheme(theme);
}
function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute("content", theme === "dark" ? "#101a16" : "#194a3a");
  window.dispatchEvent(new Event(changed));
}
export const getTheme = (): Theme =>
  document.documentElement.dataset.theme === "dark" ? "dark" : "light";
export function setTheme(theme: Theme) {
  try {
    localStorage.setItem(key, theme);
  } catch {
    /* Theme still works for this visit. */
  }
  applyTheme(theme);
}
export function subscribeTheme(listener: () => void) {
  const storage = (event: StorageEvent) => {
    if (event.key === key || event.key === null) initializeTheme();
  };
  window.addEventListener(changed, listener);
  window.addEventListener("storage", storage);
  return () => {
    window.removeEventListener(changed, listener);
    window.removeEventListener("storage", storage);
  };
}
