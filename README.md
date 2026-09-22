# CostProject

Job-cost calculator. Projects hold Jasa, Barang, Transportasi and Lain-lain line
items, totalled against the contract value.

This repository holds both halves:

| Folder | What | Stack |
|---|---|---|
| [composeApp/](composeApp/) | The app, Android and iOS from one codebase | Kotlin Multiplatform, Compose Multiplatform, Ktor |
| [server/](server/) | The API the app syncs with | Express, TypeScript, PostgreSQL |
| [iosApp/](iosApp/) | Xcode wrapper for the iOS build | Swift |

The API contract both sides build against is [server/API.md](server/API.md).

## Running it locally

**1. Start the server** — full setup in [server/README.md](server/README.md):

```sh
cd server
npm install
cp .env.example .env      # then set DATABASE_URL and JWT_ACCESS_SECRET
npm run migrate
npm run dev               # http://localhost:3000
```

**2. Run the app** on an Android emulator from Android Studio, or:

```sh
./gradlew :composeApp:installDebug
```

Debug builds reach the server at `http://10.0.2.2:3000`, the emulator's alias
for your computer. On a physical phone on the same Wi-Fi, pass your computer's
LAN address instead:

```sh
./gradlew :composeApp:installDebug -PapiBaseUrl=http://192.168.1.10:3000/api/v1/
```

Release builds only allow HTTPS; set `-PapiBaseUrl` to the deployed server.

## Tests

```sh
./gradlew :composeApp:testDebugUnitTest   # app: models, mapping, repositories, ViewModels
cd server && npm test                     # API: routes, auth, validation (no database needed)
```

The server's Postgres integration tests run when `TEST_DATABASE_URL` is set; see
[server/README.md](server/README.md#tests).

## App structure

MVVM, with the package layout of the NFC-Konekt Android project, adapted for
Kotlin Multiplatform. Everything below lives in `composeApp/src/commonMain`.

```
data/
  container/    AppContainer - builds clients, services and repositories once
  dto/          wire shapes, mirroring server/API.md
  local/        token storage, and import of pre-server on-device projects
  remote/       Ktor clients, API services, error mapping
  mapper/       DTO <-> domain conversion (typed text <-> integer rupiah)
  repository/   AuthRepository, ProjectRepository - the error boundary
domain/model/   Project and its line items
ui/
  routing/      navigation graph, gated on the session
  viewmodel/    one ViewModel per screen, StateFlow + sealed UI state
  view/         composables, grouped by screen
  theme/  util/
```

Platform-specific code is limited to `androidMain/` and `iosMain/`: the HTTP
engine, secure token storage (Keychain on iOS), and the system back gesture.

## Things worth knowing

- **Money is whole rupiah in a `Long`**, never a `Double`. The user types
  "5.000.000"; `ProjectMapper` sends `5000000`.
- **Edits autosave** 800 ms after typing stops. Leaving a project waits for the
  last save and warns if it could not be sent.
- **Projects saved on the device** by the version of the app from before there
  was a server are uploaded to the account on first sign-in, then removed from
  the device once the server has accepted them.
- **iOS cannot be built on Windows.** On Windows, run
  `./gradlew :composeApp:compileCommonMainKotlinMetadata` after changing shared
  code: it fails if anything Android-only has crept into `commonMain`, which
  would otherwise only surface on a Mac.
