import {Subject} from "rxjs";
import {NodeInfoUpdate} from "@/app/api/NodeInfoUpdate";
import {NextResponse} from "next/server";
import {isValidBody} from "@/app/api/Validator"

export const nodeStream = new Subject<NodeInfoUpdate>();

export async function PUT(req: Request) {
  let node = isValidBody<NodeInfoUpdate>(await req.json(), ["id", "peers"])
  if (!node) {
    return new NextResponse("Invalid request body", {status: 400})
  }
  console.log(`Received message from node ${node.id}`)
  nodeStream.next(node)
  return NextResponse.json({message: "Ok"}, {status: 200})
}
