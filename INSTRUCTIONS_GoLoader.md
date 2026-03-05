# GoLoader.jar 実装指示書

> AIエージェント向け。作業前に必ず AGENT.md を読むこと。
> このファイルは GoLoader.jar のみを対象にする。
> GoAPI.jar の実装は INSTRUCTIONS_GoAPI.md を参照。
>
> 前提: GoAPI.jar のビルドが完了していること (build/libs/GoAPI.jar が存在する)

## プロジェクト構造
```
GoLoader/
├── build.gradle                   # GoAPI.jarをcompileOnlyで参照
├── src/main/resources/
│   ├── plugin.yml                 # depend: [GoAPI]
│   └── config.yml
└── src/main/java/com/example/goloader/
    ├── GoLoaderPlugin.java
    ├── ProcessManager.java
    ├── Watchdog.java
    └── GoLoaderCommand.java
```

## GoAPI.jar との依存関係
GoLoader.jar は GoAPI.jar に依存する。
- コンパイル時: GoAPI.jar を compileOnly で参照 (build.gradleに記載)
- 実行時: サーバーに GoAPI.jar が先にロードされている必要がある
- plugin.yml の depend: [GoAPI] によりロード順序が保証される

---

## 指示1 - ProcessManager + Watchdog

### 対象ファイル (2ファイル)
- src/main/java/com/example/goloader/ProcessManager.java
- src/main/java/com/example/goloader/Watchdog.java

### ProcessManager.java
```
ProcessManager(JavaPlugin plugin, FileConfiguration config)
フィールド:
  Process goProcess
  JavaPlugin plugin
  FileConfiguration config
  String binaryPath  // config.getString("go-binary")
  String serverUrl   // config.getString("go-server-url")

void start()
  1. new File(binaryPath).exists() チェック -> なければ SEVERE ログで return
  2. config.getBoolean("build-on-start", false) が true なら:
       ProcessBuilder("go","build","-o",binaryPath,".") で go build 実行
       waitFor() で完了待機、exitCode != 0 なら SEVERE で return
  3. ProcessBuilder でバイナリ起動
     環境変数:
       GOAPI_ADDR      = "localhost:8765"
       GOAPI_JAVA_ADDR = "localhost:8766"
     redirectErrorStream(false)
  4. 別スレッドで stdout/stderr を読み "[GoProcess] " プレフィックス付きでlogに流す
  5. GET {serverUrl}/health が 200 返るまで 1秒ごとにポーリング (最大30秒)
     成功: INFO "Go server is ready"
     タイムアウト: WARNING "Go server did not respond within 30s"

void stop()
  if (goProcess == null || !goProcess.isAlive()) return
  goProcess.destroy()
  boolean exited = goProcess.waitFor(3, TimeUnit.SECONDS)
  if (!exited) goProcess.destroyForcibly()
  plugin.getLogger().info("Go server stopped")

void restart()
  stop()
  Thread.sleep(500)
  start()

boolean isAlive() -> goProcess != null && goProcess.isAlive()
long getPid()     -> goProcess != null ? goProcess.pid() : -1
```

### Watchdog.java
```
Watchdog(ProcessManager pm, JavaPlugin plugin, FileConfiguration config)
フィールド: int failCount = 0

void start()
  long interval = config.getLong("health-check-interval-ticks", 100L)
  plugin.getServer().getScheduler()
    .runTaskTimerAsynchronously(plugin, this::check, 100L, interval)

void check()
  try {
    HttpRequest req = GET {serverUrl}/health (timeout 2s)
    int status = send(req).statusCode()
    if (status == 200) { failCount = 0; return; }
  } catch (Exception e) { /* fall through */ }

  failCount++
  if (failCount == 1) plugin.getLogger().warning("Go server health check failed")
  if (failCount >= 3) plugin.getLogger().severe("Go server is down (3 consecutive failures)")
  if (config.getBoolean("auto-restart", true)) {
    plugin.getLogger().info("Auto-restarting Go server ...")
    pm.restart()
    failCount = 0
  }
```

