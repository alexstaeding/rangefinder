import {NextResponse} from "next/server";
import {ReadableStreamDefaultController} from "node:stream/web";
import {eventStream} from "@/app/api/hello/route";
import {Subscription} from "rxjs";

export async function GET(req: Request, res: NextResponse) {
  let subscription: Subscription | undefined = undefined;
  const customReadable = new ReadableStream<string>({
    start(controller: ReadableStreamDefaultController<string>) {
      subscription = eventStream.subscribe(next => controller.enqueue(JSON.stringify(next) + "\n"))
    },
    cancel() {
      subscription?.unsubscribe()
    }
  })
  return new Response(customReadable, {
    headers: {
      Connection: 'keep-alive',
      'Content-Encoding': 'application/json',
      'Cache-Control': 'no-cache, no-transform',
      'Content-Type': 'text/event-stream; charset=utf-8',
    },
  })
}
