import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { api, tokenStore, ApiError } from "../src/api.ts";

const storage = new Map();
globalThis.localStorage = {
  getItem: (k) => storage.get(k) ?? null,
  setItem: (k, v) => storage.set(k, v),
  removeItem: (k) => storage.delete(k),
};
let calls;
beforeEach(() => {
  storage.clear();
  calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return new Response(JSON.stringify({ message: "OK" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };
});

test("registration sends its verification code and does not require a second login", async () => {
  await api.register("09171234567", "1234", "Test User", "654321");
  assert.deepEqual(JSON.parse(calls[0].body), {
    mobile: "09171234567",
    pin: "1234",
    fullName: "Test User",
    otp: "654321",
  });
});
test("request registration code uses the request-code endpoint", async () => {
  await api.requestRegistrationCode("09171234567", "1234", "Test User");
  assert.ok(calls[0].url.endsWith("/api/auth/register/request-code"));
});
test("recovery sends the new PIN and OTP to reset-pin", async () => {
  await api.resetPin("09171234567", "654321", "5678");
  assert.deepEqual(JSON.parse(calls[0].body), {
    mobile: "09171234567",
    otp: "654321",
    pin: "5678",
  });
});
test("current user endpoint receives bearer token", async () => {
  tokenStore.set("test-token");
  await api.me();
  assert.ok(calls[0].url.endsWith("/api/auth/me"));
  assert.equal(calls[0].headers.Authorization, "Bearer test-token");
});
test("401 clears stale session and signals expiry", async () => {
  let expired = 0;
  globalThis.window = {
    dispatchEvent: () => {
      expired++;
    },
  };
  tokenStore.set("expired");
  globalThis.fetch = async () =>
    new Response('{"error":"Session expired"}', { status: 401 });
  await assert.rejects(
    api.balance(),
    (e) => e instanceof ApiError && e.status === 401,
  );
  assert.equal(tokenStore.get(), null);
  assert.equal(expired, 1);
});
test("non-JSON server errors become actionable API errors", async () => {
  globalThis.fetch = async () =>
    new Response("<html>Bad gateway</html>", { status: 502 });
  await assert.rejects(
    api.balance(),
    (e) =>
      e instanceof ApiError &&
      e.status === 502 &&
      !e.message.includes("<html>"),
  );
});
test("cash-in preserves an exact decimal amount", async () => {
  await api.cashIn("100.50");
  assert.deepEqual(JSON.parse(calls[0].body), { amount: "100.50" });
});

test("a lost response body reports an indeterminate result", async () => {
  globalThis.fetch = async () => ({
    ok: true,
    status: 200,
    text: async () => {
      throw new TypeError("connection terminated");
    },
  });
  await assert.rejects(
    api.cashIn("25.50"),
    (e) => e instanceof ApiError && e.outcomeUnknown === true,
  );
});
test("malformed successful JSON reports an indeterminate result", async () => {
  globalThis.fetch = async () => new Response("<invalid>", { status: 200 });
  await assert.rejects(
    api.transfer("09171234567", "25.50"),
    (e) => e instanceof ApiError && e.outcomeUnknown === true,
  );
});
test("a delayed old-session 401 does not erase a newer login", async () => {
  let deliver;
  let expired = 0;
  globalThis.window = {
    dispatchEvent: () => {
      expired++;
    },
  };
  tokenStore.set("old-token");
  globalThis.fetch = () =>
    new Promise((resolve) => {
      deliver = resolve;
    });
  const pending = api.balance();
  tokenStore.set("new-token");
  deliver(new Response('{"error":"Expired"}', { status: 401 }));
  await assert.rejects(
    pending,
    (e) => e instanceof ApiError && e.status === 401,
  );
  assert.equal(tokenStore.get(), "new-token");
  assert.equal(expired, 0);
});

test("money operations preserve caller supplied idempotency keys", async () => {
  await api.prepareDeposit("25.50", "same-operation");
  await api.prepareDeposit("25.50", "same-operation");
  assert.equal(
    new Headers(calls[0].headers).get("Idempotency-Key"),
    "same-operation",
  );
  assert.equal(
    new Headers(calls[1].headers).get("Idempotency-Key"),
    "same-operation",
  );
});
