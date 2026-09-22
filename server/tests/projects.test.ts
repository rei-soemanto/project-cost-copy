import { describe, expect, it } from "vitest";
import { buildTestApp, registerUser, sampleProject } from "./helpers.js";

describe("project CRUD", () => {
  it("creates, reads, lists, updates and deletes a project", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    const body = sampleProject("proj-1");

    const created = await http.post("/api/v1/projects").set(auth).send(body).expect(201);
    expect(created.body.data).toMatchObject({ id: "proj-1", name: "Project IT System", hargaKontrak: 50_000_000 });
    expect(created.body.data).not.toHaveProperty("ownerId");
    expect(created.body.data.createdAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);

    await http.get("/api/v1/projects/proj-1").set(auth).expect(200);

    const list = await http.get("/api/v1/projects").set(auth).expect(200);
    expect(list.body.data.map((p: { id: string }) => p.id)).toEqual(["proj-1"]);

    const patched = await http
      .patch("/api/v1/projects/proj-1")
      .set(auth)
      .send({ name: "Renamed", hargaKontrak: 60_000_000 })
      .expect(200);
    // PATCH changes only the fields it names.
    expect(patched.body.data).toMatchObject({ name: "Renamed", hargaKontrak: 60_000_000, customer: "PT Maju Jaya" });

    await http.delete("/api/v1/projects/proj-1").set(auth).expect(204);
    await http.get("/api/v1/projects/proj-1").set(auth).expect(404);
  });

  it("generates an id when the client omits one", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    const res = await http.post("/api/v1/projects").set(auth).send({ name: "No id" }).expect(201);
    expect(res.body.data.id).toMatch(/^[0-9a-f]{24}$/);
    expect(res.body.data.items).toEqual([]);
  });

  it("rejects reusing a project id with 409 PROJECT_EXISTS", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    await http.post("/api/v1/projects").set(auth).send(sampleProject("same-id")).expect(201);
    const res = await http.post("/api/v1/projects").set(auth).send(sampleProject("same-id")).expect(409);
    expect(res.body.error.code).toBe("PROJECT_EXISTS");
  });
});

describe("ownership", () => {
  it("hides another user's project behind 404 on every endpoint - never 403", async () => {
    const { http } = buildTestApp();
    const alice = await registerUser(http);
    const bob = await registerUser(http);
    await http.post("/api/v1/projects").set(alice.auth).send(sampleProject("alices")).expect(201);

    // 404 rather than 403 means Bob cannot even learn the id exists.
    await http.get("/api/v1/projects/alices").set(bob.auth).expect(404);
    await http.patch("/api/v1/projects/alices").set(bob.auth).send({ name: "hijack" }).expect(404);
    await http.put("/api/v1/projects/alices/items").set(bob.auth).send({ items: [] }).expect(404);
    await http.delete("/api/v1/projects/alices").set(bob.auth).expect(404);

    const bobsList = await http.get("/api/v1/projects").set(bob.auth).expect(200);
    expect(bobsList.body.data).toEqual([]);

    // Alice's project survived all of Bob's attempts, unchanged.
    const alices = await http.get("/api/v1/projects/alices").set(alice.auth).expect(200);
    expect(alices.body.data.name).toBe("Project IT System");
    expect(alices.body.data.items).toHaveLength(4);
  });
});

describe("cost items", () => {
  it("replaces the whole item list and preserves order", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    await http.post("/api/v1/projects").set(auth).send(sampleProject("p")).expect(201);

    const items = [
      { id: "b", kind: "LAIN_LAIN", description: "second", amount: 2 },
      { id: "a", kind: "JASA", description: "first", engineer: "x", amount: 1 },
    ];
    const res = await http.put("/api/v1/projects/p/items").set(auth).send({ items }).expect(200);
    expect(res.body.data.items.map((i: { id: string }) => i.id)).toEqual(["b", "a"]);
  });

  it("canonicalises fields that do not apply to an item's kind", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    await http.post("/api/v1/projects").set(auth).send({ id: "p", name: "p" }).expect(201);

    const res = await http
      .put("/api/v1/projects/p/items")
      .set(auth)
      .send({
        items: [
          // JASA never has a quantity; BARANG never has an engineer.
          { id: "j", kind: "JASA", description: "d", quantity: 5, amount: 100 },
          { id: "b", kind: "BARANG", description: "d", engineer: "nobody", amount: 100 },
        ],
      })
      .expect(200);

    const [jasa, barang] = res.body.data.items;
    expect(jasa).toMatchObject({ kind: "JASA", quantity: null, engineer: "" });
    expect(barang).toMatchObject({ kind: "BARANG", engineer: null, quantity: 0 });
  });

  it("rejects fractional rupiah - money is whole integers", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    const res = await http
      .post("/api/v1/projects")
      .set(auth)
      .send({ name: "p", hargaKontrak: 1500.5 })
      .expect(400);
    expect(res.body.error.code).toBe("VALIDATION_ERROR");
  });

  it("rejects money sent as a formatted string", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    await http.post("/api/v1/projects").set(auth).send({ name: "p", hargaKontrak: "5.000.000" }).expect(400);
  });

  it("rejects negative amounts", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    await http
      .post("/api/v1/projects")
      .set(auth)
      .send({ name: "p", items: [{ id: "x", kind: "JASA", amount: -1 }] })
      .expect(400);
  });

  it("enforces the 20-TRANSPORTASI limit server-side", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    await http.post("/api/v1/projects").set(auth).send({ id: "p", name: "p" }).expect(201);

    const transport = (n: number) =>
      Array.from({ length: n }, (_, i) => ({ id: `t${i}`, kind: "TRANSPORTASI", description: "", amount: 1 }));

    await http.put("/api/v1/projects/p/items").set(auth).send({ items: transport(20) }).expect(200);
    const res = await http.put("/api/v1/projects/p/items").set(auth).send({ items: transport(21) }).expect(400);
    expect(res.body.error.details[0].message).toContain("20 TRANSPORTASI");
  });

  it("rejects duplicate item ids within one project", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    await http.post("/api/v1/projects").set(auth).send({ id: "p", name: "p" }).expect(201);
    await http
      .put("/api/v1/projects/p/items")
      .set(auth)
      .send({
        items: [
          { id: "dup", kind: "JASA", amount: 1 },
          { id: "dup", kind: "JASA", amount: 2 },
        ],
      })
      .expect(400);
  });
});

describe("validation and error envelope", () => {
  it("requires a project name", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    const res = await http.post("/api/v1/projects").set(auth).send({ name: "   " }).expect(400);
    expect(res.body.error.details[0].path).toBe("name");
  });

  it("rejects ids with unsafe characters", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    await http.post("/api/v1/projects").set(auth).send({ id: "../etc", name: "p" }).expect(400);
  });

  it("answers malformed JSON with 400 in the error envelope, not an HTML page", async () => {
    const { http } = buildTestApp();
    const { auth } = await registerUser(http);
    const res = await http
      .post("/api/v1/projects")
      .set(auth)
      .set("Content-Type", "application/json")
      .send("{ not json")
      .expect(400);
    expect(res.body.error.code).toBe("VALIDATION_ERROR");
  });

  it("answers unknown routes with 404 NOT_FOUND", async () => {
    const { http } = buildTestApp();
    const res = await http.get("/api/v1/nope").expect(404);
    expect(res.body.error.code).toBe("NOT_FOUND");
  });

  it("serves a health check without auth", async () => {
    const { http } = buildTestApp();
    const res = await http.get("/health").expect(200);
    expect(res.body.data.status).toBe("ok");
  });
});