---

## 指示2 - GoLoaderPlugin + GoLoaderCommand

### 対象ファイル (2ファイル)
- src/main/java/com/example/goloader/GoLoaderPlugin.java
- src/main/java/com/example/goloader/GoLoaderCommand.java

### GoLoaderPlugin.java
```
extends JavaPlugin
フィールド: ProcessManager pm, Watchdog watchdog

onEnable()
  saveDefaultConfig()

  // GoAPI 取得
  RegisteredServiceProvider<GoAPI> provider =
    getServer().getServicesManager().getRegistration(GoAPI.class)
  if (provider == null) {
    getLogger().severe("GoAPI plugin not found! Disabling GoLoader.")
    setEnabled(false)
    return
  }

  pm       = new ProcessManager(this, getConfig())
  watchdog = new Watchdog(pm, this, getConfig())
  pm.start()
  watchdog.start()
  getCommand("goloader").setExecutor(new GoLoaderCommand(pm))
  getLogger().info("GoLoader enabled")

onDisable()
  if (pm != null) pm.stop()
```

### GoLoaderCommand.java
```
implements TabExecutor
コンストラクタ: GoLoaderCommand(ProcessManager pm)

onCommand(sender, command, label, args)
  if args.length == 0 -> usage表示
  switch args[0]:
    "reload" -> pm.restart(); sender.sendMessage("Reloading Go server...")
    "status" ->
      if pm.isAlive():
        sender.sendMessage("Go server: RUNNING (pid=" + pm.getPid() + ")")
      else:
        sender.sendMessage("Go server: STOPPED")
    "stop"   -> pm.stop(); sender.sendMessage("Go server stopped")
    "build"  ->
      sender.sendMessage("Building Go binary...")
      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        // ProcessManager 内で go build -> restart
        pm.restart()
      })
    default  -> usage表示

onTabComplete -> ["reload", "status", "stop", "build"]
```

---

## 指示3 - build.gradle + plugin.yml + config.yml

### build.gradle
```groovy
plugins { id 'java' }
group = 'com.example'
version = '1.0.0'

repositories {
    mavenCentral()
    maven { url 'https://repo.papermc.io/repository/maven-public/' }
}
dependencies {
    // GoAPI.jar を参照 (実行時はサーバーが提供するのでcompileOnlyでよい)
    compileOnly files('../GoAPI/build/libs/GoAPI.jar')
    compileOnly 'io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT'
}
java { sourceCompatibility = JavaVersion.VERSION_17 }
jar { archiveFileName = 'GoLoader.jar' }
```

### src/main/resources/plugin.yml
```yaml
name: GoLoader
version: 1.0.0
main: com.example.goloader.GoLoaderPlugin
api-version: 1.20
depend: [GoAPI]
description: Go binary loader for GoAPI
authors: [generated]
commands:
  goloader:
    description: GoLoader management
    usage: /goloader <reload|status|stop|build>
    permission: goloader.admin
```

### src/main/resources/config.yml
```yaml
go-binary: plugins/GoLoader/server
go-server-url: http://localhost:8765
java-rpc-port: 8766
auto-restart: true
restart-delay-ticks: 60
health-check-interval-ticks: 100
build-on-start: false
go-source-dir: plugins/GoLoader/src
```

---

## ビルドコマンド
```bash
# GoAPI.jar が先にビルドされている前提
cd GoLoader
./gradlew jar
# -> build/libs/GoLoader.jar が生成される
```

## 完了チェックリスト
- [ ] 前提: GoAPI/build/libs/GoAPI.jar が存在する
- [ ] 指示1: ProcessManager / Watchdog
- [ ] 指示2: GoLoaderPlugin / GoLoaderCommand
- [ ] 指示3: build.gradle / plugin.yml / config.yml
- [ ] ./gradlew jar でビルド成功確認
- [ ] plugins/ に GoAPI.jar, GoLoader.jar を両方配置
- [ ] plugins/GoLoader/server (Goバイナリ) を配置
- [ ] サーバー起動確認
