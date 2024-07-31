export type NodeInfoUpdate = {
  id: string
  peers: NodeInfo[]
  contentUrl?: string
  contentKeys?: string[]
}

export type NodeInfo = {
  id: string
  nodeType: "node" | "value"
}
