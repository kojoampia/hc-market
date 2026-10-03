import { defineConfig } from 'cypress';

export default defineConfig({
  video: false,
  fixturesFolder: 'src/test/javascript/cypress/fixtures',
  screenshotsFolder: 'target/cypress/screenshots',
  downloadsFolder: 'target/cypress/downloads',
  videosFolder: 'target/cypress/videos',
  chromeWebSecurity: true,
  viewportWidth: 1200,
  viewportHeight: 720,
  retries: 2,
  // `allowCypressEnv: false` STAYS FALSE, and reading `process.env` below is unrelated to it rather
  // than a way around it. Measured against `cypress@15.18.1`'s own types: this flag governs whether
  // the DEPRECATED browser-side `Cypress.env()` API is available in a spec — Cypress recommends
  // disabling it and will remove the API — and it does not govern `CYPRESS_*` variables, which still
  // reach `cy.env()`. This file is TypeScript evaluated in Node and reads the environment directly,
  // which no Cypress flag constrains. Flipping it re-enables an API on its way out: decisions.md D108 §4.
  allowCypressEnv: false,
  expose: {
    // THE FOUR CREDENTIALS COME FROM THE ENVIRONMENT, WITH THE dev/test SEEDED VALUES AS THE
    // DOCUMENTED DEFAULT — backlog NEW-80, decisions.md D108. They were four bare `'admin'`
    // literals, which is not a leak and is a thing a reader has to RECONSTRUCT before concluding so:
    // this repository publishes the rule that `dev`/`test` create `admin` and `user` with passwords
    // derived from their own logins, and under `prod` the gateway REFUSES to create an administrator
    // without `HC_GATEWAY_ADMIN_PASSWORD` rather than falling back to it (D61). Both facts live three
    // documents away. The fallback states the provenance here, and the variable states where a real
    // value comes from — which is the estate's standing rule inverted: a committed default may exist
    // only where it cannot be the value a running estate uses.
    //
    // The values are the generator's own and are UNCHANGED, `username`/`password` included — those
    // are its duplication of the same administrator rather than a second account, and correcting
    // them to `user` is a behaviour change to a harness nobody runs (NEW-94).
    //
    // ⚠ THERE IS A SECOND, GENERATED OVERRIDE AND IT IS NOT A CONTRADICTION — it is the outer link of
    // one chain. `commands.ts` reads `E2E_USERNAME ?? Cypress.expose('adminUsername')` through
    // `cy.env`, so `CYPRESS_E2E_USERNAME`/`CYPRESS_E2E_PASSWORD` win over anything here. That pair is
    // ONE username and ONE password for all four slots; these four are per-slot, which is what an
    // estate with a separate administrator and customer needs. Set one or the other, not both.
    //
    // ⚠ GENERATED FILE. `jhipster --force` puts the four literals back and nothing fails, because
    // nothing in this estate runs Cypress — `build.yml` sets `CYPRESS_INSTALL_BINARY: '0'`, so the
    // binary is not even installed. `.github/checks/e2e-credentials-are-not-committed.sh` is what
    // goes red; keep each of these on ONE line, which is what that check can read.
    adminUsername: process.env.HC_E2E_ADMIN_USERNAME ?? 'admin',
    adminPassword: process.env.HC_E2E_ADMIN_PASSWORD ?? 'admin',
    username: process.env.HC_E2E_USERNAME ?? 'admin',
    password: process.env.HC_E2E_PASSWORD ?? 'admin',
    authenticationUrl: '/api/authenticate',
    jwtStorageName: 'abm-authenticationToken',
  },
  e2e: {
    // We've imported your old cypress plugins here.
    // You may want to clean this up later by importing these.
    async setupNodeEvents(on, config) {
      return (await import('./src/test/javascript/cypress/plugins/index')).default(on, config);
    },
    baseUrl: 'http://localhost:8080/',
    specPattern: 'src/test/javascript/cypress/e2e/**/*.cy.ts',
    supportFile: 'src/test/javascript/cypress/support/index.ts',
    experimentalRunAllSpecs: true,
  },
});
