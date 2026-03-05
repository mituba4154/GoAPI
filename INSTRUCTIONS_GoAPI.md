# GoAPI.jar 実装指示書

> AIエージェント向け。作業前に必ず AGENT.md を読むこと。
> このファイルは GoAPI.jar のみを対象にする。
> GoLoader.jar は INSTRUCTIONS_GoLoader.md を参照。

## プロジェクト構造
```
GoAPI/
├── build.gradle
├── src/main/resources/
│   ├── plugin.yml       [自動生成] scrape_goapi.py が出力
│   └── config.yml
└── src/main/java/com/example/goapi/
    ├── GoAPI.java
    ├── GoAPIPlugin.java
    ├── transport/
    │   ├── JsonRpcMessage.java
    │   ├── JsonRpcClient.java
    │   └── JsonRpcServer.java
    ├── api/
    │   ├── PlayerAPI.java
    │   ├── WorldAPI.java
    │   ├── SchedulerAPI.java
    │   └── CommandRegistry.java
    └── bridge/
        ├── BridgePlugin.java   [自動生成] scrape_goapi.py が出力 - 触らない
        └── EventDispatcher.java
```

---

## 指示1 - transport層

### 対象ファイル (3ファイル)
- src/main/java/com/example/goapi/transport/JsonRpcMessage.java
- src/main/java/com/example/goapi/transport/JsonRpcClient.java
- src/main/java/com/example/goapi/transport/JsonRpcServer.java

### JsonRpcMessage.java
```
フィールド: jsonrpc(="2.0"), method, params(Map<String,Object>), id(String), result, error
static JsonRpcMessage request(method, params)  // id = UUID.randomUUID().toString()
static JsonRpcMessage response(id, result)
static JsonRpcMessage error(id, code, message)
toJson() -> String        // Gson使用
static fromJson(String) -> JsonRpcMessage
```

### JsonRpcClient.java
```
JsonRpcClient(String url)  // "http://localhost:8765/rpc"
Map<String,Object> call(String method, Map<String,Object> params)
  - POST /rpc に JsonRpcMessage.request を送信
  - CompletableFuture.get(500, MILLISECONDS) で待機
  - result フィールドを Map にして返す
  - 失敗時: {ok:false, error:message} を返す (例外は握りつぶす)
void callAsync(String method, Map<String,Object> params)
  - fire-and-forget。レスポンスは捨てる
使用ライブラリ: java.net.http.HttpClient (Java 11+)
```

### JsonRpcServer.java
```
interface RpcHandler { Map<String,Object> handle(Map<String,Object> params); }

JsonRpcServer(int port)  // 8766
void start()
  - com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0)
  - POST /rpc を受け付け -> method でディスパッチ -> RpcHandler 呼び出し
  - JSON-RPC 2.0 response を返す
  - method 未登録なら error(-32601, "Method not found")
void register(String method, RpcHandler handler)
void stop()
```

---

## 指示2 - GoAPI interface + GoAPIPlugin

### 対象ファイル (2ファイル)
- src/main/java/com/example/goapi/GoAPI.java
- src/main/java/com/example/goapi/GoAPIPlugin.java

### GoAPI.java (interface)
```java
public interface GoAPI {
    Map<String, Object> call(String method, Map<String, Object> params); // 同期
    void callAsync(String method, Map<String, Object> params);           // 非同期
    void onReverseCall(String method, JsonRpcServer.RpcHandler handler); // Go->Java登録
    boolean isAlive();
    boolean isPaper();
    EventDispatcher getDispatcher();
}
```

### GoAPIPlugin.java
```
extends JavaPlugin, implements GoAPI
フィールド:
  JsonRpcClient client
  JsonRpcServer server
  boolean paperEnv
  EventDispatcher dispatcher

onLoad():
  try {
    Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
    paperEnv = true;
  } catch (ClassNotFoundException e) { paperEnv = false; }
  getLogger().info("Running on " + (paperEnv ? "Paper" : "Spigot/Bukkit"));

onEnable():
  saveDefaultConfig();
  client     = new JsonRpcClient(getConfig().getString("go-server-url", "http://localhost:8765"));
  server     = new JsonRpcServer(getConfig().getInt("java-rpc-port", 8766));
  dispatcher = new EventDispatcher(client);
  PlayerAPI.register(server, this);
  WorldAPI.register(server, this);
  SchedulerAPI.register(server, client, this);
  CommandRegistry.register(server, client, this);
  server.start();
  getServer().getPluginManager().registerEvents(new BridgePlugin(this), this);
  getServer().getServicesManager().register(GoAPI.class, this, this, ServicePriority.Normal);

onDisable():
  if (server != null) server.stop();
  getServer().getServicesManager().unregisterAll(this);

GoAPI実装: 各フィールドに委譲するだけ
```

---

## 指示3 - API層

### 対象ファイル (4ファイル)
- src/main/java/com/example/goapi/api/PlayerAPI.java
- src/main/java/com/example/goapi/api/WorldAPI.java
- src/main/java/com/example/goapi/api/SchedulerAPI.java
- src/main/java/com/example/goapi/api/CommandRegistry.java

