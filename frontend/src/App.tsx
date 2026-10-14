import { useEffect, useState } from "react";
import { api } from "./api";

export default function App() {
  const [status, setStatus] = useState("checking...");

  useEffect(() => {
    api
        .health()
        .then((r) => setStatus(r.status))
        .catch((e: Error) => setStatus("error: " + e.message));
  }, []);

  return <h1>FuroCash API: {status}</h1>;
}