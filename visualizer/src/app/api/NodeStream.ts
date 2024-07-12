import {Subject} from "rxjs";
import {NodeInfoUpdate} from "@/app/api/NodeInfoUpdate";

export const nodeStream = new Subject<NodeInfoUpdate>();
