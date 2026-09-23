import ExcelJS from "exceljs";
import { itemSubtotal, projectTotals, type CostItemKind, type Project, type PublicUser } from "../domain/types.js";
import type { ProjectRepository, UserRepository } from "../repositories/types.js";
import type { AccountService } from "./account.service.js";
import { AppError } from "../errors/AppError.js";

export const XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

/** Indonesian users read times in WIB; Excel would otherwise show raw UTC. */
export const DEFAULT_EXPORT_TIME_ZONE = "Asia/Jakarta";

export type ExportScope = "own" | "all";

export interface ExportFile {
  fileName: string;
  buffer: Buffer;
}

export interface BuildWorkbookInput {
  projects: Project[];
  /** Present only for the admin export: adds owner columns, keyed by user id. */
  owners?: Map<string, PublicUser>;
  exportedBy: PublicUser;
  scope: ExportScope;
  now: Date;
  timeZone: string;
}

// Sheet and column labels match the app's own wording.
export const SHEET_SUMMARY = "Ringkasan Project";
export const SHEET_ITEMS = "Rincian Biaya";
export const SHEET_INFO = "Info";

const KIND_LABEL: Record<CostItemKind, string> = {
  JASA: "Jasa",
  BARANG: "Barang",
  TRANSPORTASI: "Transportasi",
  LAIN_LAIN: "Lain-lain",
};

/** Whole rupiah; negatives (a project over budget) in red. */
export const MONEY_FORMAT = '"Rp"#,##0;[Red]-"Rp"#,##0';
const DATE_TIME_FORMAT = "dd mmm yyyy hh:mm";
const HEADER_FILL = "FF1565C0"; // the app's primary blue

interface Column {
  header: string;
  width: number;
  money?: boolean;
  date?: boolean;
}

/**
 * Builds the backup workbook. Pure: everything it needs is passed in, so the
 * tests parse its output directly.
 *
 * Values are written as static numbers, not formulas, because a backup should be
 * a snapshot. Text goes in as typed string cells, so a name like "=SUM(1)" stays
 * text and is never evaluated.
 */
export async function buildWorkbook(input: BuildWorkbookInput): Promise<ExportFile> {
  const { projects, owners, now, timeZone } = input;
  const withOwner = owners !== undefined;
  const ownerOf = (p: Project) => owners?.get(p.ownerId);
  const localTime = (d: Date) => toWallClock(d, timeZone);

  const workbook = new ExcelJS.Workbook();
  workbook.creator = "CostProject";
  workbook.created = now;

  // --- Ringkasan Project: one row per project -------------------------------
  const summary = addSheet(workbook, SHEET_SUMMARY, [
    ...(withOwner ? [{ header: "Pemilik", width: 22 }, { header: "Email Pemilik", width: 28 }] : []),
    { header: "Project", width: 28 },
    { header: "Customer", width: 24 },
    { header: "PIC", width: 18 },
    { header: "Harga Kontrak", width: 17, money: true },
    { header: "Total Jasa", width: 16, money: true },
    { header: "Total Barang", width: 16, money: true },
    { header: "Total Transportasi", width: 18, money: true },
    { header: "Total Lain-lain", width: 16, money: true },
    { header: "Total Biaya", width: 17, money: true },
    { header: "Sisa Kontrak", width: 17, money: true },
    { header: "Dibuat", width: 18, date: true },
    { header: "Diperbarui", width: 18, date: true },
  ]);

  for (const p of projects) {
    const t = projectTotals(p);
    // The app treats a contract of 0 as "no contract" and shows "-"; an empty
    // cell says the same thing without inventing a figure.
    const hasKontrak = p.hargaKontrak !== 0;
    const owner = ownerOf(p);
    summary.addRow([
      ...(withOwner ? [owner?.fullName ?? "(pengguna dihapus)", owner?.email ?? ""] : []),
      p.name,
      p.customer,
      p.pic,
      hasKontrak ? p.hargaKontrak : null,
      t.jasa,
      t.barang,
      t.transportasi,
      t.lainLain,
      t.total,
      hasKontrak ? t.sisaKontrak : null,
      localTime(p.createdAt),
      localTime(p.updatedAt),
    ]);
  }

  // --- Rincian Biaya: one row per line item ---------------------------------
  const items = addSheet(workbook, SHEET_ITEMS, [
    ...(withOwner ? [{ header: "Pemilik", width: 22 }] : []),
    { header: "Project", width: 28 },
    { header: "Kategori", width: 14 },
    { header: "No", width: 6 },
    { header: "Keterangan", width: 36 },
    { header: "Engineer", width: 20 },
    { header: "Qty", width: 8 },
    { header: "Harga", width: 16, money: true },
    { header: "Subtotal", width: 17, money: true },
  ]);

  let itemCount = 0;
  for (const p of projects) {
    const owner = ownerOf(p);
    // "No" restarts per category, matching the numbered cards in the app.
    const numberWithinKind = new Map<CostItemKind, number>();
    for (const item of p.items) {
      const no = (numberWithinKind.get(item.kind) ?? 0) + 1;
      numberWithinKind.set(item.kind, no);
      itemCount++;
      items.addRow([
        ...(withOwner ? [owner?.fullName ?? "(pengguna dihapus)"] : []),
        p.name,
        KIND_LABEL[item.kind],
        no,
        item.description,
        item.kind === "JASA" ? item.engineer || null : null,
        item.kind === "BARANG" ? item.quantity : null,
        item.amount,
        itemSubtotal(item),
      ]);
    }
  }

  // --- Info: what this file is ----------------------------------------------
  const info = workbook.addWorksheet(SHEET_INFO);
  info.columns = [{ width: 22 }, { width: 44 }];
  const ownerCount = withOwner ? new Set(projects.map((p) => p.ownerId)).size : 1;
  const infoRows: [string, string | number | Date][] = [
    ["Aplikasi", "CostProject"],
    ["Diekspor pada", localTime(now)],
    ["Diekspor oleh", `${input.exportedBy.fullName} <${input.exportedBy.email}>`],
    ["Cakupan", input.scope === "all" ? "Semua data (semua pengguna)" : "Project saya"],
    ["Jumlah pengguna", ownerCount],
    ["Jumlah project", projects.length],
    ["Jumlah rincian biaya", itemCount],
    ["Zona waktu", timeZone],
  ];
  for (const [label, value] of infoRows) {
    const row = info.addRow([label, value]);
    row.getCell(1).font = { bold: true };
    if (value instanceof Date) row.getCell(2).numFmt = DATE_TIME_FORMAT;
    row.getCell(2).alignment = { horizontal: "left" };
  }

  const buffer = Buffer.from(await workbook.xlsx.writeBuffer());
  return { fileName: exportFileName(input.scope, now, timeZone), buffer };
}

