import type { Request, Response } from "express";
import { currentUserId } from "../middleware/auth.js";
import { XLSX_MIME, type ExportFile, type ExportService } from "../services/export.service.js";

export class ExportController {
  constructor(private readonly exports: ExportService) {}

  mine = async (req: Request, res: Response) => {
    sendFile(res, await this.exports.exportMine(currentUserId(req)));
  };

  all = async (req: Request, res: Response) => {
    sendFile(res, await this.exports.exportAll(currentUserId(req)));
  };
}

function sendFile(res: Response, file: ExportFile) {
  res.setHeader("Content-Type", XLSX_MIME);
  res.setHeader("Content-Disposition", `attachment; filename="${file.fileName}"`);
  res.setHeader("Content-Length", String(file.buffer.length));
  // A backup holds private data: never let Cloudflare or any proxy keep a copy.
  res.setHeader("Cache-Control", "no-store");
  res.send(file.buffer);
}
