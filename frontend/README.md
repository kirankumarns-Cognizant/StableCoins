# StableFlow connected payment workspace

React + TypeScript frontend for the existing six-service stablecoin payment POC.
See [setup, security and screen inventory](../docs/32-CONNECTED-REACT-WORKSPACE.md)
and the [complete role/action demonstration script](../docs/29-PROTOTYPE-EXECUTION-RUNBOOK.md).

Node 22.18+ is required. Run `npm ci`, `npm test`, `npm run lint`, `npm run build`,
then `npm run dev`. Configure the shared repository connection settings and start
the existing backend first; the app does not insert mock results if APIs fail.

`npm run test:browser` is an optional synthetic HTTP/browser contract rehearsal.
It requires an installed Edge-compatible executable (`EDGE_PATH` override), uses
an isolated profile, and writes ignored artifacts under `.local/step9-ui`.
It does not execute identity, database, model or blockchain integration.
