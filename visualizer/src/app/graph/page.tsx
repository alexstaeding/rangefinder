"use client";

import ForceGraph from "@/app/graph/forcegraph";
import {data} from "@/app/graph/data";
import {useState} from "react";
import {Node} from "./data";

export default function Page() {
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);

  const handleNodeClick = (node: Node) => {
    setSelectedNode(node);
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-between p-24">
      <ForceGraph {...data} onNodeClick={handleNodeClick}></ForceGraph>
      <p>Name: {selectedNode?.id}</p>
    </main>
  );
}
