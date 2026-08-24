# hc-market-quality

Runs the whole hc-market estate on **jacserver**, seeded, at a hostname, behind the same proxy the
server has — and then checks that it behaves.

```bash
./startup.sh --local            # you are already on jacserver
./startup.sh --local --verify   # touch nothing, re-run the checks
./startup.sh --local --down     # stop, keep the databases
./startup.sh --local --clean    # stop and drop the database volumes
```

Then `curl http://market.healthconnect.local/services/healthconnectcatalog/api/professionals/count`
→ `18`.

Shaped after `hc-admin/quality` and `hc-professional/quality` deliberately, so the four read side by
side. The differences below are the ones that matter.

## There is no web container, and that costs this stack something

Every sibling puts a `web` container behind the vhost: jacserver's nginx proxies to another nginx
which serves an Angular app and forwards the API paths. **Two hops, deliberately** — the bugs that
hide in a proxy chain only appear when there is a chain.

hc-market is API-only. Spec §2 settled that JHipster generates no SPA for it, so there is nothing to
put in front of and the vhost proxies straight to the gateway. **One hop.** The consequence is worth
saying rather than discovering: the class of defect those stacks exist to catch — a header or a
scheme mangled between two nginxes — *cannot be rehearsed here*. This stack still catches CSP
failures, wrong-image deploys and hostname theft. It does not catch second-hop bugs, because there
is no second hop.

## dev,test is what makes it seeded

`SeedDataLoader` is `@Profile("test & dev")` — it requires **both**, never one alone. So
`SPRING_PROFILES_ACTIVE: dev,test` is not habit inherited from the siblings; it is the thing that
makes this stack have data in it. Set it to `dev` and the estate comes up correct and empty.

Dates load **unshifted** (`HEALTHCONNECT_SEED_ANCHOR_DATES: true`), unlike the dev estate. A shifting
seed is right for a demo and wrong for a place whose job is catching drift: the figures here are the
prototype's own, so they can be compared against it directly.

## Consul is off

As in every sibling quality stack, and for the reason production does it: the gateway routes
statically. Discovery is a development convenience, and leaving it on would mean rehearsing a
routing mechanism production does not use. The four routes are spelled out in `compose.yml` — without
them the gateway starts, answers `/management` perfectly, and 404s every `/services/**` path, which
is a healthy edge in front of nothing.

## Images are local, and that is a weakness

The siblings pull published images, which is the better discipline: it proves the thing you are
about to deploy is the thing you built. hc-market publishes nothing yet — no CI, no registry push —
so `--images=local` is the default and the script warns on every run. Switch the default the day
images are published; the flag already exists.

## Two things need root, and the script runs neither

`/etc` is not this repository's to edit. `startup.sh` prints these and stops.

```bash
echo '127.0.0.1  market.healthconnect.local market.abofonsa.local' | sudo tee -a /etc/hosts

sudo ln -sfn ~/work/health-connect/workspace/hc-market/quality/host-site.conf \
             /etc/nginx/sites-enabled/market.healthconnect.local.conf
sudo nginx -t && sudo systemctl reload nginx
```

Symlinked rather than copied, so editing `host-site.conf` here edits the live site after a reload.

### `listen 127.0.0.1:80` is not redundant

nginx picks the server block by the most specific listen **address** before it ever looks at
`server_name`. A block declaring `127.0.0.1:80` owns every loopback connection; a block declaring
only `*:80` is not even a candidate. On this machine `admin.healthconnect.conf`,
`patient.healthconnect.local.conf`, `professional.abofonsa.local.conf` and
`monitoring.jojoaddison.local.conf` **all** declare it — so with a wildcard alone, every request to
`market.healthconnect.local` *from jacserver itself* would be served by one of those, answering 200
with somebody else's application.

That is on record: `hc-admin/quality/host-site.conf` documents `admin.healthconnect.local` serving
the patient app for exactly this reason. It is inherited here rather than rediscovered — which is
also why every check in `startup.sh` reads the response **body**, never just the status.

## Ports

`GATEWAY_PORT` is written twice — here and in `host-site.conf` — because nginx cannot read the
environment. `startup.sh` checks the two agree rather than letting them drift into a 502.

| | port | |
|---|---|---|
| gateway | 15509 | the vhost's upstream |
| catalog | 18100 | |
| booking | 18101 | |
| messaging | 18102 | |
| payout | 18103 | |

All bind `127.0.0.1`, so the vhost is the only way in. The four domain services are published only
so that "the gateway returns 502" can be diagnosed by asking the service directly.
