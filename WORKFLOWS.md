## Builds and releases

The GitHub Actions workflows follow the same stages as LDESServer:

| Workflow                           | Purpose                                                                                                                                                                                                              |
|------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.a. Build & Test Project          | Verify pull requests with Maven, optionally analyze with SonarCloud, and build and scan both container images with Trivy.                                                                                            |
| 2.a. Build & Deploy Project        | Verify pushes to `main` and `develop`, optionally publish Maven packages, and publish both container images for `linux/amd64` and `linux/arm64`. Can also be run manually, replacing the former image-only workflow. |
| 2.b. Build Docs                    | Publish versioned documentation under `/Linked-Data-Interactions/<version>`.                                                                                                                                         |
| 3. Approve Snapshot                | Open a pull request against `main` that removes the `-SNAPSHOT` suffix, manually from `main` or when a release targeting `main` is published.                                                                        |
| 4. Prepare New Version for Project | Prepare a patch, minor, or major version bump on `main` or `develop`, using the release SSH key.                                                                                                                     |
| 5. Create Snapshot Version         | Manually bump the major, minor, or patch version, append `-SNAPSHOT`, and commit the updated Maven POMs directly to the selected `main` or `develop` branch.                                                         |

Both **LDIO** (`ldi-orchestrator`) and **LDES Discoverer** (`ldes-discoverer`) are published.
Builds from `main` publish to Docker Hub under the `DOCKER_NAMESPACE` repository variable, tagged
with the Maven version.
Non-snapshot releases also receive the `latest` tag.
Builds from `develop` (and manual builds from other branches) publish to GHCR using `GHCR_LDIO_REPO`
and `GHCR_DISCOVERER_REPO`, with timestamped version tags and `latest`.

The workflows use the `JAVA_DISTRIBUTION` and `JAVA_VERSION` variables.
`ANALYSE_PACKAGE` and `DEPLOY_PACKAGE_TO_OSSRH` enable optional SonarCloud analysis and Maven
publication; both default to disabled when unset.
Maven publication configures the `central` and `snapshots` server IDs with Central Portal user-token
credentials. Ordinary verification does not sign artifacts; signing runs only with the `release`
profile during publication, using `MAVEN_GPG_PASSPHRASE` for non-interactive signing.
Docker Hub publication requires `DOCKER_USER` and `DOCKER_PASSWORD`; GHCR uses `GITHUB_TOKEN` with
package write permission.
Snapshot promotion requires the repository setting that allows GitHub Actions to create pull
requests, and uses `GIT_CONFIG_USERNAME` and `GIT_CONFIG_EMAIL` for its commit.
Documentation deployment uses `DEPLOY_DOCS_PAT`; version preparation uses `SSH_PRIVATE_KEY`.

Snapshot creation uses `GIT_CONFIG_USERNAME` and `GIT_CONFIG_EMAIL` for its commit and
`GITHUB_TOKEN` with contents write permission to push. Branch protection must allow this push.
The selected component is incremented even if the current version already ends in `-SNAPSHOT`;
major and minor bumps reset the lower components to zero (a bump in the major version will result in
{MAJOR+1}.0.0). Commits pushed with `GITHUB_TOKEN` do not trigger the push-based build workflow.

### GitHub Actions variables and secrets

Configure these under **Settings > Secrets and variables > Actions** (or inherit them from the
organization). "Required" refers to the workflows or features listed in that row. Defaults below
are the effective behavior when unset; example values are not automatically configured.

