import ExcelJS from "exceljs";
import type { Test } from "supertest";
import { describe, expect, it } from "vitest";
import { projectTotals, type CostItem, type Project } from "../src/domain/types.js";
import {
  MONEY_FORMAT,
  SHEET_INFO,
  SHEET_ITEMS,
  SHEET_SUMMARY,
  XLSX_MIME,
  buildWorkbook,
  exportFileName,
  toWallClock,
} from "../src/services/export.service.js";
import { buildTestApp, registerUser, sampleProject } from "./helpers.js";

// --- helpers ------------------------------------------------------------------

async function load(buffer: Buffer): Promise<ExcelJS.Workbook> {
  const wb = new ExcelJS.Workbook();
  await wb.xlsx.load(buffer as unknown as ArrayBuffer);
  return wb;
}

function sheet(wb: ExcelJS.Workbook, name: string): ExcelJS.Worksheet {
  const ws = wb.getWorksheet(name);
  if (!ws) throw new Error(`missing sheet ${name}`);
  return ws;
}

/** Header labels of a sheet's first row. */
function headers(ws: ExcelJS.Worksheet): string[] {
  const out: string[] = [];
  ws.getRow(1).eachCell({ includeEmpty: false }, (cell) => out.push(String(cell.value)));
  return out;
}

/** The cell in [row] under the column headed [header]. */
function cellAt(ws: ExcelJS.Worksheet, row: number, header: string): ExcelJS.Cell {
  const col = headers(ws).indexOf(header);
  if (col < 0) throw new Error(`no column "${header}" in ${ws.name}: ${headers(ws).join(", ")}`);
  return ws.getRow(row).getCell(col + 1);
}

/** Collects a binary response body; supertest only buffers text by default. */
function binary(req: Test): Test {
  return req.buffer(true).parse((res, done) => {
    const chunks: Buffer[] = [];
    res.on("data", (chunk: Buffer) => chunks.push(chunk));
    res.on("end", () => done(null, Buffer.concat(chunks)));
  });
}

const item = (over: Partial<CostItem> & Pick<CostItem, "id" | "kind" | "amount">): CostItem => ({
  description: "",
  engineer: null,
  quantity: null,
  ...over,
});

const project = (over: Partial<Project> = {}): Project => ({
  id: "p1",
  ownerId: "u1",
  name: "Gedung A",
  customer: "PT Maju",
  pic: "Budi",
  hargaKontrak: 50_000_000,
  items: [
    item({ id: "j1", kind: "JASA", description: "Rancang bangun", engineer: "Andi", amount: 5_000_000 }),
    item({ id: "j2", kind: "JASA", description: "Instalasi", engineer: "", amount: 1_000_000 }),
    item({ id: "b1", kind: "BARANG", description: "Kabel", quantity: 10, amount: 25_000 }),
    item({ id: "t1", kind: "TRANSPORTASI", description: "Sewa mobil", amount: 300_000 }),
    item({ id: "l1", kind: "LAIN_LAIN", description: "Konsumsi", amount: 150_000 }),
  ],
  createdAt: new Date("2026-09-22T19:00:00Z"), // 02:00 on the 23rd in Jakarta
  updatedAt: new Date("2026-09-22T19:30:00Z"),
  ...over,
});

const me = { id: "u1", email: "rei@example.com", fullName: "Rei" };
const NOW = new Date("2026-09-22T19:00:00Z");

const build = (projects: Project[], extra: Partial<Parameters<typeof buildWorkbook>[0]> = {}) =>
  buildWorkbook({ projects, exportedBy: me, scope: "own", now: NOW, timeZone: "Asia/Jakarta", ...extra });

// --- totals -------------------------------------------------------------------

describe("projectTotals", () => {
  it("matches the app's formula: Barang is qty x unit price, the rest are their amount", () => {
    const t = projectTotals(project());
    expect(t).toEqual({
      jasa: 6_000_000,
      barang: 250_000, // 10 x 25.000
      transportasi: 300_000,
      lainLain: 150_000,
      total: 6_700_000,
      sisaKontrak: 43_300_000,
    });
  });

  it("treats a Barang with no quantity as zero, and goes negative when over budget", () => {
    const t = projectTotals({
      hargaKontrak: 100,
      items: [item({ id: "b", kind: "BARANG", quantity: null, amount: 999 }), item({ id: "j", kind: "JASA", amount: 150 })],
    });
    expect(t.barang).toBe(0);
    expect(t.sisaKontrak).toBe(-50);
  });
});

// --- workbook -----------------------------------------------------------------

