/** Full-page navigation, kept in one place so tests can replace it. */
export function goTo(url: string) {
  window.location.href = url;
}
