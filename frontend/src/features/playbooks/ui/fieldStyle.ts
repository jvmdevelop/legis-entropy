import type { CSSProperties } from "react";

export const SANS = "'IBM Plex Sans', sans-serif";
export const MONO = "'IBM Plex Mono', monospace";

export const fieldStyle: CSSProperties = {
  fontFamily: SANS,
  fontSize: "12px",
  background: "var(--color-bg, #fff)",
  border: "1px solid var(--color-border)",
  color: "var(--color-text)",
  borderRadius: 10,
  padding: "6px 8px",
  width: "100%",
  minWidth: 0,
};
