import ForceGraph from "@/app/graph/forcegraph";
import {data} from "@/app/graph/data";

export default function Page() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-between p-24">
      <ForceGraph nodes={data.nodes} links={data.links}></ForceGraph>
    </main>
  );
}