describe("buildWorkbook", () => {
  it("has the summary, item and info sheets in that order", async () => {
    const wb = await load((await build([project()])).buffer);
    expect(wb.worksheets.map((w) => w.name)).toEqual([SHEET_SUMMARY, SHEET_ITEMS, SHEET_INFO]);
  });

  it("writes totals as real numbers in rupiah format, matching projectTotals", async () => {
    const ws = sheet(await load((await build([project()])).buffer), SHEET_SUMMARY);
    const expected = projectTotals(project());

    const kontrak = cellAt(ws, 2, "Harga Kontrak");
    expect(kontrak.value).toBe(50_000_000);
    expect(kontrak.numFmt).toBe(MONEY_FORMAT);

    expect(cellAt(ws, 2, "Total Jasa").value).toBe(expected.jasa);
    expect(cellAt(ws, 2, "Total Barang").value).toBe(expected.barang);
    expect(cellAt(ws, 2, "Total Transportasi").value).toBe(expected.transportasi);
    expect(cellAt(ws, 2, "Total Lain-lain").value).toBe(expected.lainLain);
    expect(cellAt(ws, 2, "Total Biaya").value).toBe(expected.total);
    expect(cellAt(ws, 2, "Sisa Kontrak").value).toBe(expected.sisaKontrak);
    // The money column's style must not leak onto its header label.
    expect(cellAt(ws, 1, "Harga Kontrak").numFmt).not.toBe(MONEY_FORMAT);
  });

  it("leaves Harga Kontrak and Sisa Kontrak empty when the project has no contract", async () => {
    const ws = sheet(await load((await build([project({ hargaKontrak: 0 })])).buffer), SHEET_SUMMARY);
    expect(cellAt(ws, 2, "Harga Kontrak").value).toBeNull();
    expect(cellAt(ws, 2, "Sisa Kontrak").value).toBeNull();
    expect(cellAt(ws, 2, "Total Biaya").value).toBe(6_700_000);
  });

  it("lists every line item, numbered within its category, with Barang as qty x price", async () => {
    const ws = sheet(await load((await build([project()])).buffer), SHEET_ITEMS);
    expect(headers(ws)).toEqual(["Project", "Kategori", "No", "Keterangan", "Engineer", "Qty", "Harga", "Subtotal"]);
    expect(ws.rowCount).toBe(1 + 5);

    const rows = [2, 3, 4, 5, 6].map((r) => ({
      kategori: cellAt(ws, r, "Kategori").value,
      no: cellAt(ws, r, "No").value,
      engineer: cellAt(ws, r, "Engineer").value,
      qty: cellAt(ws, r, "Qty").value,
      subtotal: cellAt(ws, r, "Subtotal").value,
    }));
    expect(rows).toEqual([
      { kategori: "Jasa", no: 1, engineer: "Andi", qty: null, subtotal: 5_000_000 },
      { kategori: "Jasa", no: 2, engineer: null, qty: null, subtotal: 1_000_000 },
      { kategori: "Barang", no: 1, engineer: null, qty: 10, subtotal: 250_000 },
      { kategori: "Transportasi", no: 1, engineer: null, qty: null, subtotal: 300_000 },
      { kategori: "Lain-lain", no: 1, engineer: null, qty: null, subtotal: 150_000 },
    ]);
    expect(cellAt(ws, 4, "Subtotal").numFmt).toBe(MONEY_FORMAT);
  });

  it("keeps text that looks like a formula as plain text", async () => {
    const hostile = project({ name: "=SUM(1)+HYPERLINK(\"http://x\")", customer: "@cmd", pic: "+1" });
    const ws = sheet(await load((await build([hostile])).buffer), SHEET_SUMMARY);
    const name = cellAt(ws, 2, "Project");
    expect(name.type).toBe(ExcelJS.ValueType.String);
    expect(name.value).toBe("=SUM(1)+HYPERLINK(\"http://x\")");
    expect(cellAt(ws, 2, "Customer").type).toBe(ExcelJS.ValueType.String);
  });

  it("shows dates as Jakarta wall-clock time, not UTC", async () => {
    const ws = sheet(await load((await build([project()])).buffer), SHEET_SUMMARY);
    const created = cellAt(ws, 2, "Dibuat").value as Date;
    // 19:00 UTC on the 22nd is 02:00 on the 23rd in Jakarta (UTC+7).
    expect(created.toISOString()).toBe("2026-09-23T02:00:00.000Z");
  });

  it("names the file by the Jakarta date and scope", () => {
    expect(exportFileName("own", NOW, "Asia/Jakarta")).toBe("CostProject-Backup-2026-09-23.xlsx");
    expect(exportFileName("all", NOW, "Asia/Jakarta")).toBe("CostProject-Backup-SemuaData-2026-09-23.xlsx");
    expect(exportFileName("own", NOW, "UTC")).toBe("CostProject-Backup-2026-09-22.xlsx");
    expect(toWallClock(NOW, "UTC").toISOString()).toBe(NOW.toISOString());
  });

  it("adds owner columns only to the admin export", async () => {
    const owners = new Map([["u1", { id: "u1", email: "rei@example.com", fullName: "Rei" }]]);
    const own = await load((await build([project()])).buffer);
    const all = await load((await build([project()], { owners, scope: "all" })).buffer);

    expect(headers(sheet(own, SHEET_SUMMARY))).not.toContain("Pemilik");
    expect(headers(sheet(all, SHEET_SUMMARY)).slice(0, 3)).toEqual(["Pemilik", "Email Pemilik", "Project"]);
    expect(cellAt(sheet(all, SHEET_SUMMARY), 2, "Email Pemilik").value).toBe("rei@example.com");
    expect(cellAt(sheet(all, SHEET_ITEMS), 2, "Pemilik").value).toBe("Rei");
  });

  it("records scope and counts on the info sheet", async () => {
    const wb = await load((await build([project(), project({ id: "p2", items: [] })])).buffer);
    const info = new Map<string, unknown>();
    sheet(wb, SHEET_INFO).eachRow((row) => info.set(String(row.getCell(1).value), row.getCell(2).value));
    expect(info.get("Cakupan")).toBe("Project saya");
    expect(info.get("Diekspor oleh")).toBe("Rei <rei@example.com>");
    expect(info.get("Jumlah project")).toBe(2);
    expect(info.get("Jumlah rincian biaya")).toBe(5);
  });

  it("still produces a valid workbook with headers when there are no projects", async () => {
    const wb = await load((await build([])).buffer);
    expect(sheet(wb, SHEET_SUMMARY).rowCount).toBe(1);
    expect(headers(sheet(wb, SHEET_SUMMARY))).toContain("Total Biaya");
  });
});

