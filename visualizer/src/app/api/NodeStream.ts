import {Subject} from "rxjs";
import {NodeInfoUpdate} from "./NodeInfoUpdate";

export const nodeStream = new Subject<NodeInfoUpdate>();
