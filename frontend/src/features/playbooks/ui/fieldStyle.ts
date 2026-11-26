import type { CSSProperties } from "react";

export const SANS = "var(--mono)";
export const MONO = "var(--mono)";

export const fieldStyle: CSSProperties = {
  fontFamily: SANS,
  fontSize: "12px",
  background: "var(--color-bg)",
  border: "1px solid var(--color-border)",
  color: "var(--color-text)",
  borderRadius: 10,
  padding: "6px 8px",
  width: "100%",
  minWidth: 0,
};
