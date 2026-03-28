"use client"

import { useState } from "react"
import { Header } from "@/components/header"
import { DocumentComparison } from "@/components/document-comparison"
import { GraphVisualization } from "@/components/graph-visualization"
import { Inspector } from "@/components/inspector"
import { GraphLegend } from "@/components/graph-legend"

export default function DocumentAnalysisPage() {
  const [selectedDocument, setSelectedDocument] = useState<string | null>("P960000790")
  const [isComparisonOpen, setIsComparisonOpen] = useState(true)
  const [isInspectorOpen, setIsInspectorOpen] = useState(true)

  return (
    <div className="flex h-screen flex-col bg-background text-foreground">
      <Header onInspectorToggle={() => setIsInspectorOpen(!isInspectorOpen)} isInspectorOpen={isInspectorOpen} />
      
      <div className="flex flex-1 overflow-hidden">
        <main className="relative flex-1 overflow-hidden">
          {isComparisonOpen && (
            <DocumentComparison onClose={() => setIsComparisonOpen(false)} />
          )}
          
          <GraphVisualization 
            onNodeSelect={setSelectedDocument}
            selectedNode={selectedDocument}
          />
          
          <GraphLegend />
        </main>

        {isInspectorOpen && (
          <Inspector 
            documentId={selectedDocument} 
            onClose={() => setIsInspectorOpen(false)}
          />
        )}
      </div>
    </div>
  )
}
