# 公共镜像仓库发布方案（Docker）

本文说明如何将 **Log4AI standalone** 镜像构建并推送到**公共**容器镜像库（以 **Docker Hub**、**GitHub Container Registry (ghcr.io)** 为主），供他人 `docker pull` 使用。若仅需内网或本机使用，无需执行本文流程（见主 `README`「未发布镜像仓库时怎么用」）。

### 本仓库快速推送（GHCR + GitHub Actions）

已内置工作流 **[`docker-publish.yml`](../.github/workflows/docker-publish.yml)**（镜像）与 **[`ci.yml`](../.github/workflows/ci.yml)**（`mvn verify` + 上传 JAR）。推送到 **GitHub** 后按下列步骤即可开始推送镜像：

1. **仓库设置**：**Settings → Actions → General → Workflow permissions** 勾选 **Read and write permissions**（使 `GITHUB_TOKEN` 能写入 `ghcr.io`；若使用默认只读权限，推送会失败）。
2. **触发构建**（二选一）  
   - **打 Git 标签**：`git tag v0.1.0 && git push origin v0.1.0`（标签须符合 `v*.*.*` ）  
   - **手动运行**：**Actions** → **Publish Docker image (GHCR)** → **Run workflow**，输入版本号（如 `0.1.0`，**不要** `v` 前缀）。
3. **公开包**（首次）：构建成功后到 **Packages** 页面，将 **`log4ai-server`** 设为 **Public**，否则他人拉取可能无权限。
4. **拉取**：`docker pull ghcr.io/<你的小写 GitHub 用户名或组织>/log4ai-server:0.1.0`

### 本机连不上 GitHub（无法 `git push`）时：用 Actions 在云端构建并推送

工作流在 **GitHub 云端** 执行 `mvn package`、`docker build` 并 **`docker push` 到 GHCR**，**不依赖**你本机能否访问 `github.com:443`。但注意：**构建用的是 GitHub 仓库里 `main` 上的代码**；若你只在本地改了文件、从未推到远端，云端构建**不会包含**这些改动。

**推荐流程**：

1. **先把代码弄到 GitHub**（任选其一）  
   - 换网络 / 代理 / SSH 后再 `git push`；  
   - 或在仓库页 **Add file → Upload files** / 在线编辑，把变更提交到 `main`；  
   - 或另一台能访问 GitHub 的机器克隆后拷贝你的改动再推送。
2. 打开：**Actions** → 左侧 **Publish Docker image (GHCR)** → 右侧 **Run workflow**。  
3. **Branch** 选 **`main`**（或你要打镜像的分支）。  
4. **version** 填镜像标签，如 **`0.1.2`**（**不要**加 `v`）。  
5. 点 **Run workflow**，等待绿色成功。  
6. 服务器上：`docker pull ghcr.io/<owner>/log4ai-server:0.1.2`（或 `latest`，视工作流是否推送 `latest` 而定）。

若已配置 Secret **`GHCR_TOKEN`**，登录步骤会优先用它，可缓解组织内 `GITHUB_TOKEN` 无法写 GHCR 的问题。

以下为通用说明（含 Docker Hub 与手动 `docker push`）。

---

## 1. 目标与边界

| 项目 | 说明 |
|------|------|
| **交付物** | 可运行的 OCI 镜像，默认入口为 **standalone** 可执行 JAR（`Log4AiStandaloneApplication`），监听 **8080**。 |
| **不包含** | 业务日志、LLM API Key、私有证书；这些由部署方通过 **环境变量 / Secret / 挂载卷** 注入。 |
| **与 Maven 构件关系** | 镜像内嵌 **`*-standalone.jar`**；发布 Maven 坐标（`com.log4ai:spring-boot-log4ai`）与发布 Docker 镜像**相互独立**，可分开版本号。 |

---

## 2. 公共镜像库选型

