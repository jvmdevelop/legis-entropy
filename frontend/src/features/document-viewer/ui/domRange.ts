export interface Placement {
  top: number;
  left: number;
  width: number;
}

export function markSelector(id: string): string {
  return `[data-mark="${CSS.escape(id)}"]`;
}

export function placeBelow(rect: DOMRect, box: DOMRect, width: number): Placement {
  const w = Math.min(width, Math.max(box.width - 16, 160));
  const raw = rect.left - box.left;
  const left = Math.max(8, Math.min(raw, box.width - w - 8));
  return { top: rect.bottom - box.top + 6, left, width: w };
}

export function offsetOf(node: Node, offset: number, root: HTMLElement): number | null {
  if (node.nodeType !== Node.TEXT_NODE) return null;
  const el = node.parentElement?.closest<HTMLElement>("[data-start]");
  if (!el || !root.contains(el) || el.dataset.kind === "accepted") return null;
  const start = Number(el.dataset.start);
  return Number.isFinite(start) ? start + offset : null;
}
