import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { createHash } from "node:crypto";

export function assess(report, kind) {
  assert.ok(["backend", "frontend"].includes(kind));
  assert.equal(report.SchemaVersion, 2, "Unsupported security report");
  assert.ok(Array.isArray(report.Results), "Missing scan results");
  assert.ok(
    report.Results.some((r) => r.Class === "os-pkgs" && r.Packages?.length > 0),
    "OS inventory was not analyzed",
  );
  if (kind === "backend")
    assert.ok(
      report.Results.some((r) => r.Type === "jar" && r.Packages?.length > 0),
      "Packaged Java dependencies were not analyzed",
    );
  const findings = report.Results.flatMap((r) => r.Vulnerabilities ?? []);
  const blocking = findings.filter(
    (v) =>
      ["HIGH", "CRITICAL"].includes(v.Severity) &&
      String(v.FixedVersion ?? "").trim(),
  );
  return {
    image: kind,
    findings: findings.length,
    blocking: blocking.map((v) => ({
      id: v.VulnerabilityID,
      package: v.PkgName,
      installed: v.InstalledVersion,
      fixed: v.FixedVersion,
      severity: v.Severity,
    })),
    unfixedHighOrCritical: findings.filter(
      (v) =>
        ["HIGH", "CRITICAL"].includes(v.Severity) &&
        !String(v.FixedVersion ?? "").trim(),
    ).length,
  };
}
export function assessSarif(report, reviews = [], fingerprints = {}) {
  assert.equal(report.version, "2.1.0", "Unsupported SAST report");
  assert.ok(report.runs?.length > 0, "No SAST analysis was recorded");
  const findings = report.runs.flatMap((run) =>
    (run.results ?? []).map((result) => {
      const rules = [run.tool.driver, ...(run.tool.extensions ?? [])].flatMap(
        (component) => component.rules ?? [],
      );
      const ruleId = result.ruleId ?? result.rule?.id;
      const rule = rules.find((candidate) => candidate.id === ruleId);
      assert.ok(rule, "A SAST result has no rule metadata");
      const score = Number(rule.properties?.["security-severity"] ?? 0);
      assert.ok(Number.isFinite(score), "Invalid security severity");
      return {
        rule: ruleId,
        score,
        file: result.locations?.[0]?.physicalLocation?.artifactLocation?.uri,
        line: result.locations?.[0]?.physicalLocation?.region?.startLine,
      };
    }),
  );
  const reviewed = [];
  const blocking = findings
    .filter((finding) => finding.score >= 7)
    .filter((finding) => {
      const review = reviews.find(
        (entry) =>
          entry.rule === finding.rule &&
          entry.file === finding.file &&
          typeof entry.reason === "string" &&
          entry.reason.length >= 30 &&
          /^[a-f0-9]{64}$/.test(entry.sha256) &&
          fingerprints[entry.file] === entry.sha256,
      );
      if (!review) return true;
      reviewed.push({
        ...finding,
        reason: review.reason,
        sourceSha256: review.sha256,
      });
      return false;
    });
  return {
    findings: findings.length,
    blocking,
    reviewed,
    reported: findings,
  };
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  const result =
    process.argv[3] === "sarif"
      ? (() => {
          const files = readdirSync(process.argv[2]).filter((file) =>
            file.endsWith(".sarif"),
          );
          assert.ok(files.length > 0, "No SAST report was produced");
          const reviews = JSON.parse(
            readFileSync(
              new URL("../codeql-reviewed-findings.json", import.meta.url),
              "utf8",
            ),
          ).findings;
          const fingerprints = Object.fromEntries(
            reviews.map((entry) => {
              assert.ok(
                entry.file.startsWith("src/") &&
                  !entry.file.split("/").includes(".."),
                "Invalid reviewed source path",
              );
              const source = readFileSync(
                new URL("../../" + entry.file, import.meta.url),
                "utf8",
              ).replaceAll("\r\n", "\n");
              return [
                entry.file,
                createHash("sha256").update(source).digest("hex"),
              ];
            }),
          );
          const reports = files.map((file) =>
            assessSarif(
              JSON.parse(readFileSync(join(process.argv[2], file), "utf8")),
              reviews,
              fingerprints,
            ),
          );
          return {
            reports: reports.length,
            findings: reports.reduce((sum, report) => sum + report.findings, 0),
            blocking: reports.flatMap((report) => report.blocking),
            reviewed: reports.flatMap((report) => report.reviewed),
            reported: reports.flatMap((report) => report.reported),
          };
        })()
      : assess(
          JSON.parse(readFileSync(process.argv[2], "utf8")),
          process.argv[3],
        );
  console.log(JSON.stringify(result, null, 2));
  if (result.blocking.length) process.exitCode = 1;
}
