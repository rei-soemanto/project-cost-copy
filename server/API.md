# CostProject API — v1

The contract between the Express server (`server/`) and the Kotlin client
(`composeApp/`). Both sides are built against this document; change it first.

Base path: `/api/v1`

## Conventions

- **JSON only.** Requests send `Content-Type: application/json`.
- **Money is integer rupiah.** Every amount (`hargaKontrak`, `amount`) is a JSON
  integer. Never a float, never a formatted string. The client parses user input
  like `"5.000.000"` to `5000000` before sending.
- **Ids are strings.** Projects and cost items use client-generated ids, so a
  create can be retried without producing a duplicate. Users get server-generated
  UUIDs.
- **Timestamps** are ISO-8601 UTC strings, e.g. `"2026-09-22T05:24:54.000Z"`.
- **Success envelope:** `{ "data": ... }`
- **Error envelope:**
  ```json
  { "error": { "code": "VALIDATION_ERROR", "message": "Human-readable text", "details": [ ... ] } }
  ```
  `details` is present only for validation errors.

## Authentication

JWT bearer tokens. Send `Authorization: Bearer <accessToken>` on every request
except `register`, `login` and `refresh`.

- **Access token**: short-lived (15 minutes). Sent on each request.
- **Refresh token**: long-lived (30 days), single use. Each refresh returns a new
  pair and revokes the old refresh token (rotation). Reusing a spent refresh token
  is rejected.

When an access token expires the server answers `401 TOKEN_EXPIRED`; the client
calls `refresh` and retries.

`register` and `login` are rate limited per client IP (20 requests per 15
minutes) and answer `429 RATE_LIMITED` beyond that. `refresh` is not limited.

### `POST /auth/register`

```json
{ "fullName": "Rei Soemanto", "email": "rei@example.com", "password": "at-least-8-chars" }
```

`201` →
```json
{ "data": { "accessToken": "...", "refreshToken": "...", "user": { "id": "uuid", "email": "rei@example.com", "fullName": "Rei Soemanto" } } }
```

Errors: `400 VALIDATION_ERROR`, `409 EMAIL_TAKEN`.

### `POST /auth/login`

```json
{ "email": "rei@example.com", "password": "..." }
```

`200` → same shape as register. Errors: `400 VALIDATION_ERROR`, `401 INVALID_CREDENTIALS`.

### `POST /auth/refresh`

```json
{ "refreshToken": "..." }
```

`200` →
```json
{ "data": { "accessToken": "...", "refreshToken": "..." } }
```

Errors: `401 INVALID_REFRESH_TOKEN` (unknown, expired, or already used).

## Projects

All project endpoints are scoped to the authenticated user. Another user's
project answers `404`, never `403`, so ids cannot be probed.

### Project shape

```json
{
  "id": "a1b2c3...",
  "name": "Project IT System",
  "customer": "PT Maju Jaya",
  "pic": "Budi",
  "hargaKontrak": 50000000,
  "items": [
    { "id": "...", "kind": "JASA",         "description": "Rancang bangun", "engineer": "Andi", "quantity": null, "amount": 5000000 },
    { "id": "...", "kind": "BARANG",       "description": "Kabel 2m",       "engineer": null,   "quantity": 10,   "amount": 25000 },
    { "id": "...", "kind": "TRANSPORTASI", "description": "Sewa mobil",     "engineer": null,   "quantity": null, "amount": 300000 },
    { "id": "...", "kind": "LAIN_LAIN",    "description": "Konsumsi",       "engineer": null,   "quantity": null, "amount": 150000 }
  ],
  "createdAt": "2026-09-22T05:24:54.000Z",
  "updatedAt": "2026-09-22T05:30:00.000Z"
}
```

**Cost items** share one shape, discriminated by `kind`:

| kind | `description` holds | `engineer` | `quantity` | `amount` means |
|---|---|---|---|---|
| `JASA` | scope pekerjaan | required-ish, may be empty | `null` | harga jasa |
| `BARANG` | nama barang | `null` | integer ≥ 0 | harga satuan |
| `TRANSPORTASI` | keterangan | `null` | `null` | biaya |
| `LAIN_LAIN` | keterangan | `null` | `null` | biaya |

Item order in the array is the display order and is preserved.

Totals are **not** stored or returned — the client computes them from the items,
so there is one source of truth for the arithmetic.

### `GET /projects`

`200` → `{ "data": [Project, ...] }`, newest first. Includes items.

### `POST /projects`

```json
{ "id": "a1b2c3...", "name": "Project IT System", "customer": "PT Maju Jaya", "pic": "Budi", "hargaKontrak": 50000000, "items": [] }
```

`id` is optional; the server generates one if omitted. `items` is optional.

`201` → `{ "data": Project }`. Errors: `400 VALIDATION_ERROR`, `409 PROJECT_EXISTS` (id already used).

### `GET /projects/:id`

`200` → `{ "data": Project }`. Errors: `404 NOT_FOUND`.

### `PATCH /projects/:id`

Header fields only; every field optional.

```json
{ "name": "...", "customer": "...", "pic": "...", "hargaKontrak": 60000000 }
```

`200` → `{ "data": Project }`. Errors: `400`, `404`.

### `PUT /projects/:id/items`

Replaces the project's entire item list in one transaction. This is what the
client's debounced autosave sends.

```json
{ "items": [ CostItem, ... ] }
```

`200` → `{ "data": Project }`. Errors: `400`, `404`.

Limits: at most 20 `TRANSPORTASI` items (`400 VALIDATION_ERROR` otherwise), at
most 500 items in total.

### `DELETE /projects/:id`

`204`, no body. Errors: `404`.

## Error codes

| HTTP | code | when |
|---|---|---|
| 400 | `VALIDATION_ERROR` | body or params fail validation |
| 401 | `UNAUTHORIZED` | no or malformed bearer token |
| 401 | `TOKEN_EXPIRED` | access token expired — refresh and retry |
| 401 | `INVALID_CREDENTIALS` | wrong email or password |
| 401 | `INVALID_REFRESH_TOKEN` | refresh token unknown, expired, or reused |
| 404 | `NOT_FOUND` | resource missing or not owned by caller |
| 409 | `EMAIL_TAKEN` | register with an existing email |
| 409 | `PROJECT_EXISTS` | create with an id already in use |
| 429 | `RATE_LIMITED` | too many auth attempts from one client; retry after the `RateLimit` header's reset |
| 500 | `INTERNAL_ERROR` | anything unexpected; details are logged, not returned |
