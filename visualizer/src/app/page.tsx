"use client";

import {useEffect, useState} from "react";

export default function Home() {

  let [bar, setBar] = useState<string | null>(null)
  //
  // useEffect(() => {
  //   const eventSource = new EventSource("/api/events");
  //   console.log("Test1")
  //   eventSource.addEventListener('message', (event: MessageEvent) => {
  //     let update = JSON.parse(event.data) as NodeInfoUpdate
  //     console.log(`Received message ${update}`)
  //     setBar(body.message);
  //   })
  //
  //   eventSource.addEventListener('open', (event: Event) => {
  //     console.log("Connection opened", event)
  //   })
  //
  //   eventSource.addEventListener('error', (event: Event) => {
  //     console.log("Error", event)
  //   })
  //
  //   return () => {
  //     eventSource.close();
  //   }
  // })

  return (
    <main className="flex min-h-screen flex-col items-center justify-between p-24">
      <p>Visualizer</p>
      <p>{bar}</p>
    </main>
  );
}
