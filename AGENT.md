# GoAPILib - AI Agent Context

## プロジェクト概要
MinecraftのPaper/SpigotサーバーでGoを使ってプラグインが書けるようにする。
Javaプラグインの代替として、Goコードだけでゲームロジックを実装できる状態が目標。
Skriptの代替に相当するが、言語はGoを使う。

## 2プラグイン構成

| Jar | 役割 | depend |
|---|---|---|
| `GoAPI.jar` | 汎用ブリッジライブラリ。Transport/Protocol/BukkitAPI操作 | なし |
| `GoLoader.jar` | Goバイナリの起動・死活監視・リロード管理 | GoAPI |

### サーバー起動フロー
```
サーバー起動
  -> GoAPI.jar  onLoad   : Paper/Spigot判定 (Class.forName)
  -> GoAPI.jar  onEnable : JsonRpcServer起動(port 8766) + BridgePlugin登録
  -> GoLoader.jar onEnable: Goバイナリ起動 -> /health ポーリング待機 -> 稼働
  -> [稼働中] Event発火 -> Go転送 -> GoがJava APIを逆コール
```

## プロトコル仕様 (JSON-RPC 2.0)

### Java -> Go (イベント通知) port:8765
```json
{"jsonrpc":"2.0","method":"event.PlayerJoinEvent","params":{"player_id":"uuid","_api":"bukkit"},"id":"evt-uuid"}
```

### Go -> Java (API逆コール) port:8766
```json
{"jsonrpc":"2.0","method":"api.player.sendMessage","params":{"player_id":"uuid","message":"hello"},"id":"api-uuid"}
```

### Go -> Java (コマンド登録)
```json
{"jsonrpc":"2.0","method":"api.command.register","params":{"name":"home","description":"帰宅","usage":"/home"},"id":"cmd-uuid"}
```

### Go -> Java (Scheduler)
```json
{"jsonrpc":"2.0","method":"api.scheduler.runLater","params":{"ticks":20,"callback_id":"cb-uuid"},"id":"sch-uuid"}
```
コールバック発火時: Java->Goに event.callback.{callback_id} が届く

## ポート
| ポート | 方向 | 用途 |
|---|---|---|
| 8765 | Java->Go | イベント通知 (GoのHTTPサーバー) |
| 8766 | Go->Java | API逆コール受信 (JavaのHttpServer) |

## GoAPI.jar ファイル構成
```
src/main/java/com/example/goapi/
├── GoAPI.java                  # publicインターフェース
├── GoAPIPlugin.java            # JavaPlugin本体
├── transport/
│   ├── JsonRpcMessage.java     # リクエスト/レスポンスPOJO
│   ├── JsonRpcClient.java      # Java->Go送信 (POST to 8765)
│   └── JsonRpcServer.java      # Go->Java受信 (HttpServer on 8766)
├── api/
│   ├── PlayerAPI.java          # api.player.* ハンドラ
│   ├── WorldAPI.java           # api.world.* ハンドラ
│   ├── SchedulerAPI.java       # api.scheduler.* ハンドラ
│   └── CommandRegistry.java    # api.command.* ハンドラ
└── bridge/
    ├── BridgePlugin.java       # [自動生成] scrape_goapi.py が出力 - 触らない
    └── EventDispatcher.java    # Event -> JSON-RPC変換
```

## GoLoader.jar ファイル構成
```
src/main/java/com/example/goloader/
├── GoLoaderPlugin.java         # JavaPlugin本体
├── ProcessManager.java         # Goプロセス起動/停止/再起動
├── Watchdog.java               # 死活監視
└── GoLoaderCommand.java        # /goloader reload|status|stop|build
src/main/resources/
├── plugin.yml                  # depend: [GoAPI]
└── config.yml
```

## GoAPI.java インターフェース
```java
public interface GoAPI {
    Map<String, Object> call(String method, Map<String, Object> params); // 同期
    void callAsync(String method, Map<String, Object> params);           // 非同期
    void onReverseCall(String method, RpcHandler handler);               // Go->Java登録
    boolean isAlive();
    boolean isPaper();
    EventDispatcher getDispatcher();
}
```

