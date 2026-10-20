# FuroCash frontend

React, TypeScript and Vite. See the [project README](../README.md) for database setup and complete app startup.

```text
npm ci
npm run dev
```

Open http://localhost:5173 with the Java API running on port 7070. Vite proxies `/api` to that backend. `npm run build` creates `dist/`, which the packaged Java server can serve directly.

`npm test` runs API regression tests and React form/session/dashboard flows. `npm run lint` checks the source. `npm run format` formats source and tests.

Never put secrets in `VITE_` environment variables: they are public browser configuration.
