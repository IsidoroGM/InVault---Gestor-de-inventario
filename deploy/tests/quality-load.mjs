import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { performance } from "node:perf_hooks";
import { api, login, movement, seedFixture, settings } from "./quality-api.mjs";

settings(); // Reject remote targets before authentication or any mutation.
assert.ok(process.env.INVAULT_QUALITY_REPORT);
const report = {
  workload: "bounded-loopback-smoke",
  revision: process.env.INVAULT_TEST_REVISION,
  readRequests: 400,
  uniqueMovements: 50,
  repeatsPerMovement: 4,
  readConcurrency: 10,
  writeConcurrency: 10,
  thresholds: { readP95Ms: 3000, writeP95Ms: 5000, totalMs: 120000, errors: 0 },
  passed: false,
};
const samples = { read: [], write: [] };
let errors = 0;
const started = performance.now();
const deadline = setTimeout(() => {
  console.error("Disposable load verification exceeded 120 seconds.");
  process.exit(1);
}, 120000);
deadline.unref();
async function pool(count, concurrency, action) {
  let next = 0;
  await Promise.all(
    Array.from({ length: concurrency }, async () => {
      while (next < count) {
        const index = next++;
        await action(index);
      }
    }),
  );
}
async function measured(kind, action) {
  const start = performance.now();
  try {
    return await action();
  } catch (error) {
    errors++;
    throw error;
  } finally {
    samples[kind].push(performance.now() - start);
  }
}
const p95 = (values) =>
  Math.round(
    [...values].sort((a, b) => a - b)[Math.ceil(values.length * 0.95) - 1] ?? 0,
  );
try {
  const token = (await login()).accessToken;
  const fixture = await seedFixture(token);
  for (let i = 0; i < 10; i++)
    await api("/api/stock/summary/products", { token });
  const keys = Array.from({ length: report.uniqueMovements }, () =>
    randomUUID(),
  );
  const savedResponses = new Map();
  await Promise.all([
    pool(report.readRequests, report.readConcurrency, () =>
      measured("read", () => api("/api/stock/summary/products", { token })),
    ),
    pool(
      report.uniqueMovements * report.repeatsPerMovement,
      report.writeConcurrency,
      (index) =>
        measured("write", async () => {
          const key = keys[index % keys.length];
          const result = await api("/api/stock/movements", {
            method: "POST",
            token,
            expected: 201,
            key,
            data: movement(fixture, 0.125),
          });
          if (savedResponses.has(key))
            assert.deepEqual(
              result,
              savedResponses.get(key),
              "Retry changed its persisted response",
            );
          else savedResponses.set(key, result);
        }),
    ),
  ]);
  const batch = await api("/api/batches/" + fixture.batch.id, { token });
  const history = await api(
    "/api/stock/movements/batch/" + fixture.batch.id + "?size=100",
    { token },
  );
  assert.equal(
    batch.quantity,
    6.25,
    "Duplicate submissions changed stock more than once",
  );
  assert.equal(history.totalElements, report.uniqueMovements);
  assert.equal(
    new Set(history.content.map((item) => item.id)).size,
    report.uniqueMovements,
  );
  report.finalQuantity = batch.quantity;
  report.confirmedMovements = history.totalElements;
  report.latencyMs = {
    readP95: p95(samples.read),
    writeP95: p95(samples.write),
  };
  report.elapsedMs = Math.round(performance.now() - started);
  report.errors = errors;
  assert.equal(errors, 0);
  assert.ok(
    report.latencyMs.readP95 <= report.thresholds.readP95Ms,
    "Read latency regression",
  );
  assert.ok(
    report.latencyMs.writeP95 <= report.thresholds.writeP95Ms,
    "Write latency regression",
  );
  assert.ok(
    report.elapsedMs <= report.thresholds.totalMs,
    "Workload duration regression",
  );
  report.passed = true;
} finally {
  clearTimeout(deadline);
  await mkdir(process.env.INVAULT_QUALITY_REPORT, { recursive: true });
  await writeFile(
    join(process.env.INVAULT_QUALITY_REPORT, "load.json"),
    JSON.stringify(report, null, 2),
  );
  console.log(JSON.stringify(report));
}