| 平台 | 镜像地址示例 | 特点 |
|------|----------------|------|
| **Docker Hub** | `docker.io/<namespace>/log4ai-server` | 生态最广；免费账号有拉取速率与私有库数量限制。 |
| **GitHub Container Registry (GHCR)** | `ghcr.io/<org-or-user>/log4ai-server` | 与 GitHub 仓库同源协作方便；可对公开仓库设公开包。 |
| **其他** | 阿里云 ACR 国际站、Quay.io 等 | 按团队合规与网络选择；流程与下文类似，仅 **login / 前缀** 不同。 |

**命名建议**：仓库名用 **`log4ai-server`** 或 **`spring-boot-log4ai`**，与 Maven `artifactId` 对齐，避免与「库 JAR」混淆时可加后缀 `-standalone`。

---

## 3. 镜像命名与标签（Tag）策略

推荐**多标签**指向同一构建，便于运维锁定版本。

| 标签类型 | 示例 | 用途 |
|----------|------|------|
| **语义化版本** | `0.1.0`、`0.1.1` | 正式发布，与 Git tag / Maven 版本对齐。 |
| **主版本浮动** | `0.1` | 指向该主版本线最新补丁（可选，需流程约定）。 |
| **`latest`** | `latest` | 默认拉取；**建议仅 stable 发布时更新**，避免将不稳定构建标为 latest。 |
| **预发布** | `0.2.0-rc.1`、`sha-abc1234` | CI 每 commit 或 PR 构建（可选）。 |

**禁止**：将 **密钥、令牌** 打进镜像层或标签名。

---

## 4. 前置条件

1. **Docker Buildx**（Docker Desktop 或 Linux 已自带），用于多平台可选扩展（见第 8 节）。
2. **镜像库账号**：Docker Hub 注册 namespace；GitHub 对目标仓库有 **packages** 权限。
3. **访问令牌**（勿提交仓库）  
   - Docker Hub：**Account → Security → New Access Token**  
   - GHCR：GitHub **Personal Access Token (classic)**，勾选 `write:packages`（及 `read:packages`）
4. 本地已能成功 **`docker build`**（见项目根目录 `Dockerfile`）。

---

## 5. 本地构建与手动推送（一次通）

以下以 **Docker Hub**、`namespace=log4ai` 为例，请替换为你的命名空间与版本。

```bash
cd /path/to/Spring-Boot-Log4AI

# 1）构建（与 CI 一致，打版本标签）
export VERSION=0.1.0
docker build -t log4ai/log4ai-server:${VERSION} -t log4ai/log4ai-server:latest .

# 2）登录（交互输入密码或令牌）
docker login

# 3）推送（多标签需分别推送，或使用 buildx --tag 多枚一次推）
docker push log4ai/log4ai-server:${VERSION}
docker push log4ai/log4ai-server:latest
```

**GHCR 示例**：

```bash
export VERSION=0.1.0
export GH_USER=your-github-username
docker build -t ghcr.io/${GH_USER}/log4ai-server:${VERSION} .

echo <GITHUB_PAT> | docker login ghcr.io -u ${GH_USER} --password-stdin
docker push ghcr.io/${GH_USER}/log4ai-server:${VERSION}
```

**GHCR 镜像名须全小写**（`ghcr.io/<owner>/log4ai-server`）；上面 `GH_USER` 请使用小写 GitHub 用户名。

### Windows（PowerShell / CMD）

上文为 **bash**；在 **Windows** 上请用下面写法（勿使用 `export`、`${VAR}` 除非在 **Git Bash / WSL** 中）。

**PowerShell**（推荐）：

```powershell
cd C:\path\to\Spring-Boot-Log4AI

$env:VERSION = "0.1.0"
$env:GH_USER = "your-github-username"   # 小写

docker build -t "ghcr.io/$($env:GH_USER)/log4ai-server:$($env:VERSION)" .

$env:GITHUB_PAT = "ghp_xxxxxxxx"   # 勿用字面 <GITHUB_PAT>；勿提交真实令牌
$env:GITHUB_PAT | docker login ghcr.io -u $env:GH_USER --password-stdin

docker push "ghcr.io/$($env:GH_USER)/log4ai-server:$($env:VERSION)"
```

**CMD**：

