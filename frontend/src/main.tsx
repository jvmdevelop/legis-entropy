import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "d3-transition";
import "./index.css";
import "./styles/legacy-entropy.css";
import App from "./App";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
