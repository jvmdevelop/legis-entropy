export interface Locatable {
  id: string;
  clause?: string | null;
  original: string;
  anchor?: number;
}

export interface Range {
  start: number;
  end: number;
}

export type Segment =
  | { kind: "text"; start: number; text: string }
  | { kind: "mark"; start: number; text: string; id: string };

export interface SegmentResult {
  segments: Segment[];
  ranges: Map<string, Range>;
  missing: string[];
}

const CLAUSE_LINE = /^\s*\d+(?:\.\d+)*\.?\s/;

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function clauseNumber(clause?: string | null): string | null {
  if (!clause) return null;
  const m = clause.match(/\d+(?:\.\d+)*/);
  return m ? m[0] : null;
}

function lineStarts(text: string): number[] {
  const starts = [0];
  for (let i = 0; i < text.length; i++) {
    if (text[i] === "\n") starts.push(i + 1);
  }
  return starts;
}

function clauseRegion(text: string, starts: number[], clause: string): Range | null {
  const head = new RegExp(`^\\s*${escapeRegExp(clause)}\\.?\\s`);
  for (let i = 0; i < starts.length; i++) {
    const lineEnd = i + 1 < starts.length ? starts[i + 1] : text.length;
    if (!head.test(text.slice(starts[i], lineEnd))) continue;
    let end = text.length;
    for (let j = i + 1; j < starts.length; j++) {
      const nextEnd = j + 1 < starts.length ? starts[j + 1] : text.length;
      if (CLAUSE_LINE.test(text.slice(starts[j], nextEnd))) {
        end = starts[j];
        break;
      }
    }
    return { start: starts[i], end };
  }
  return null;
}

function occurrences(text: string, needle: string): number[] {
  const out: number[] = [];
  let from = 0;
  while (needle.length > 0) {
    const idx = text.indexOf(needle, from);
    if (idx < 0) break;
    out.push(idx);
    from = idx + 1;
  }
  return out;
}

function overlaps(used: Range[], r: Range): boolean {
  return used.some((u) => r.start < u.end && u.start < r.end);
}

export function locateProposals(text: string, proposals: Locatable[]): SegmentResult["ranges"] {
  const starts = lineStarts(text);
  const used: Range[] = [];
  const ranges = new Map<string, Range>();
  const rank = (p: Locatable) => (p.anchor !== undefined ? 2 : clauseNumber(p.clause) ? 1 : 0);
  const ordered = [...proposals].sort((a, b) => rank(b) - rank(a));
  for (const p of ordered) {
    const len = p.original.length;
    if (!len || ranges.has(p.id)) continue;
    const free = (start: number) => !overlaps(used, { start, end: start + len });
    let pick: number | undefined;
    if (p.anchor !== undefined && text.startsWith(p.original, p.anchor) && free(p.anchor)) {
      pick = p.anchor;
    }
    const all = pick === undefined ? occurrences(text, p.original) : [];
    const num = clauseNumber(p.clause);
    if (pick === undefined && num) {
      const region = clauseRegion(text, starts, num);
      if (region) {
        pick = all.find((i) => i >= region.start && i + len <= region.end && free(i));
      }
    }
    if (pick === undefined) pick = all.find(free);
    if (pick === undefined) continue;
    const r = { start: pick, end: pick + len };
    used.push(r);
    ranges.set(p.id, r);
  }
  return ranges;
}

export function segmentText(text: string, proposals: Locatable[]): SegmentResult {
  const ranges = locateProposals(text, proposals);
  const marks = [...ranges.entries()].sort((a, b) => a[1].start - b[1].start);
  const segments: Segment[] = [];
  let cursor = 0;
  for (const [id, r] of marks) {
    if (r.start > cursor) {
      segments.push({ kind: "text", start: cursor, text: text.slice(cursor, r.start) });
    }
    segments.push({ kind: "mark", start: r.start, text: text.slice(r.start, r.end), id });
    cursor = r.end;
  }
  if (cursor < text.length) {
    segments.push({ kind: "text", start: cursor, text: text.slice(cursor) });
  }
  const missing = proposals.filter((p) => !ranges.has(p.id)).map((p) => p.id);
  return { segments, ranges, missing };
}