### PlayerAPI.java
```
static void register(JsonRpcServer server, JavaPlugin plugin)
以下をserver.register()で登録:
  api.player.getName(player_id)                     -> {result: string}
  api.player.sendMessage(player_id, message)        -> {ok: true}
  api.player.setHealth(player_id, value)            -> {ok: true}
  api.player.getHealth(player_id)                   -> {result: double}
  api.player.teleport(player_id, world, x, y, z)   -> {ok: true}
  api.player.getLocation(player_id)                 -> {world, x, y, z}
  api.player.kick(player_id, reason)                -> {ok: true}
  api.player.giveItem(player_id, material, amount)  -> {ok: true}
  api.player.setGameMode(player_id, mode)           -> {ok: true}
  api.player.getGameMode(player_id)                 -> {result: string}
注意:
  player_id は UUID文字列。Bukkit.getPlayer(UUID.fromString(id)) で取得
  オフラインなら {ok:false, error:"player offline"}
  全操作は Bukkit.getScheduler().runTask() でメインスレッド実行
```

### WorldAPI.java
```
static void register(JsonRpcServer server, JavaPlugin plugin)
  api.world.getBlock(world, x, y, z)               -> {material: string}
  api.world.setBlock(world, x, y, z, material)     -> {ok: true}
  api.world.getSpawn(world)                        -> {x, y, z}
  api.world.setSpawn(world, x, y, z)              -> {ok: true}
  api.world.getTime(world)                         -> {result: long}
  api.world.setTime(world, time)                   -> {ok: true}
  api.world.spawnEntity(world, x, y, z, type)      -> {entity_id: string}
全操作は runTask() でメインスレッド実行
```

### SchedulerAPI.java
```
static void register(JsonRpcServer server, JsonRpcClient client, JavaPlugin plugin)
  api.scheduler.runLater(ticks, callback_id)
    -> BukkitScheduler.runTaskLater(plugin, () -> client.callAsync("event.callback."+id, {}), ticks)
    -> {ok: true}
  api.scheduler.runTimer(ticks, interval, callback_id)
    -> BukkitScheduler.runTaskTimer(...) -> {task_id: int}
  api.scheduler.cancel(task_id)
    -> Bukkit.getScheduler().cancelTask(task_id) -> {ok: true}
```

### CommandRegistry.java
```
static void register(JsonRpcServer server, JsonRpcClient client, JavaPlugin plugin)
  api.command.register(name, description, usage)
    -> Reflection で SimpleCommandMap を取得して PluginCommand を動的登録
    -> 実行時: client.callAsync("event.command."+name, {sender_id, args:[]})
    -> {ok: true}
  api.command.unregister(name) -> CommandMap から削除 -> {ok: true}

Reflection手順:
  Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
  f.setAccessible(true);
  SimpleCommandMap map = (SimpleCommandMap) f.get(Bukkit.getServer());
  map.register(plugin.getName(), command);
```

---

## 指示4 - EventDispatcher

### 対象ファイル (1ファイル)
- src/main/java/com/example/goapi/bridge/EventDispatcher.java

```
EventDispatcher(JsonRpcClient client)

void dispatch(String eventKey, Map<String,Object> data, Event event)
  1. populateCommonFields(data, event)
  2. client.callAsync(eventKey, data)  // 必ずcallAsync (高頻度イベント対策)

private void populateCommonFields(Map<String,Object> data, Event event)
  リフレクションで以下を試み、取得できたらdataに追加:
  - getPlayer() -> player_id = UUID文字列
  - getEntity() -> entity_id = UUID文字列
  - isCancelled() -> cancelled = boolean
  - getBlock() -> block_x, block_y, block_z, block_material
  取得失敗は try-catch で全て無視
```

---

## 指示5 - BridgePlugin.java 生成 (自動・人間が実行)

```bash
python3 scrape_goapi.py
# -> src/main/java/com/example/goapi/bridge/BridgePlugin.java 生成
# -> src/main/resources/plugin.yml 生成
```
このファイルは触らない。再生成したいときだけスクリプトを実行する。

---

## 指示6 - build.gradle + config.yml

### build.gradle
```groovy
plugins { id 'java' }
group = 'com.example'
version = '1.0.0'

repositories {
    mavenCentral()
    maven { url 'https://repo.papermc.io/repository/maven-public/' }
    maven { url 'https://hub.spigotmc.org/nexus/content/repositories/snapshots/' }
}
dependencies {
    compileOnly 'io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT'
    compileOnly 'org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT'
    implementation 'com.google.code.gson:gson:2.10.1'
}
java { sourceCompatibility = JavaVersion.VERSION_17 }
jar {
    archiveFileName = 'GoAPI.jar'
    from { configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
}
```

### src/main/resources/config.yml (デフォルト)
```yaml
go-server-url: http://localhost:8765
java-rpc-port: 8766
```

---

## ビルドコマンド
```bash
cd GoAPI
./gradlew jar
# -> build/libs/GoAPI.jar が生成される
```

## 完了チェックリスト
- [ ] 指示1: JsonRpcMessage / Client / Server
- [ ] 指示2: GoAPI.java / GoAPIPlugin.java
- [ ] 指示3: PlayerAPI / WorldAPI / SchedulerAPI / CommandRegistry
- [ ] 指示4: EventDispatcher
- [ ] 指示5: python3 scrape_goapi.py 実行
- [ ] 指示6: build.gradle / config.yml
- [ ] ./gradlew jar でビルド成功確認