```bat
set VERSION=0.1.0
set GH_USER=your-github-username
docker build -t ghcr.io/%GH_USER%/log4ai-server:%VERSION% .

set GITHUB_PAT=ghp_xxxxxxxx
echo %GITHUB_PAT% | docker login ghcr.io -u %GH_USER% --password-stdin

docker push ghcr.io/%GH_USER%/log4ai-server:%VERSION%
```

---

## 6. CI 自动推送（推荐）

将 **构建 + 推送** 放在 CI，避免本机环境不一致，且令牌用 **Secret** 注入。

### 6.1 通用要点

- **触发条件**：`push` 到 `main` / `release/*` 或打 **Git tag `v*`**。
- **Secrets**：`DOCKERHUB_TOKEN` / `GHCR_TOKEN`，**不要**写入 `Dockerfile` 或 yaml 明文。
- **缓存**：利用 GitHub Actions `cache-from/to type=gha` 或 Registry cache 加速 Maven 阶段。

### 6.2 GitHub Actions 示例（Docker Hub）

将以下内容保存为 `.github/workflows/docker-publish.yml`（需根据分支名、镜像名微调）：

```yaml
name: Publish Docker image

on:
  push:
    tags: ['v*.*.*']

env:
  IMAGE_NAME: log4ai/log4ai-server

jobs:
  push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Log in to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Extract version from tag
        id: ver
        run: echo "VERSION=${GITHUB_REF_NAME#v}" >> $GITHUB_OUTPUT

      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ env.IMAGE_NAME }}:${{ steps.ver.outputs.VERSION }}
            ${{ env.IMAGE_NAME }}:latest
```

**需在仓库 Settings → Secrets** 配置：`DOCKERHUB_USERNAME`、`DOCKERHUB_TOKEN`。

### 6.3 GitHub Actions 推送 GHCR

使用 `docker/login-action` 的 `registry: ghcr.io`，`username` 为 GitHub 用户或 `github.actor`，`password` 为 `GITHUB_TOKEN`（默认 `packages: write` 权限需在 workflow `permissions` 中打开）或 PAT。

---

## 7. 使用者拉取与运行

发布后在文档中固定给出**可复制**命令（示例）：

```bash
docker pull log4ai/log4ai-server:0.1.0

docker run --rm -p 8080:8080 \
  -e LLM_API_KEY=sk-*** \
  -e LOGGING_FILE_NAME=/data/logs/application.log \
  -v /宿主机日志目录:/data/logs:ro \
  log4ai/log4ai-server:0.1.0
```

说明：

- **密钥**始终通过 **`-e` / `--env-file` / K8s Secret** 传入；
- **日志路径**为容器内路径，与 **`-v` 挂载**一致；
- 需要持久化控制台配置时，挂载 **`/app/.log4ai`**（见主 `README` Docker 章节）。

---

## 8. 可选：多架构镜像（amd64 / arm64）

在 Apple Silicon 或混合集群中，可使用 **buildx** 一次构建多平台并推送：

```bash
docker buildx create --use
docker buildx build --platform linux/amd64,linux/arm64 \
  -t log4ai/log4ai-server:0.1.0 --push .
```

注意：多阶段构建中 **Maven 阶段**在目标平台执行，CI 中通常用 **QEMU**（`docker/setup-qemu-action`）或分架构矩阵构建。

---

## 9. 安全与合规清单

- [ ] 镜像内**无** `LLM_API_KEY`、数据库密码等（仅用占位或运行时注入）。
- [ ] `.dockerignore` 已排除 `target/`、`.git`，避免误拷构建产物与历史。
- [ ] 按需选择运行用户：默认镜像以 **root** 运行 JVM（便于读挂载日志）；若需降权可在 compose 中设 **`user: "1000:1000"`** 并配合宿主机目录权限。
- [ ] 定期用 **`docker scout`** / Trivy 扫描基础镜像漏洞（可选）。
- [ ] 公开镜像说明中写明：**软件许可证**、**数据与日志不落盘到镜像**、**用户自行承担 LLM 调用费用与合规**。

---

## 10. 与 Maven 版本对齐建议

