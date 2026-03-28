"use client"

import { useEffect, useRef, useState, useCallback } from "react"

interface Node {
  id: string
  x: number
  y: number
  size: number
  status: "active" | "inactive" | "unknown" | "amendment"
}

interface Edge {
  from: string
  to: string
}

interface GraphVisualizationProps {
  onNodeSelect: (id: string | null) => void
  selectedNode: string | null
}

const generateNodes = (): Node[] => {
  const nodes: Node[] = []
  const statuses: Node["status"][] = ["active", "inactive", "unknown", "amendment"]
  
  for (let i = 0; i < 80; i++) {
    nodes.push({
      id: `node-${i}`,
      x: Math.random() * 1200 + 100,
      y: Math.random() * 600 + 100,
      size: Math.random() * 25 + 10,
      status: statuses[Math.floor(Math.random() * statuses.length)]
    })
  }
  return nodes
}

const generateEdges = (nodes: Node[]): Edge[] => {
  const edges: Edge[] = []
  for (let i = 0; i < nodes.length; i++) {
    const connections = Math.floor(Math.random() * 3) + 1
    for (let j = 0; j < connections; j++) {
      const targetIndex = Math.floor(Math.random() * nodes.length)
      if (targetIndex !== i) {
        edges.push({ from: nodes[i].id, to: nodes[targetIndex].id })
      }
    }
  }
  return edges
}

const statusColors: Record<Node["status"], { fill: string; stroke: string; glow: string }> = {
  active: { fill: "#22c55e", stroke: "#16a34a", glow: "rgba(34, 197, 94, 0.4)" },
  inactive: { fill: "#ef4444", stroke: "#dc2626", glow: "rgba(239, 68, 68, 0.4)" },
  unknown: { fill: "#a855f7", stroke: "#9333ea", glow: "rgba(168, 85, 247, 0.4)" },
  amendment: { fill: "#f59e0b", stroke: "#d97706", glow: "rgba(245, 158, 11, 0.4)" }
}

