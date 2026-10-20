import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  test: {
    pool: "vmThreads",
    maxWorkers: 1,
    fileParallelism: false,
    environment: "jsdom",
    include: ["tests/*.test.tsx"],
    testTimeout: 15000,
  },
});
