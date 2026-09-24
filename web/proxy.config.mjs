// The dev server's proxy to the estate's gateway.
//
// THE APPLICATION ITSELF IS ORIGIN-AGNOSTIC and must stay that way: `angular.json` defines
// `SERVER_API_URL` as `''`, `app.ts` hands that to `ApplicationConfigService.setEndpointPrefix`, and
// every URL is therefore relative. This file is the ONLY place a host and a port appear, and it
// applies to `ng serve` alone — never to a built bundle. How the built application is served, and
// therefore which origin it addresses, is a deployment question and is deliberately not answered
// here (decisions.md D101 §6).
//
// THE PORT IS READ FROM `HC_GATEWAY_PORT`, WHICH IS THE ESTATE'S OWN VARIABLE, and the default `8080`
// is the same default `deploy/deploy-dev.sh:110` and `deploy/docker/docker-compose.dev.yml:330`
// carry. Writing a third default here is how the three drift; the generator shipped a bare literal
// `8080`, which is that default by coincidence rather than by reference.
//
// ⚠ ON jacserver 8080 IS SOMEBODY ELSE'S API. `abofonsa_api` publishes it (hc-market/CLAUDE.md,
// "Published ports collide on this workstation"), so an unoverridden `npm start` here proxies the
// marketplace client into another product — which answers, plausibly, with the wrong estate. That is
// the `admin.healthconnect.local` collision one layer along, and the reason it is worth a paragraph:
// nothing about it fails loudly. Override together with everything else, as that section instructs:
//
//   HC_GATEWAY_PORT=18200 npm start          # a dev estate started with the same override
//   HC_GATEWAY_PORT=15509 npm start          # the quality gateway on this box (quality/startup.sh:80)
//
// `/api` and `/management` are the gateway's own; `/services` is how the gateway routes to the four
// microservices (`/services/<service>/api/**`), and it is proxied here so a screen built in Stage B
// onwards reaches them through `getEndpointFor(api, microservice)` without a second origin.
const backendHost = process.env.HC_GATEWAY_HOST ?? '127.0.0.1';
const backendPort = process.env.HC_GATEWAY_PORT ?? '8080';

/**
 * @type {import('vite').CommonServerOptions['proxy']}
 */
export default {
  '^/(api|management|services|v3/api-docs)': {
    target: `http://${backendHost}:${backendPort}`,
    xfwd: true,
  },
};
