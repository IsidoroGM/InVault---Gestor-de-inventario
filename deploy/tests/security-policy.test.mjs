import assert from "node:assert/strict";
import test from "node:test";
import { assess, assessSarif } from "./security-policy.mjs";
const os = { Class: "os-pkgs", Packages: [{ Name: "sample" }] };
const jar = { Type: "jar", Packages: [{ Name: "sample-java" }] };
test("a successful empty scanner cannot approve an image", () => {
  assert.throws(() => assess({ SchemaVersion: 2, Results: [] }, "frontend"));
});
test("a backend must include Java inventory", () => {
  assert.throws(() => assess({ SchemaVersion: 2, Results: [os] }, "backend"));
});
test("unknown report schemas fail closed", () => {
  assert.throws(() =>
    assess({ SchemaVersion: 3, Results: [os, jar] }, "backend"),
  );
});
test("clean reports retain complete inventory evidence", () => {
  assert.equal(
    assess({ SchemaVersion: 2, Results: [os, jar] }, "backend").blocking.length,
    0,
  );
});
test("fixed high and critical vulnerabilities block", () => {
  const result = assess(
    {
      SchemaVersion: 2,
      Results: [
        {
          ...os,
          Vulnerabilities: [
            { Severity: "HIGH", FixedVersion: "2" },
            { Severity: "CRITICAL", FixedVersion: "3" },
          ],
        },
      ],
    },
    "frontend",
  );
  assert.equal(result.blocking.length, 2);
});
test("unfixed findings remain visible, without a fabricated remediation", () => {
  const result = assess(
    {
      SchemaVersion: 2,
      Results: [
        {
          ...os,
          Vulnerabilities: [
            { Severity: "CRITICAL", FixedVersion: "" },
            { Severity: "LOW", FixedVersion: "2" },
          ],
        },
      ],
    },
    "frontend",
  );
  assert.equal(result.findings, 2);
  assert.equal(result.unfixedHighOrCritical, 1);
  assert.equal(result.blocking.length, 0);
});
test("missing SAST analysis cannot approve source", () => {
  assert.throws(() => assessSarif({ version: "2.1.0", runs: [] }));
});
test("high SAST scores block while retaining lower scores", () => {
  const result = assessSarif({
    version: "2.1.0",
    runs: [
      {
        tool: {
          driver: {
            rules: [
              { id: "high", properties: { "security-severity": "8.1" } },
              { id: "medium", properties: { "security-severity": "6.5" } },
            ],
          },
        },
        results: [{ ruleId: "high" }, { ruleId: "medium" }],
      },
    ],
  });
  assert.equal(result.findings, 2);
  assert.equal(result.blocking.length, 1);
});
test("unknown SAST rules cannot evade the severity gate", () => {
  assert.throws(() =>
    assessSarif({
      version: "2.1.0",
      runs: [
        {
          tool: { driver: { rules: [] } },
          results: [{ ruleId: "unknown" }],
        },
      ],
    }),
  );
});
test("CodeQL query-pack extension metadata preserves security scores", () => {
  const result = assessSarif({
    version: "2.1.0",
    runs: [
      {
        tool: {
          driver: { rules: [] },
          extensions: [
            {
              name: "codeql/javascript-queries",
              rules: [
                {
                  id: "extension/high",
                  properties: { "security-severity": "9.1" },
                },
              ],
            },
          ],
        },
        results: [
          { ruleId: "extension/high", rule: { toolComponent: { index: 0 } } },
        ],
      },
    ],
  });
  assert.equal(result.blocking.length, 1);
});
