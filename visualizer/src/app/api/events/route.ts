import {NextResponse} from "next/server";
import {ReadableStreamDefaultController} from "node:stream/web";
import {nodeStream} from "@/app/api/node/route";
import {Subscription} from "rxjs";

export async function GET(req: Request, res: NextResponse) {
  let subscription: Subscription | undefined = undefined;
  const customReadable = new ReadableStream<string>({
    start(controller: ReadableStreamDefaultController<string>) {
      subscription = nodeStream.subscribe(next =>
        controller.enqueue(`data: ${JSON.stringify(next)}\n\n`))
    },
    cancel() {
      subscription?.unsubscribe()
    }
  })
  return new Response(customReadable, {
    headers: {
      Connection: 'keep-alive',
      // 'Content-Encoding': 'none',
      'Cache-Control': 'no-cache, no-transform',
      'Content-Type': 'text/event-stream; charset=utf-8',
    },
  })
}
