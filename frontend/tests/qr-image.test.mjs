import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import vm from "node:vm";
import test from "node:test";
import QRCode from "qrcode";

// Run the installed scanner's real image decoder without a browser camera.
// The package wraps its worker in a Blob; capture that wrapper in an isolated VM.
async function scannerDecoder() {
  const require = createRequire(import.meta.url);
  const source = await readFile(
    join(dirname(require.resolve("qr-scanner")), "qr-scanner-worker.min.js"),
    "utf8",
  );
  let workerSource;
  const wrapper = {
    Blob: class {
      constructor(parts) {
        this.source = parts.join("");
      }
    },
    URL: {
      createObjectURL(blob) {
        workerSource = blob.source;
        return "local-test-worker";
      },
    },
    Worker: class {},
  };
  vm.runInNewContext(
    source.replace("export const createWorker=", "globalThis.createWorker="),
    wrapper,
  );
  wrapper.createWorker();
  let result;
  const self = {
    postMessage(message) {
      result = message;
    },
  };
  vm.runInNewContext(
    workerSource,
    { self, Uint8ClampedArray },
    { timeout: 5000 },
  );
  return (image) => {
    self.onmessage({ data: { id: 1, type: "decode", data: image } });
    return result.data;
  };
}

function renderQr(text) {
  const modules = QRCode.create(text, { errorCorrectionLevel: "M" }).modules;
  const scale = 4,
    margin = 4,
    width = (modules.size + margin * 2) * scale;
  const data = new Uint8ClampedArray(width * width * 4).fill(255);
  for (let y = 0; y < modules.size; y++)
    for (let x = 0; x < modules.size; x++) {
      if (!modules.get(y, x)) continue;
      for (let dy = 0; dy < scale; dy++)
        for (let dx = 0; dx < scale; dx++) {
          const offset =
            (((y + margin) * scale + dy) * width + (x + margin) * scale + dx) *
            4;
          data[offset] = data[offset + 1] = data[offset + 2] = 0;
        }
    }
  return { data, width, height: width };
}

test("generated FuroCash QR pixels decode with the installed scanner engine", async () => {
  const decode = await scannerDecoder();
  for (const purpose of ["TRANSFER", "DEPOSIT"]) {
    const payload = {
      app: "FUROCASH",
      version: 1,
      purpose,
      accountIdentifier: "FC-0000000001",
      reference: "12345678-1234-4234-8234-123456789abc",
      amount: purpose === "TRANSFER" ? "25.50" : null,
    };
    assert.deepEqual(
      JSON.parse(decode(renderQr(JSON.stringify(payload)))),
      payload,
    );
  }
  const width = 160;
  assert.equal(
    decode({
      data: new Uint8ClampedArray(width * width * 4).fill(255),
      width,
      height: width,
    }),
    null,
  );
});