## api.player.* メソッド一覧
```
api.player.getName(player_id)                     -> {result: string}
api.player.getUUID(player_id)                     -> {result: string}
api.player.sendMessage(player_id, message)        -> {ok: true}
api.player.setHealth(player_id, value)            -> {ok: true}
api.player.getHealth(player_id)                   -> {result: double}
api.player.teleport(player_id, world, x, y, z)   -> {ok: true}
api.player.getLocation(player_id)                 -> {world, x, y, z}
api.player.kick(player_id, reason)                -> {ok: true}
api.player.giveItem(player_id, material, amount)  -> {ok: true}
api.player.setGameMode(player_id, mode)           -> {ok: true}
api.player.getGameMode(player_id)                 -> {result: string}
```

## api.world.* メソッド一覧
```
api.world.getBlock(world, x, y, z)                -> {material: string}
api.world.setBlock(world, x, y, z, material)      -> {ok: true}
api.world.getSpawn(world)                         -> {x, y, z}
api.world.setSpawn(world, x, y, z)               -> {ok: true}
api.world.getTime(world)                          -> {result: long}
api.world.setTime(world, time)                    -> {ok: true}
api.world.spawnEntity(world, x, y, z, type)       -> {entity_id: string}
```

## api.scheduler.* メソッド一覧
```
api.scheduler.runLater(ticks, callback_id)             -> {ok: true}
api.scheduler.runTimer(ticks, interval, callback_id)   -> {task_id: int}
api.scheduler.cancel(task_id)                          -> {ok: true}
```

## api.command.* メソッド一覧
```
api.command.register(name, description, usage)   -> {ok: true}
api.command.unregister(name)                     -> {ok: true}
```
実行時: event.command.{name} がGoに届く / params: {sender_id, args:[]}

## config.yml (GoLoader)
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

## 自動生成ファイル (触らない)
- GoAPI/src/main/java/com/example/goapi/bridge/BridgePlugin.java
- GoAPI/src/main/resources/plugin.yml
生成コマンド: python3 scrape_goapi.py

## ビルド手順
```bash
python3 scrape_goapi.py
cd GoAPI && ./gradlew jar
cd GoLoader && ./gradlew jar
cd go && go build -o ../plugins/GoLoader/server .
```

## ユーザーコード例 (Go)
```go
package main
import goapi "github.com/yourname/goapi-sdk"

func init() {
    goapi.On("PlayerJoinEvent", func(ctx *goapi.Context) {
        p := ctx.Player()
        p.SendMessage("ようこそ！")
    })
    goapi.Command("home", func(ctx *goapi.CommandContext) {
        ctx.Player().Teleport("world", 0, 64, 0)
    })
}
```

## IntelliJ IDEA でのセットアップ (GoLoader開発時)

GoLoader.jar は GoAPI.jar に依存するため、IntelliJ でビルドを通すには
GoAPI.jar をローカル参照させる必要がある。

### Gradle の場合 (推奨)
build.gradle に以下を記述:
```groovy
compileOnly files('../GoAPI/build/libs/GoAPI.jar')
```
-> IntelliJ は Gradle プロジェクトを自動認識するため追加設定不要。
-> GoAPI側で ./gradlew jar を一度実行しておくこと。

### Maven の場合
GoAPI.jar をローカルリポジトリにインストール:
```bash
mvn install:install-file \
  -Dfile=GoAPI/build/libs/GoAPI.jar \
  -DgroupId=com.example \
  -DartifactId=goapi \
  -Dversion=1.0.0 \
  -Dpackaging=jar
```
GoLoader/pom.xml に依存を追加:
```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>goapi</artifactId>
  <version>1.0.0</version>
  <scope>provided</scope>
</dependency>
```

### IntelliJ での手動追加 (Gradle/Maven を使わない場合)
1. File -> Project Structure -> Libraries
2. "+" -> Java -> GoAPI.jar を選択
3. Scope を "Provided" に設定 (実行時はサーバーが提供するため)
