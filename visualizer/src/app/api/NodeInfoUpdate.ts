export type NodeInfoUpdate = {
  id: string
  peers: NodeInfo[]
}

export type NodeInfo = {
  id: string
  nodeType: "node" | "value"
}
