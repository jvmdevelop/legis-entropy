import { Link, Navigate, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { DocumentReviewer } from "@/widgets/document-reviewer";

export default function DocumentViewerPage() {
  const { documentId } = useParams<{ documentId: string }>();
  if (!documentId) return <Navigate to="/documents" replace />;
  return (
    <div className="le-dv-scroll">
      <div className="le-dv-page">
        <DocumentReviewer
          key={documentId}
          documentId={documentId}
          actions={
            <Link to="/documents" className="le-btn" style={{ textDecoration: "none" }}>
              <ArrowLeft /> документы
            </Link>
          }
        />
      </div>
    </div>
  );
}
