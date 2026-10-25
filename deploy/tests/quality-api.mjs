import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";

export function settings() {
  const url = new URL(process.env.INVAULT_TEST_URL);
  assert.equal(url.protocol, "http:");
  assert.ok(["localhost", "127.0.0.1", "[::1]"].includes(url.hostname));
  assert.ok(
    url.port &&
      url.pathname === "/" &&
      !url.username &&
      !url.password &&
      !url.search &&
      !url.hash,
  );
  assert.match(process.env.INVAULT_TEST_RUN_ID ?? "", /^[a-f0-9]{10}$/);
  assert.equal(process.env.INVAULT_TEST_MUTATIONS, "owned-disposable-stack");
  assert.ok(process.env.INVAULT_TEST_PASSWORD);
  return {
    url: url.origin,
    runId: process.env.INVAULT_TEST_RUN_ID,
    password: process.env.INVAULT_TEST_PASSWORD,
  };
}

export async function api(
  path,
  { method = "GET", data, token, expected = 200, key } = {},
) {
  assert.ok(path.startsWith("/api/"));
  const response = await fetch(settings().url + path, {
    method,
    redirect: "error",
    signal: AbortSignal.timeout(15000),
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: "Bearer " + token } : {}),
      ...(key ? { "Idempotency-Key": key } : {}),
    },
    ...(data === undefined ? {} : { body: JSON.stringify(data) }),
  });
  assert.equal(
    response.status,
    expected,
    method + " " + path + ": unexpected HTTP status",
  );
  const body = await response.text();
  return body ? JSON.parse(body) : null;
}

export async function login(
  username = "admin",
  password = settings().password,
) {
  return api("/api/auth/login", {
    method: "POST",
    data: { username, password },
  });
}

export async function createUser(token, role, { changePassword = true } = {}) {
  const username =
    "q" + settings().runId + randomUUID().replaceAll("-", "").slice(0, 8);
  const temporaryPassword = "Temporary-quality-" + randomUUID();
  const user = await api("/api/users", {
    method: "POST",
    token,
    expected: 201,
    data: {
      username,
      email: username + "@example.test",
      temporaryPassword,
      roles: [role],
    },
  });
  if (changePassword) {
    const session = await login(username, temporaryPassword);
    await api("/api/users/me/password", {
      method: "PUT",
      token: session.accessToken,
      expected: 204,
      data: {
        currentPassword: temporaryPassword,
        newPassword: settings().password,
      },
    });
  }
  return { ...user, username, temporaryPassword };
}

export async function seedFixture(token) {
  const suffix =
    settings().runId + randomUUID().replaceAll("-", "").slice(0, 6);
  const create = (path, data) =>
    api(path, { method: "POST", token, data, expected: 201 });
  const category = await create("/api/categories", {
    name: "Category " + suffix,
  });
  const location = await create("/api/locations", {
    name: "Location " + suffix,
  });
  const unit = await create("/api/units", {
    code: "Q" + suffix,
    name: "Unit " + suffix,
    symbol: "q",
  });
  const product = await create("/api/products", {
    sku: "Q-" + suffix,
    name: "Product " + suffix,
    categoryId: category.id,
    locationId: location.id,
    unitId: unit.id,
    minimumStock: 1,
  });
  const batch = await create("/api/batches", {
    productId: product.id,
    batchCode: "B-" + suffix,
    status: "AVAILABLE",
  });
  return { category, location, unit, product, batch };
}

export function movement(fixture, quantity, movementType = "INBOUND") {
  return {
    productId: fixture.product.id,
    batchId: fixture.batch.id,
    quantity,
    movementType,
    supplierId: null,
    reason: "Disposable phase 2 verification",
  };
}