// --- endpoints ----------------------------------------------------------------

describe("GET /api/v1/me", () => {
  it("requires a token", async () => {
    const { http } = buildTestApp();
    await http.get("/api/v1/me").expect(401);
  });

  it("reports isAdmin from ADMIN_EMAILS, case-insensitively", async () => {
    const { http } = buildTestApp({ adminEmails: ["Boss@Example.com"] });
    const boss = await registerUser(http, { email: "boss@example.com" });
    const staff = await registerUser(http);

    const bossRes = await http.get("/api/v1/me").set(boss.auth).expect(200);
    expect(bossRes.body.data).toMatchObject({ email: "boss@example.com", isAdmin: true });
    expect(bossRes.body.data).not.toHaveProperty("passwordHash");

    const staffRes = await http.get("/api/v1/me").set(staff.auth).expect(200);
    expect(staffRes.body.data.isAdmin).toBe(false);
  });
});

describe("GET /api/v1/export/projects.xlsx", () => {
  it("requires a token", async () => {
    const { http } = buildTestApp();
    await http.get("/api/v1/export/projects.xlsx").expect(401);
  });

  it("downloads the caller's projects only, as an uncacheable xlsx attachment", async () => {
    const { http } = buildTestApp();
    const alice = await registerUser(http);
    const bob = await registerUser(http);
    await http.post("/api/v1/projects").set(alice.auth).send({ ...sampleProject("a1"), name: "Punya Alice" }).expect(201);
    await http.post("/api/v1/projects").set(bob.auth).send({ ...sampleProject("b1"), name: "Punya Bob" }).expect(201);

    const res = await binary(http.get("/api/v1/export/projects.xlsx").set(alice.auth)).expect(200);

    expect(res.headers["content-type"]).toBe(XLSX_MIME);
    expect(res.headers["content-disposition"]).toMatch(/^attachment; filename="CostProject-Backup-\d{4}-\d{2}-\d{2}\.xlsx"$/);
    expect(res.headers["cache-control"]).toBe("no-store");

    const ws = sheet(await load(res.body as Buffer), SHEET_SUMMARY);
    const names: unknown[] = [];
    ws.eachRow((row, n) => n > 1 && names.push(cellAt(ws, n, "Project").value));
    expect(names).toEqual(["Punya Alice"]);
  });
});

describe("GET /api/v1/export/all.xlsx", () => {
  it("is forbidden for non-admins, with the JSON error envelope", async () => {
    const { http } = buildTestApp({ adminEmails: ["boss@example.com"] });
    const staff = await registerUser(http);
    const res = await http.get("/api/v1/export/all.xlsx").set(staff.auth).expect(403);
    expect(res.body.error.code).toBe("FORBIDDEN");
  });

  it("gives admins every user's projects with their owners", async () => {
    const { http } = buildTestApp({ adminEmails: ["boss@example.com"] });
    const boss = await registerUser(http, { email: "boss@example.com" });
    const staff = await registerUser(http, { email: "staff@example.com" });
    await http.post("/api/v1/projects").set(staff.auth).send({ ...sampleProject("s1"), name: "Punya Staff" }).expect(201);
    await http.post("/api/v1/projects").set(boss.auth).send({ ...sampleProject("x1"), name: "Punya Boss" }).expect(201);

    const res = await binary(http.get("/api/v1/export/all.xlsx").set(boss.auth)).expect(200);
    expect(res.headers["content-disposition"]).toContain("CostProject-Backup-SemuaData-");

    const ws = sheet(await load(res.body as Buffer), SHEET_SUMMARY);
    const rows: { owner: unknown; project: unknown }[] = [];
    ws.eachRow((_row, n) => {
      if (n > 1) rows.push({ owner: cellAt(ws, n, "Email Pemilik").value, project: cellAt(ws, n, "Project").value });
    });
    expect(rows).toEqual(
      expect.arrayContaining([
        { owner: "staff@example.com", project: "Punya Staff" },
        { owner: "boss@example.com", project: "Punya Boss" },
      ]),
    );
    expect(rows).toHaveLength(2);
  });
});
