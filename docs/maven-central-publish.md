# JDK 8 兼容线发布到 Maven Central（Sonatype）

将 **`io.github.ml4497658:spring-boot-log4ai`** 发布到 **Maven Central** 前，须在 Central Portal 完成 **命名空间（已验证）**、本机 **GPG 签名** 与 **`~/.m2/settings.xml` 凭据**。

**说明**：Maven **`groupId`** 为 **`io.github.ml4497658`**（与 Sonatype 已验证命名空间一致）；源码 **Java 包名**仍为 **`com.log4ai.*`**，业务工程只需改 `pom.xml` 里的 `groupId`，**无需**改 `import`。

---

## 1. 命名空间（groupId）与账号

1. 打开 **[Central Portal](https://central.sonatype.com/)** 登录发布者账号。  
2. 本仓库 **`groupId`**：**`io.github.ml4497658`**（GitHub 用户命名空间，状态 **Verified** 后方可 `deploy`）。  
3. 若使用 **组织**命名空间（如 `io.github.zhi-java`）时出现 **「Namespace exists」**：表示该字符串已被登记（可能为旧 **OSSRH**、其他账号或本组织他人）。请到 Portal **View Namespaces** 查看是否已在本人账号下；否则联系 [central-support@sonatype.com](mailto:central-support@sonatype.com) 并说明 GitHub 组织归属。个人用户通常使用 **`io.github.<你的 GitHub 用户名>`** 最直接。  
4. 命名空间 **Verified** 后，即可按下文使用 **`mvn deploy`** / Portal **Deployments** 发布（流程以 [Sonatype 发布指南](https://central.sonatype.org/publish/publish-guide/) 为准）。

---

## 2. 发布前仓库要求

| 项 | 说明 |
|----|------|
| **版本** | JDK 8 兼容线正式版建议使用独立前缀版本，如 `jdk8-0.2.0`；中央库正式版不能用 `-SNAPSHOT`。发布前可执行例如：`mvn versions:set -DnewVersion=jdk8-0.2.0 -DgenerateBackupPoms=false`，提交后再部署。 |
| **LICENSE** | 根目录应有 **`LICENSE`** 文件，且与 `pom.xml` 中 `<licenses>` 一致（当前为 **Apache-2.0**）。 |
| **GPG** | 生成密钥、`gpg --export` 公钥上传到公钥服务器；Maven 用 **`maven-gpg-plugin`** 签名（见下述 profile）。 |
| **`settings.xml`** | 配置 **`<server>`** 的 `id` 与 POM 里 **`distributionManagement`** 一致，用户名/密码为 Sonatype 令牌（**不要**提交到仓库）。 |

### GPG：新生成密钥后的操作（清单）

以你本机生成的 **RSA 3072**、指纹末段 **`F2ED00C2`**（完整指纹见 `gpg -K --with-subkey-fingerprint`）为例：

1. **撤销证书**  
   路径类似：`%APPDATA%\gnupg\openpgp-revocs.d\736B881D18B8D222CA8EC174E6936640F2ED00C2.rev`  
   **单独备份到安全位置（离线/U 盘）**，不要提交到 Git。将来私钥泄露时用它 **吊销** 公钥。丢失私钥时也可用于标记密钥作废。

2. **把公钥发到密钥服务器**（Maven Central 验证签名时会从公钥服务器拉取）：

   ```bash
   gpg --keyserver keys.openpgp.org --send-keys F2ED00C2
   ```

   若失败可换 `keyserver.ubuntu.com`。也可用网页 **https://keys.openpgp.org/** 上传 **ASCII 公钥**（`gpg --armor --export F2ED00C2` 的输出）。

3. **让 Maven 固定使用这把密钥**（多把密钥时建议显式指定）。在 **`%USERPROFILE%\.m2\settings.xml`** 里增加 **`activeByDefault` 的 profile**（与下面第 4 节合并到同一 `<settings>` 即可）：

   ```xml
   <settings>
     <profiles>
       <profile>
         <id>gpg-signing</id>
         <properties>
           <gpg.keyname>F2ED00C2</gpg.keyname>
         </properties>
       </profile>
     </profiles>
     <activeProfiles>
       <activeProfile>gpg-signing</activeProfile>
     </activeProfiles>
   </settings>
   ```

   **`gpg.keyname`** 也可用 **完整 40 位指纹**（无空格），与 `gpg -K` 中一致即可。

4. **签名时输入口令**  
   执行 `mvn -Prelease-central verify` 或 `deploy` 时，`maven-gpg-plugin` 会调用 GPG 解锁私钥：输入你生成密钥时设的 **passphrase**。  
   若希望少输几次：在本机启用 **gpg-agent**（Gpg4win 默认会带），同一会话内会记住一段时间。

5. **切勿**把 **私钥**、**passphrase**、**Sonatype Token** 写入仓库；CI 里用 **GitHub Actions Secrets** 注入。

6. **试签一次**（不要求已配置 OSSRH，仅验证 GPG 与 Maven）：

   ```bash
   cd /path/to/Spring-Boot-Log4AI
   mvn -Prelease-central -DskipTests package
   ```

   若卡在签名步骤，检查 `gpg.keyname` 是否与 `gpg -K` 中默认或指定密钥一致。

> 提示：生成密钥时若出现 **entropy（熵）** 不足提示，属正常现象；多动鼠标、键盘、读写磁盘即可，完成后不必重复操作。

---

## 3. `~/.m2/settings.xml` 示例（勿提交）

将 **`YOUR_SONATYPE_TOKEN_USER`** / **`YOUR_SONATYPE_TOKEN_PASSWORD`** 换成在 Central Portal 生成的 **User Token**（登录后打开 **[https://central.sonatype.com/usertoken](https://central.sonatype.com/usertoken)** → **Generate User Token**，见 [官方说明](https://central.sonatype.org/publish/generate-portal-token/)）。**弹窗关闭后无法再次查看同一组口令，请当场保存；丢失只能重新生成。**

**`server` 的 `id` 必须与 POM 完全一致（区分大小写），本仓库为 `central`**，对应 `central-publishing-maven-plugin` 的 `publishingServerId` 与快照仓库 `distributionManagement` 的 id。**不能使用**：Sonatype 网站登录密码、GitHub 密码、旧版 OSSRH/JIRA 密码；必须是 **User Token 弹窗里给出的那一对 username + password**。

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_SONATYPE_TOKEN_USER</username>
      <password>YOUR_SONATYPE_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

GPG 若需密码，可用 **`gpg-agent`**，或在本机交互输入（CI 则用加密 Secret）。

---

## 4. 本仓库 POM：`release-central` Profile

根 `pom.xml` 的 **`release-central`** profile（默认**不启用**）包含：

- **`central-publishing-maven-plugin`**（`extensions=true`）：与 **Central Portal** 对接；`mvn deploy` 时上传 **bundle**（正式版在 Portal 的 **Deployments** 里校验 / 发布）。详见 [Publish Portal Maven](https://central.sonatype.org/publish/publish-portal-maven/)。  
- **`spring-boot-maven-plugin`**：本 profile 内 **`skip=true`**，**不**生成 / 上传 **`standalone` fat jar**，减轻 bundle 体积与上传中断风险；依赖方只需普通 **`spring-boot-log4ai-*.jar`**。  
- **`distributionManagement.snapshotRepository`**：**`https://central.sonatype.com/repository/maven-snapshots/`**，`id` 为 **`central`**（**勿**再使用 `s01.oss.sonatype.org/.../snapshots/`，仅 Portal 账号时易出现 **405 Not Allowed**）。  
- **`maven-source-plugin`** / **`maven-javadoc-plugin`** / **`maven-gpg-plugin`**：满足 Central 对附件与签名的常规要求。

### 发布 `-SNAPSHOT` 前（必做）

在 [Namespaces](https://central.sonatype.com/publishing/namespaces) 对 **`io.github.ml4497658`** 点击菜单 **Enable SNAPSHOTs** 并确认；未开启时快照上传会失败。消费快照的工程需加仓库 **`https://central.sonatype.com/repository/maven-snapshots/`**（见 [Publish Portal Snapshots](https://central.sonatype.org/publish/publish-portal-snapshots/)）。

**部署快照**：

```bash
mvn -Prelease-central clean deploy
```

**发布正式版**（须先把版本改为非 `SNAPSHOT`）：

```bash
mvn -Prelease-central clean deploy
```

之后在 **[central.sonatype.com → Publishing → Deployments](https://central.sonatype.com/publishing/deployments)** 查看校验结果，必要时点击 **Publish**（若未在插件中开启 `autoPublish`）。**不再使用** 旧版 OSSRH 网页里 **Staging Repositories** 的 Close/Release（除非你的账号仍走 legacy 流程）。

---

## 5. 常见错误

### 5.1 `401 Unauthorized`（`central.sonatype.com/repository/maven-snapshots/`）

说明 Maven **没有带上有效凭据**，或 Token 与当前 Portal 账号不匹配。

| 检查项 | 说明 |
|--------|------|
| **`settings.xml` 路径** | 使用当前用户目录下的 **`%USERPROFILE%\.m2\settings.xml`**（Windows）或 **`~/.m2/settings.xml`**。勿把含密码的 `settings.xml` 放进 Git；若用 `-s` 指定文件，确认路径正确。 |
| **`<server><id>`** | 必须与 POM 里一致，为 **`central`**（不是 `ossrh`、`sonatype` 等）。 |
| **用户名 / 密码来源** | 必须来自 **[usertoken](https://central.sonatype.com/usertoken)** 生成的 **User Token** 两行值；**不要**填邮箱或网站登录密码。 |
| **Token 是否过期** | 生成时若设了过期时间，过期后需重新 **Generate User Token** 并更新 `settings.xml`。 |
| **发布账号与命名空间** | 生成 Token 时登录的 Portal 账号，须对 **`io.github.ml4497658`** 有发布权限（该命名空间已 Verified 且已 **Enable SNAPSHOTs**）。换电脑或换账号登录会需要对应账号的 Token。 |
| **密码里的特殊字符** | 若 Token 含 `&`、`<`、`>` 等，在 XML 中需转义（`&amp;` 等）或使用 [Maven 密码加密](https://maven.apache.org/guides/mini/guide-encryption.html)。 |
| **`mirror` 干扰** | `settings.xml` 里若有 `<mirror><mirrorOf>*</mirrorOf>`，可能把上传请求转到无凭据的镜像；可临时去掉 mirror 或为 `central.sonatype.com` 排除镜像后重试。 |

自检命令（**不要**把 `-X` 完整日志发给别人，其中可能含认证头信息）：

```bash
mvn -Prelease-central help:effective-settings
```

确认输出的 `<servers>` 里存在 **`id=central`** 且用户名非空（密码通常被掩码）。

### 5.2 `403 Forbidden`（`central.sonatype.com/repository/maven-snapshots/`）

已通过认证（不再是 401），但服务器**拒绝写入**，常见于 **未开启快照发布** 或 **Token 所属账号对该命名空间无发布权**。

| 检查项 | 说明 |
|--------|------|
| **Enable SNAPSHOTs（最常见）** | 打开 **[Namespaces](https://central.sonatype.com/publishing/namespaces)**，找到 **`io.github.ml4497658`**，点右侧菜单 → **Enable SNAPSHOTs** → 确认。成功后命名空间上会显示 **SNAPSHOT** 相关标识。未开启时，对 `maven-snapshots` 的上传会 **403**。详见 [Publish Portal Snapshots](https://central.sonatype.org/publish/publish-portal-snapshots/)。 |
| **Token 与命名空间是否同一发布者** | User Token 必须在 **拥有该命名空间发布权限** 的 Portal 账号下生成。若命名空间在账号 A 下 Verified，却在账号 B 下生成 Token，可能出现 403。 |
| **正式版 immutable 403** | 若错误发生在**非 SNAPSHOT** 的 release 流程，可能是重复发布已存在的版本（Central 不允许覆盖）。快照路径下的 403 一般仍以「未 Enable SNAPSHOTs」优先排查。 |

处理完 **Enable SNAPSHOTs** 后，等待约 **1～2 分钟** 再执行 `mvn -Prelease-central clean deploy`。

### 5.3 `405 Not Allowed`（旧 s01 快照地址）

| 现象 | 原因与处理 |
|------|------------|
| `deploy` 到 **`s01.oss.sonatype.org/.../snapshots/`** 报 **405** | 当前多为 **Central Portal** 发布者：应使用本仓库 POM 中的 **`https://central.sonatype.com/repository/maven-snapshots/`**，且 **`settings.xml` 中 `server` 的 `id` 为 `central`**。 |
| 快照仍失败 | 在 Portal 上对命名空间执行 **Enable SNAPSHOTs**；确认 Token 有效、账号有该 namespace 发布权限。 |
| 仅 legacy OSSRH 仍可用 | 若 Sonatype 仍要求你走 **s01 Staging**，以其邮件/工单为准，与本仓库默认 POM 可能不一致，需单独改 URL。 |

### 5.4 `Connection reset by peer`（上传 `central-bundle.zip` 失败）

多为 **上传时间长、网络不稳定**（跨境、代理、防火墙中断 TCP）。处理方式：

| 做法 | 说明 |
|------|------|
| **已在本仓库 POM 中缩小 bundle** | **`release-central`** profile 内对 **`spring-boot-maven-plugin`** 设置了 **`skip`**，不再把 **`spring-boot-log4ai-*-standalone.jar`**（fat jar）打进 Central 包，仅上传普通库 jar + sources + javadoc + 签名，体积通常小一个数量级。可执行 fat 包仍用 **`mvn package`（不要加 `-Prelease-central`）** 生成。 |
| **换网络 / 关代理 / 用手机热点** | 再执行 `mvn -Prelease-central clean deploy`。 |
| **只打 zip、浏览器上传** | `mvn -Prelease-central -DskipTests -DskipPublishing=true clean deploy`（插件挂在 **deploy** 阶段），在 **`target/central-publishing/central-bundle.zip`** 生成后，到 [Portal Deployments](https://central.sonatype.com/publishing/deployments) **手动上传**（以 Portal 当前界面为准）。 |

---

## 6. CI 中发布（可选）

在 **GitHub Actions** 中发布时：将 **GPG 私钥**、**Sonatype Token** 存为 **Secrets**，步骤中导入 GPG 并执行 `mvn -Prelease-central deploy`。**切勿**把私钥或 Token 写入仓库文件。

---

## 7. 与 Docker / GHCR 的关系

- **Maven Central**：给其他项目 **`pom.xml` 依赖** 用。  
- **GHCR 镜像**：给 **`docker pull`** 独立部署用。  
两者可并行维护，版本号建议对齐或遵循各自发版节奏。

---

*文档随仓库维护；Sonatype 流程变更时请以 [central.sonatype.org](https://central.sonatype.org/) 为准。*