| Name                            | Type             | Required                                                               | Default when unset               | Explanation                                                                                                                                                                                                                                                               |
|---------------------------------|------------------|------------------------------------------------------------------------|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `JAVA_DISTRIBUTION`             | Variable         | Yes, for workflows 1.a, 2.a, 2.b, 3 and 5                              | None                             | JDK distribution used by `actions/setup-java`, for example `temurin`.                                                                                                                                                                                                     |
| `JAVA_VERSION`                  | Variable         | Yes, for workflows 1.a, 2.a, 2.b, 3 and 5                              | None                             | JDK version used by `actions/setup-java`; set to `21` to match this project's Maven configuration.                                                                                                                                                                        |
| `ANALYSE_PACKAGE`               | Variable         | No                                                                     | `false`                          | Set to `true` to enable SonarCloud analysis in workflows 1.a and 2.a. Fork and Dependabot pull requests skip analysis. Use a JSON boolean (`true` or `false`).                                                                                                            |
| `SONAR_ORGANIZATION`            | Variable         | When `ANALYSE_PACKAGE=true`                                            | None                             | SonarCloud organization key passed to the Maven scanner.                                                                                                                                                                                                                  |
| `SONAR_PROJECT_KEY`             | Variable         | When `ANALYSE_PACKAGE=true`                                            | None                             | SonarCloud project key identifying the analysis target.                                                                                                                                                                                                                   |
| `DEPLOY_PACKAGE_TO_OSSRH`       | Variable         | No                                                                     | `false`                          | Set to `true` to sign and publish Maven artifacts in workflow 2.a using the `release` profile, `central` for releases and `snapshots` for snapshots. Use a JSON boolean (`true` or `false`). |
| `DEPLOY_DOCUMENTATION`          | Variable         | No                                                                     | Enabled                          | Set to the literal value `false` to skip workflow 2.b. Any other value, including an unset variable, enables documentation deployment.                                                                                                                                    |
| `DOCKER_NAMESPACE`              | Variable         | For Docker Hub publication from `main`                                 | None                             | Docker Hub user or organization namespace. Workflow 2.a publishes `<namespace>/ldi-orchestrator` and `<namespace>/ldes-discoverer`.                                                                                                                                       |
| `GHCR_LDIO_REPO`                | Variable         | For LDIO publication from branches other than `main`                   | None                             | Lowercase GHCR image path without `ghcr.io/`, for example `openldes/ldi-orchestrator`. Used by workflow 2.a.                                                                                                                                                              |
| `GHCR_DISCOVERER_REPO`          | Variable         | For Discoverer publication from branches other than `main`             | None                             | Lowercase GHCR image path without `ghcr.io/`, for example `openldes/ldes-discoverer`. Used by workflow 2.a.                                                                                                                                                               |
| `GIT_CONFIG_USERNAME`           | Variable         | For documentation deployment, snapshot promotion and snapshot creation | None                             | Git committer name used by workflows 2.b, 3 and 5. Workflow 4 uses its own fixed release-bot identity.                                                                                                                                                                    |
| `GIT_CONFIG_EMAIL`              | Variable         | For documentation deployment, snapshot promotion and snapshot creation | None                             | Git committer email used by workflows 2.b, 3 and 5.                                                                                                                                                                                                                       |
| `SONAR_TOKEN`                   | Secret           | When `ANALYSE_PACKAGE=true`                                            | None                             | SonarCloud token authorized to analyze the configured project.                                                                                                                                                                                                            |
| `OSSRH_USERNAME`                | Secret           | When `DEPLOY_PACKAGE_TO_OSSRH=true`                                    | None                             | Central Portal user-token username, exposed as `MAVEN_USERNAME` for both Maven publishing servers. |
| `OSSRH_TOKEN`                   | Secret           | When `DEPLOY_PACKAGE_TO_OSSRH=true`                                    | None                             | Central Portal user-token password, exposed as `MAVEN_PASSWORD` for both Maven publishing servers. |
| `OSSRH_GPG_SECRET_KEY`          | Secret           | When `DEPLOY_PACKAGE_TO_OSSRH=true`                                    | None                             | ASCII-armored GPG private key imported by `actions/setup-java` to sign Maven artifacts.                                                                                                                                                                                   |
| `OSSRH_GPG_SECRET_KEY_PASSWORD` | Secret           | When Maven signing uses a passphrase-protected key                     | None                             | Passphrase for the GPG private key, exposed as `MAVEN_GPG_PASSPHRASE` during publication. |
| `DOCKER_USER`                   | Secret           | For Docker Hub publication from `main`                                 | None                             | Docker Hub login username with permission to push both images to `DOCKER_NAMESPACE`.                                                                                                                                                                                      |
| `DOCKER_PASSWORD`               | Secret           | For Docker Hub publication from `main`                                 | None                             | Docker Hub access token or password for `DOCKER_USER`; prefer an access token with write permission.                                                                                                                                                                      |
| `DEPLOY_DOCS_PAT`               | Secret           | Unless `DEPLOY_DOCUMENTATION=false`                                    | None                             | Personal access token with repository contents write access for pushing the generated GitHub Pages documentation in workflow 2.b.                                                                                                                                         |
| `SSH_PRIVATE_KEY`               | Secret           | For version preparation in workflow 4                                  | None                             | SSH private key with repository write access for the Maven release action. Its public key must be authorized on GitHub, for example as a write-enabled deploy key.                                                                                                        |
| `GITHUB_TOKEN`                  | Automatic secret | No manual configuration                                                | Generated by GitHub for each job | Used for checkout, SonarCloud integration, GHCR publication, documentation package access, snapshot promotion and snapshot creation. Permissions are controlled by each workflow/job; repository settings and branch protection must also allow the requested operations. |

`MAVEN_USERNAME`, `MAVEN_PASSWORD`, `MAVEN_GPG_PASSPHRASE` and `NODE_AUTH_TOKEN` are environment
variables populated by the workflows from the secrets above; do not create separate GitHub
variables or secrets for them.