export function exportFileName(scope: ExportScope, now: Date, timeZone: string): string {
  const date = toWallClock(now, timeZone).toISOString().slice(0, 10);
  return scope === "all" ? `CostProject-Backup-SemuaData-${date}.xlsx` : `CostProject-Backup-${date}.xlsx`;
}

/**
 * Re-expresses an instant as the wall-clock time in [timeZone], encoded in a
 * Date's UTC fields.
 *
 * Excel has no timezones: exceljs writes a Date using its UTC components. So
 * 05:24 UTC would display as 05:24, when the user in Jakarta saw 12:24. Shifting
 * to wall-clock first makes the cell show 12:24.
 */
export function toWallClock(date: Date, timeZone: string): Date {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(date);
  const get = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find((p) => p.type === type)?.value);
  return new Date(Date.UTC(get("year"), get("month") - 1, get("day"), get("hour"), get("minute"), get("second")));
}

/** A sheet with a bold, frozen, filterable header row and typed column formats. */
function addSheet(workbook: ExcelJS.Workbook, name: string, columns: Column[]): ExcelJS.Worksheet {
  const sheet = workbook.addWorksheet(name, { views: [{ state: "frozen", ySplit: 1 }] });
  sheet.columns = columns.map((c) => ({
    header: c.header,
    width: c.width,
    ...(c.money ? { style: { numFmt: MONEY_FORMAT } } : {}),
    ...(c.date ? { style: { numFmt: DATE_TIME_FORMAT } } : {}),
  }));

  const header = sheet.getRow(1);
  header.font = { bold: true, color: { argb: "FFFFFFFF" } };
  header.fill = { type: "pattern", pattern: "solid", fgColor: { argb: HEADER_FILL } };
  header.alignment = { vertical: "middle" };
  // The column style would otherwise give header cells the money format too.
  header.eachCell((cell) => {
    cell.numFmt = "General";
  });

  sheet.autoFilter = { from: { row: 1, column: 1 }, to: { row: 1, column: columns.length } };
  return sheet;
}

/**
 * Export use cases: who may export what. The workbook itself is built by
 * [buildWorkbook].
 */
export class ExportService {
  constructor(
    private readonly projects: ProjectRepository,
    private readonly users: UserRepository,
    private readonly accounts: AccountService,
    private readonly now: () => Date = () => new Date(),
    private readonly timeZone: string = DEFAULT_EXPORT_TIME_ZONE,
  ) {}

  /** The caller's own projects. */
  async exportMine(userId: string): Promise<ExportFile> {
    const me = await this.accounts.profile(userId);
    return buildWorkbook({
      projects: await this.projects.listByOwner(userId),
      exportedBy: me,
      scope: "own",
      now: this.now(),
      timeZone: this.timeZone,
    });
  }

  /** Every user's projects, with owner columns. Admins only. */
  async exportAll(userId: string): Promise<ExportFile> {
    const me = await this.accounts.profile(userId);
    if (!me.isAdmin) throw AppError.forbidden("Only admins can export all users' data");

    const projects = await this.projects.listAll();
    const ownerIds = [...new Set(projects.map((p) => p.ownerId))];
    const owners = new Map(
      (await this.users.findManyByIds(ownerIds)).map((u) => [u.id, { id: u.id, email: u.email, fullName: u.fullName }]),
    );
    return buildWorkbook({
      projects,
      owners,
      exportedBy: me,
      scope: "all",
      now: this.now(),
      timeZone: this.timeZone,
    });
  }
}
