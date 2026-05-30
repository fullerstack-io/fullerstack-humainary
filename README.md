# Fullerstack Humainary — Codespace Workspace

This repo is the GitHub Codespaces development environment for the **`fullerstack-io`** project family. It exists to give every downstream project a pre-populated workspace with the Humainary API clones and a working Maven configuration, so a fresh Codespace boots ready to build.

It is intentionally public so the user's org plan permits Codespaces to launch from it.

## What lives where

| Path | Purpose |
|---|---|
| `agent-insight/` | Agent-observability product. Pulls `fullerstack-substrates:2.9.0` from GitHub Packages. |
| `backtest/` | Crypto backtest investigation. Pulls `fullerstack-substrates:2.9.0` from GitHub Packages. |
| `substrates-api-java/` | Local clone of [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java). Required for `mvn install` because the upstream JARs are not on a public Maven repo. |
| `serventis-api-java/` | Local clone of [Humainary Serventis API](https://github.com/humainary-io/serventis-api-java). Same prereq. |
| `substrates-api-spec/` | Reference clone of the [Humainary Substrates specification](https://github.com/humainary-io/substrates-api-spec). Read-only. |

The Substrates implementation that used to live here has moved to its own repo at [`fullerstack-io/fullerstack-substrates`](https://github.com/fullerstack-io/fullerstack-substrates) and is published as `io.fullerstack:fullerstack-substrates:2.9.0` on GitHub Packages.

## First-time Codespace setup

```bash
# 1. Install the Humainary upstream APIs into the local Maven cache.
#    Required because they are not on Maven Central or any public Maven repo.
cd substrates-api-java/api && mvn clean install -DskipTests && cd ../..
cd serventis-api-java/api  && mvn clean install -DskipTests && cd ../..

# 2. Export a GitHub PAT with `read:packages` so Maven can pull
#    io.fullerstack:fullerstack-substrates from GitHub Packages.
export GITHUB_TOKEN=$(gh auth token -u milesfuller)   # or any read:packages PAT

# 3. Build a downstream project.
cd agent-insight && mvn -DskipTests compile
cd backtest     && mvn -DskipTests compile
```

`~/.m2/settings.xml` is preconfigured to authenticate to GitHub Packages as `milesfuller` against the `github-fullerstack` server using `${env.GITHUB_TOKEN}`.

## Working on Substrates itself

Substrates development happens in its own repo, not here. Open `fullerstack-io/fullerstack-substrates` in a separate Codespace (or locally) and develop there. Push a release; the next Codespace started from this repo will resolve the new version from GitHub Packages.

## License

This workspace repo is configured under Apache 2.0. The downstream projects and `fullerstack-substrates` itself are independently licensed (also Apache 2.0). Humainary API design credit: **William Louth** and the **[Humainary](https://humainary.io/)** project.
