import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { structureApi, type DocumentStructure } from "../api/structureApi";

export interface TypeStat {
  code: string;
  label: string;
  count: number;
  first: number;
}

function clauseKey(value: string | null | undefined): string | null {
  const m = value?.match(/\d+(?:\.\d+)*/);
  return m ? m[0] : null;
}

function scrollTo(selector: string) {
  requestAnimationFrame(() =>
    document.querySelector(selector)?.scrollIntoView({ behavior: "smooth", block: "center" }),
  );
}

export function useDocumentStructure(documentId: string, ready: boolean) {
  const [structure, setStructure] = useState<DocumentStructure | null>(null);
  const [failed, setFailed] = useState(false);
  const [typeFilter, setTypeFilter] = useState<Set<string>>(() => new Set());
  const [flash, setFlash] = useState<number | null>(null);
  const [openClause, setOpenClause] = useState<number | null>(null);
  const flashTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => {
    if (!ready) return;
    let alive = true;
    structureApi
      .get(documentId)
      .then((s) => {
        if (alive) setStructure(s);
      })
      .catch(() => {
        if (alive) setFailed(true);
      });
    return () => {
      alive = false;
    };
  }, [documentId, ready]);

  useEffect(() => () => clearTimeout(flashTimer.current), []);

  const clauses = useMemo(
    () => [...(structure?.clauses ?? [])].sort((a, b) => a.start - b.start),
    [structure],
  );
  const pages = useMemo(() => structure?.pages ?? [], [structure]);

  const typeStats = useMemo(() => {
    const map = new Map<string, TypeStat>();
    clauses.forEach((c, i) => {
      new Set(c.types.map((t) => t.code)).forEach((code) => {
        if (code === "OTHER") return;
        const label = c.types.find((t) => t.code === code)?.label ?? code;
        const stat = map.get(code);
        if (stat) stat.count++;
        else map.set(code, { code, label, count: 1, first: i });
      });
    });
    return [...map.values()].sort((a, b) => b.count - a.count);
  }, [clauses]);

  const matches = useCallback(
    (index: number) => {
      if (typeFilter.size === 0 || index < 0) return true;
      return clauses[index]?.types.some((t) => typeFilter.has(t.code)) ?? false;
    },
    [clauses, typeFilter],
  );

  const toggleType = useCallback((code: string) => {
    setTypeFilter((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  }, []);

  const focusClause = useCallback((index: number) => {
    scrollTo(`[data-clause-anchor="${index}"]`);
    setFlash(index);
    clearTimeout(flashTimer.current);
    flashTimer.current = setTimeout(() => setFlash(null), 1800);
  }, []);

  const focusPage = useCallback((page: number) => {
    scrollTo(page <= 1 ? "[data-dv-text]" : `[data-page-start="${page}"]`);
  }, []);

  const findClause = useCallback(
    (number: string | null | undefined) => {
      const key = clauseKey(number);
      if (!key) return -1;
      return clauses.findIndex((c) => clauseKey(c.number) === key);
    },
    [clauses],
  );

  return {
    structure,
    loading: ready && !structure && !failed,
    failed,
    clauses,
    pages,
    typeStats,
    typeFilter,
    clearFilter: () => setTypeFilter(new Set()),
    toggleType,
    matches,
    flash,
    openClause,
    setOpenClause,
    focusClause,
    focusPage,
    findClause,
  };
}

export type DocumentStructureModel = ReturnType<typeof useDocumentStructure>;