export function GraphVisualization({ onNodeSelect, selectedNode }: GraphVisualizationProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const [nodes] = useState<Node[]>(() => generateNodes())
  const [edges] = useState<Edge[]>(() => generateEdges(nodes))
  const [hoveredNode, setHoveredNode] = useState<string | null>(null)
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const [scale, setScale] = useState(1)
  const [isDragging, setIsDragging] = useState(false)
  const [lastMousePos, setLastMousePos] = useState({ x: 0, y: 0 })

  const draw = useCallback(() => {
    const canvas = canvasRef.current
    const ctx = canvas?.getContext("2d")
    if (!canvas || !ctx) return

    const rect = canvas.getBoundingClientRect()
    const dpr = window.devicePixelRatio || 1
    canvas.width = rect.width * dpr
    canvas.height = rect.height * dpr
    ctx.scale(dpr, dpr)

    ctx.fillStyle = "#0f0f0f"
    ctx.fillRect(0, 0, rect.width, rect.height)

    ctx.save()
    ctx.translate(offset.x, offset.y)
    ctx.scale(scale, scale)

    // Draw edges
    ctx.strokeStyle = "rgba(100, 100, 100, 0.15)"
    ctx.lineWidth = 0.5
    edges.forEach((edge) => {
      const fromNode = nodes.find((n) => n.id === edge.from)
      const toNode = nodes.find((n) => n.id === edge.to)
      if (fromNode && toNode) {
        ctx.beginPath()
        ctx.moveTo(fromNode.x, fromNode.y)
        ctx.lineTo(toNode.x, toNode.y)
        ctx.stroke()
      }
    })

    // Highlighted edges for selected/hovered node
    const activeNodeId = selectedNode || hoveredNode
    if (activeNodeId) {
      const relevantEdges = edges.filter((e) => e.from === activeNodeId || e.to === activeNodeId)
      ctx.strokeStyle = "rgba(168, 85, 247, 0.5)"
      ctx.lineWidth = 1.5
      relevantEdges.forEach((edge) => {
        const fromNode = nodes.find((n) => n.id === edge.from)
        const toNode = nodes.find((n) => n.id === edge.to)
        if (fromNode && toNode) {
          ctx.beginPath()
          ctx.moveTo(fromNode.x, fromNode.y)
          ctx.lineTo(toNode.x, toNode.y)
          ctx.stroke()
        }
      })
    }

    // Draw nodes
    nodes.forEach((node) => {
      const isSelected = node.id === selectedNode
      const isHovered = node.id === hoveredNode
      const colors = statusColors[node.status]

      if (isSelected || isHovered) {
        ctx.beginPath()
        ctx.arc(node.x, node.y, node.size + 8, 0, Math.PI * 2)
        ctx.fillStyle = colors.glow
        ctx.fill()
      }

      ctx.beginPath()
      ctx.arc(node.x, node.y, node.size, 0, Math.PI * 2)
      
      const gradient = ctx.createRadialGradient(
        node.x - node.size / 3,
        node.y - node.size / 3,
        0,
        node.x,
        node.y,
        node.size
      )
      gradient.addColorStop(0, colors.fill)
      gradient.addColorStop(1, colors.stroke)
      ctx.fillStyle = gradient
      ctx.fill()

      if (isSelected) {
        ctx.strokeStyle = "#fff"
        ctx.lineWidth = 2
        ctx.stroke()
      }
    })

    ctx.restore()
  }, [nodes, edges, selectedNode, hoveredNode, offset, scale])

  useEffect(() => {
    draw()
  }, [draw])

  useEffect(() => {
    const handleResize = () => draw()
    window.addEventListener("resize", handleResize)
    return () => window.removeEventListener("resize", handleResize)
  }, [draw])

  const getNodeAtPosition = (x: number, y: number): Node | null => {
    const adjustedX = (x - offset.x) / scale
    const adjustedY = (y - offset.y) / scale

    for (let i = nodes.length - 1; i >= 0; i--) {
      const node = nodes[i]
      const dx = adjustedX - node.x
      const dy = adjustedY - node.y
      if (dx * dx + dy * dy <= node.size * node.size) {
        return node
      }
    }
    return null
  }

  const handleMouseMove = (e: React.MouseEvent) => {
    const rect = canvasRef.current?.getBoundingClientRect()
    if (!rect) return

    if (isDragging) {
      const dx = e.clientX - lastMousePos.x
      const dy = e.clientY - lastMousePos.y
      setOffset((prev) => ({ x: prev.x + dx, y: prev.y + dy }))
      setLastMousePos({ x: e.clientX, y: e.clientY })
      return
    }

    const x = e.clientX - rect.left
    const y = e.clientY - rect.top
    const node = getNodeAtPosition(x, y)
    setHoveredNode(node?.id || null)
    
    if (canvasRef.current) {
      canvasRef.current.style.cursor = node ? "pointer" : "grab"
    }
  }

  const handleMouseDown = (e: React.MouseEvent) => {
    const rect = canvasRef.current?.getBoundingClientRect()
    if (!rect) return

    const x = e.clientX - rect.left
    const y = e.clientY - rect.top
    const node = getNodeAtPosition(x, y)

    if (node) {
      onNodeSelect(node.id)
    } else {
      setIsDragging(true)
      setLastMousePos({ x: e.clientX, y: e.clientY })
      if (canvasRef.current) canvasRef.current.style.cursor = "grabbing"
    }
  }

  const handleMouseUp = () => {
    setIsDragging(false)
    if (canvasRef.current) canvasRef.current.style.cursor = hoveredNode ? "pointer" : "grab"
  }

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault()
    const delta = e.deltaY > 0 ? 0.9 : 1.1
    const newScale = Math.min(Math.max(scale * delta, 0.3), 3)
    
    const rect = canvasRef.current?.getBoundingClientRect()
    if (!rect) return

    const mouseX = e.clientX - rect.left
    const mouseY = e.clientY - rect.top

    setOffset((prev) => ({
      x: mouseX - (mouseX - prev.x) * (newScale / scale),
      y: mouseY - (mouseY - prev.y) * (newScale / scale)
    }))
    setScale(newScale)
  }

  return (
    <div ref={containerRef} className="h-full w-full">
      <canvas
        ref={canvasRef}
        className="h-full w-full"
        style={{ display: "block" }}
        onMouseMove={handleMouseMove}
        onMouseDown={handleMouseDown}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
      />
    </div>
  )
}