| 场景 | 建议 |
|------|------|
| **发版** | Git tag `v0.1.0` → Maven `0.1.0` → Docker tag `0.1.0`，三者一致。 |
| **SNAPSHOT** | 一般不推公共 `latest`；可推 `0.2.0-SNAPSHOT` 或仅 CI 内网 registry。 |
| **文档** | 在 `README` 增加「Docker 镜像」小节，写清 **`docker pull` 地址** 与**最小运行示例**。 |

---

## 11. 回滚与灾备

- **回滚应用**：部署时改 tag 为上一语义化版本（如 `0.1.0` → `0.0.9`）。
- **镜像删除**：在 Docker Hub / GHCR 控制台删除有问题的 tag；**勿依赖** `latest` 做生产唯一标识。

---

## 12. 常见问题

### `docker build` 拉取基础镜像失败（`unable to fetch descriptor` / `content size of zero`）

多为访问 **Docker Hub（docker.io）** 不稳定或被限速。可尝试：

- **Docker Desktop** → **Settings** → **Docker Engine** 配置国内 **registry-mirrors**（按你所在网络文档操作）；
- 换网络 / 代理后重试，或执行 **`docker pull eclipse-temurin:17-jre-jammy`** 单独验证能否拉取；
- **不在本机构建**：把代码推 GitHub，用 **[`.github/workflows/docker-publish.yml`](../.github/workflows/docker-publish.yml)** 在云端构建并推 **GHCR**（不依赖本机访问 Docker Hub）。

本仓库 **`Dockerfile`** 仅基于 **`eclipse-temurin:17-jre-jammy`**（**不**再拉取 `maven:*` 镜像）；JAR 由宿主机或 CI 的 **`mvn package`** 生成。

### `failed size validation` / `layer-sha256 ... failed precondition`（层大小校验失败）

多为 **BuildKit 缓存或下载层损坏**（网络中断、磁盘异常等），与业务代码无关。按顺序尝试：

1. **清理构建缓存**（推荐）：执行仓库内 **`scripts/docker-prune-build-cache.bat`**，或手动：  
   `docker builder prune -f`
2. **不使用缓存重建**：  
   `set DOCKER_BUILD_NO_CACHE=1`（Windows CMD）后重新运行 **`scripts/ghcr-build-push.bat`**（脚本已支持传入 `--no-cache`）。
3. **关闭 BuildKit 使用旧构建器**（部分环境可规避校验 Bug）：  
   CMD：`set DOCKER_BUILDKIT=0`  
   PowerShell：`$env:DOCKER_BUILDKIT="0"`  
   然后再执行 `docker build`。
4. 删除可能损坏的本地基础镜像后重拉：  
   `docker rmi eclipse-temurin:17-jre-jammy`（若提示被占用可先停相关容器）→ `docker pull eclipse-temurin:17-jre-jammy`
5. **重启 Docker Desktop**，仍失败则用 **GitHub Actions** 在云端构建（见 `docker-publish.yml`）。

### GitHub Actions / buildx 报 `permission_denied: write_package`

表示推送到 GHCR 时**没有「写入软件包」权限**。请按下表**从上到下**排查（任一步不对都会导致该错误）。

#### A. 本仓库（必做）

1. 打开：**Settings → Actions → General**
2. **Workflow permissions** 选 **Read and write permissions**（不要选 *Read repository contents and packages permissions* 等**只读**项）
3. 点 **Save**
4. 回到 **Actions**，对失败任务 **Re-run all jobs**

#### B. 组织 `zhi-java`（仓库在组织下时，常是根因）

需 **组织 Owner / 管理员** 操作：

1. **Organization → Settings → Actions → General**
2. **Workflow permissions** 选 **Read and write permissions**（若组织选成 **Read** 或未允许写入，仓库里怎么选都可能仍无法写 GHCR）
3. 若有 **Allow GitHub Actions to create and approve pull requests** 等与 Actions 相关的限制，按团队策略放行（与推送镜像最直接相关的是 **Workflow permissions 可写**）

#### C. 使用 PAT 绕过（A/B 仍无法改、或需立刻推送时）

