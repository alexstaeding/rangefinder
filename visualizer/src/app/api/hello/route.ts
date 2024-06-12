import {Subject} from "rxjs";
import {MyEvent} from "@/app/MyEvent";

type RequestBody = {
  message: string
}

export const eventStream = new Subject<MyEvent>();

function isValidBody<T extends Record<string, unknown>>(
  body: any,
  fields: (keyof T)[],
): T | undefined {
  return fields.every(key => key in body)
    ? body as T
    : undefined
}

export async function POST(req: Request) {
  let body = isValidBody<RequestBody>(await req.json(), ["message"])
  if (!body) {
    return new Response("Invalid request body", {status: 400})
  }
  let message = body.message
  console.log(`Received message ${message}`)
  eventStream.next({message})
  return new Response(`Received1 ${message}`)
}
