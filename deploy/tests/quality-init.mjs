import assert from "node:assert/strict";
import { api, login, settings } from "./quality-api.mjs";

settings();
assert.ok(process.env.INVAULT_TEST_BOOTSTRAP_PASSWORD);
const session = await login(
  "admin",
  process.env.INVAULT_TEST_BOOTSTRAP_PASSWORD,
);
assert.equal(session.mustChangePassword, true);
await api("/api/users/me/password", {
  method: "PUT",
  token: session.accessToken,
  expected: 204,
  data: {
    currentPassword: process.env.INVAULT_TEST_BOOTSTRAP_PASSWORD,
    newPassword: settings().password,
  },
});
assert.equal((await login()).mustChangePassword, false);
console.log(
  "Disposable quality administrator initialized; no credentials recorded.",
);
