import { afterEach, expect, test, vi } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import NetworkActivity from "../src/components/NetworkActivity";
import { api } from "../src/api";

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

test("slow connection guidance appears after eight seconds and clears on completion", async () => {
  vi.useFakeTimers();
  let finish!: (response: Response) => void;
  vi.stubGlobal(
    "fetch",
    () =>
      new Promise<Response>((resolve) => {
        finish = resolve;
      }),
  );
  render(<NetworkActivity />);
  let pending!: ReturnType<typeof api.health>;
  act(() => {
    pending = api.health();
  });
  try {
    expect(
      screen.getByRole("progressbar", { name: "Loading FuroCash" }),
    ).toBeTruthy();
    expect(screen.queryByText("Still connecting")).toBeNull();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(8000);
    });
    expect(screen.getByText("Still connecting")).toBeTruthy();
  } finally {
    await act(async () => {
      finish(new Response('{"status":"UP"}', { status: 200 }));
      await pending;
    });
  }
  expect(screen.queryByRole("progressbar")).toBeNull();
  expect(screen.queryByText("Still connecting")).toBeNull();
});
