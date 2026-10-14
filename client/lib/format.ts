export function formatRuntime(minutes?: number | null): string {
  if (!minutes || minutes <= 0) return '';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

export function formatMeta(year?: number | null, minutes?: number | null): string {
  return [year ? String(year) : '', formatRuntime(minutes)].filter(Boolean).join(' · ');
}