1. 个人账号：**Settings → Developer settings → Personal access tokens** → 新建 **Classic** token，勾选 **`write:packages`**（及 **`read:packages`**）
2. 若组织启用了 **SAML SSO**：在令牌详情页对该 token 点 **Configure SSO** → **Authorize** 组织 `zhi-java`
3. 在**本仓库**：**Settings → Secrets and variables → Actions → New repository secret**  
   - Name：`GHCR_TOKEN`  
   - Value：粘贴 `ghp_` 开头的令牌  
4. 本仓库 **`docker-publish.yml` 已支持**：若存在 Secret **`GHCR_TOKEN`**，则用它登录 GHCR，而不再用 `GITHUB_TOKEN` 推送

#### D. 其它说明

- **Fork 的 PR**：`GITHUB_TOKEN` 不能往**上游**仓库的 GHCR 写；应在**源仓库**打 tag / 手动 Run workflow，或使用 PAT。
- 工作流里已声明 `permissions: packages: write`；组织若**强制**工作流只读，必须走 **B** 或 **C**。

### 无法拉取 `maven:*` 基础镜像

**当前默认 `Dockerfile` 已不再使用 Maven 镜像**：先在宿主机或 CI 执行 **`mvn -DskipTests package`**，再 **`docker build`**，仅拉取 **`eclipse-temurin:17-jre-jammy`**。若仍失败，请配置 Docker **registry-mirrors** 或使用 **GitHub Actions** 构建。

### `docker login ghcr.io` 报 `denied`

- **Classic PAT**：勾选 **`write:packages`**、**`read:packages`**（若需拉取私有包）；  
- **Fine-grained PAT**：在仓库或组织下授予 **Packages** 读写；  
- **用户名**：填 **GitHub 登录名**（与 PAT 所属账号一致），**不要**用邮箱；**密码**处粘贴 **PAT**（`ghp_` 开头），**不要**填 GitHub 网页登录密码。  
- **组织启用 SSO**：在 **Settings → Developer settings → 该令牌** 页面点 **Configure SSO** / **Authorize**，否则推送仍可能被拒。  
- 先 **`docker logout ghcr.io`** 再登录，避免旧错误凭证缓存。  
- 交互式粘贴异常时，在 CMD 中：  
  `set GITHUB_PAT=ghp_你的令牌`  
  `echo %GITHUB_PAT%| docker login ghcr.io -u 你的用户名 --password-stdin`  
- 已装 **GitHub CLI** 时：`gh auth login` 后  
  `gh auth token | docker login ghcr.io -u 用户名 --password-stdin`

### `docker pull` 报 `manifest unknown`

- **未指定 tag** 时默认拉 **`latest`**；若只推送过 **`0.1.0`** 等版本而从未推送 **`latest`**，会报此错。请：  
  `docker pull ghcr.io/OWNER/log4ai-server:0.1.0`  
  或使用本仓库 **`scripts/ghcr-build-push.bat`** / **CI**（已同时推送 **版本号** 与 **`latest`**）。  
- **从未成功 push** 到 GHCR，或包名/Owner 拼写错误。  
- **私有包**：未 **`docker login ghcr.io`** 或无权访问。  
- 在 **GitHub → Packages** 确认镜像已存在且 tag 列表中有目标 tag。

### 安全

**切勿**在聊天、截图、录屏中泄露 **PAT**。若已泄露，请立即在 GitHub **Settings → Developer settings → Tokens** **撤销**该令牌并新建。

---

## 13. 检查清单（发布前）

- [ ] `Dockerfile` 可在一台干净机器上 **`docker build` 成功**  
- [ ] 本地 **`docker run`** 能打开 `/log4ai/index.html` 且能读挂载日志（可选冒烟）  
- [ ] CI 使用 **Secret** 登录 registry，流水线日志中**无**令牌明文  
- [ ] README 或 Release Note 已更新 **镜像坐标、标签、运行示例**  
- [ ] （若公开）许可证与第三方依赖说明可查阅  

---

*文档版本：与 Spring-Boot-Log4AI 仓库同步维护；具体命令以实际 `Dockerfile`、分支策略为准。*
