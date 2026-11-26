import type { Segment } from "./segmentText";

export interface Span {
  start: number;
  end: number;
}

export interface PageSpan extends Span {
  page: number;
}

export type LayoutItem =
  | { kind: "page"; page: number; key: string }
  | { kind: "clause"; index: number; key: string }
  | { kind: "seg"; seg: Segment; clause: number; key: string };

interface Event {
  at: number;
  order: number;
  item: LayoutItem;
}

function splitSegments(segments: Segment[], cuts: number[]): Segment[] {
  const out: Segment[] = [];
  let ci = 0;
  for (const seg of segments) {
    if (seg.kind !== "text") {
      out.push(seg);
      continue;
    }
    const end = seg.start + seg.text.length;
    while (ci < cuts.length && cuts[ci] <= seg.start) ci++;
    let from = seg.start;
    let j = ci;
    while (j < cuts.length && cuts[j] < end) {
      out.push({ kind: "text", start: from, text: seg.text.slice(from - seg.start, cuts[j] - seg.start) });
      from = cuts[j];
      j++;
    }
    out.push({ kind: "text", start: from, text: seg.text.slice(from - seg.start) });
  }
  return out;
}

export function clauseAt(clauses: Span[], offset: number): number {
  let found = -1;
  for (let i = 0; i < clauses.length; i++) {
    if (clauses[i].start > offset) break;
    if (offset < clauses[i].end) found = i;
  }
  return found;
}

export function layoutWithStructure(
  segments: Segment[],
  textLength: number,
  pages: PageSpan[],
  clauses: Span[],
): LayoutItem[] {
  const inside = (n: number) => Number.isFinite(n) && n >= 0 && n < textLength;
  const events: Event[] = [];
  if (pages.length > 1) {
    pages.forEach((p, i) => {
      if (i > 0 && inside(p.start)) {
        events.push({ at: p.start, order: 0, item: { kind: "page", page: p.page, key: `p${i}` } });
      }
    });
  }
  clauses.forEach((c, i) => {
    if (inside(c.start)) {
      events.push({ at: c.start, order: 1, item: { kind: "clause", index: i, key: `c${i}` } });
    }
  });
  events.sort((a, b) => a.at - b.at || a.order - b.order);

  const cuts = [
    ...new Set(
      [...events.map((e) => e.at), ...clauses.map((c) => c.end)].filter((n) => inside(n) && n > 0),
    ),
  ].sort((a, b) => a - b);

  const items: LayoutItem[] = [];
  let ei = 0;
  for (const seg of splitSegments(segments, cuts)) {
    const limit = seg.kind === "mark" ? seg.start + Math.max(seg.text.length - 1, 0) : seg.start;
    while (ei < events.length && events[ei].at <= limit) items.push(events[ei++].item);
    items.push({
      kind: "seg",
      seg,
      clause: clauseAt(clauses, seg.start),
      key: seg.kind === "mark" ? `m${seg.id}` : `t${seg.start}`,
    });
  }
  return items;
}
