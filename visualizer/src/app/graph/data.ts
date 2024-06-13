import {SimulationLinkDatum, SimulationNodeDatum} from "d3-force";
import {NodeInfo} from "@/app/api/NodeInfoUpdate";

export interface Node extends SimulationNodeDatum, NodeInfo {
}

export interface Link extends SimulationLinkDatum<Node> {
}

export interface Data {
  nodes: Node[];
  links: Link[];
}
