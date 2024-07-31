"use client"

import ForceGraph from "./forcegraph";
import {Data, Node} from "./data";
import {useEffect, useState} from "react";
import {NodeInfoUpdate} from "../api/NodeInfoUpdate";

export default function Page() {
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);

  const handleNodeClick = (node: Node) => {
    setSelectedNode(node);
  }

  const [data, setData] = useState<Data>({nodes: [], links: []})

  const connectToEvents = () => {
    const eventSource = new EventSource("/visualizer/api/events")
    console.log("Source: " + eventSource.url)
    eventSource.addEventListener("open", (event: Event) => {
      console.log("Opened: " + event)
    })
    eventSource.addEventListener("message", (event: MessageEvent<string>) => {
      console.log("Received message " + event.data)
      const update: NodeInfoUpdate = JSON.parse(event.data)
      setData(oldData => {
        console.log("Updating data..." + update)
        let newData: Data = {
          nodes: oldData.nodes,
          links: oldData.links,
        }

        if (!oldData.nodes.some(n => n.id == update.id)) {
          newData.nodes.push({
            id: update.id,
            nodeType: "node",
            contentUrl: update.contentUrl,
            contentKeys: update.contentKeys,
          })
        }

        update.peers.forEach(peer => {
          // it would be great to have proper sets in javascript

          if (!newData.links.some(l => l.target == peer.id || (l.target as Node)?.id == peer.id)) {
            // if this link doesn't exist
            newData.links.push({
              source: update.id,
              target: peer.id,
            })
          }

          if (!newData.nodes.some(n => n.id == peer.id)) {
            newData.nodes.push(peer)
          }
        })

        return newData
      })
    })

    eventSource.addEventListener("error", () => {
      console.log("Error")
      eventSource.close()
      setTimeout(connectToEvents, 1)
    })

    return eventSource
  }

  useEffect(() => {
    console.log("Connecting")
    const eventSource = connectToEvents()

    return () => {
      eventSource.close()
    }
  })

  return (
    <main className="flex min-h-screen flex-col items-center justify-between p-24">
      <ForceGraph {...data} onNodeClick={handleNodeClick}></ForceGraph>
      <p>
        Name: {selectedNode?.id}
      </p>
    </main>
  );
}
