export { useDocumentViewer } from "./model/useDocumentViewer";
export type {
  DocumentViewerModel,
  ViewerSuggestion,
  SuggestionStatus,
  AppliedResult,
} from "./model/useDocumentViewer";
export { segmentText, locateProposals } from "./lib/segmentText";
export type { Segment, SegmentResult, Locatable } from "./lib/segmentText";
export { DocumentText } from "./ui/DocumentText";
export { SuggestionsPanel } from "./ui/SuggestionsPanel";
export { markSelector } from "./ui/domRange";
export { useDocumentStructure } from "./model/useDocumentStructure";
export type { DocumentStructureModel, TypeStat } from "./model/useDocumentStructure";
export { structureApi } from "./api/structureApi";
export type {
  DocumentStructure,
  StructureClause,
  StructurePage,
  StructureSection,
  ClauseType,
} from "./api/structureApi";
export { layoutWithStructure } from "./lib/structureLayout";
export { StructurePanel } from "./ui/StructurePanel";
