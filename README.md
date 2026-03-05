# GoAPI

**GoAPI** は Minecraft（Paper / Spigot）サーバーと Go プログラムを JSON-RPC 2.0 でつなぐブリッジプラグインです。  
Go コードだけでイベント処理・コマンド登録・ワールド操作などを実装できます。

---

## 目次

1. [できること](#できること)
2. [動作要件](#動作要件)
3. [リポジトリ構成](#リポジトリ構成)
4. [ビルド手順](#ビルド手順)
5. [サーバーへの配置](#サーバーへの配置)
6. [設定ファイル](#設定ファイル)
7. [通信仕様](#通信仕様)
8. [提供 API 一覧](#提供-api-一覧)
9. [起動確認](#起動確認)
10. [よくある問題](#よくある問題)
11. [開発者向け補足](#開発者向け補足)

---

## できること

| 方向 | 内容 |
|---|---|
| Java → Go | プレイヤー参加・ブロック破壊などのイベントを Go に転送 |
| Go → Java | プレイヤー操作・ワールド操作・コマンド登録などを Java 側で実行 |
| Go → Java | スケジューラ（遅延/タイマー実行）の登録 |

---

## 動作要件

- Java 21 以上
- Paper または Spigot 1.21.8 系
- Python 3（自動生成スクリプト `scrape_goapi.py` 実行時のみ）
- **GoLoader プラグインと組み合わせて使用する**（Go プロセスの管理は GoLoader 側）

---

## リポジトリ構成

```
GoAPI/          ← リポジトリルート（git管理はここ）
├── scrape_goapi.py          # BridgePlugin / plugin.yml 自動生成スクリプト
├── events.json              # イベント定義ファイル
└── GoAPI/                   ← Gradle プロジェクト本体
    ├── build.gradle
    └── src/main/
        ├── java/com/example/goapi/
        │   ├── GoAPI.java               # publicインターフェース
        │   ├── GoAPIPlugin.java         # プラグイン本体
        │   ├── transport/               # JSON-RPC 通信層
        │   ├── api/                     # Bukkit API ハンドラ群
        │   └── bridge/                  # イベント転送（自動生成あり）
        └── resources/
            ├── config.yml               # デフォルト設定
            └── plugin.yml               # ※自動生成 - 触らない
```

> **注意**: `bridge/BridgePlugin.java` と `plugin.yml` は `scrape_goapi.py` が自動生成します。手動編集しないでください。

---

## ビルド手順

```bash
# 1. 自動生成ファイルを更新（初回・イベント定義変更時）
python3 scrape_goapi.py

# 2. Jar をビルド
cd GoAPI
./gradlew --no-daemon jar
```

生成される jar:

```
GoAPI/build/libs/GoAPI.jar
```

---

## サーバーへの配置

Minecraft サーバーのルートディレクトリを `server-root/` とします。

```
server-root/
└── plugins/
    ├── GoAPI.jar       ← ここに配置
    └── GoLoader.jar    ← GoLoader 側でビルド（別リポジトリ）
```

GoLoader の配置手順は [GoLoader リポジトリ](https://github.com/mituba4154/GoLoader) を参照してください。

---

## 設定ファイル

初回起動時に `plugins/GoAPI/config.yml` が自動生成されます。

```yaml
go-server-url: http://localhost:8765  # Go サーバーのアドレス（イベント通知先）
java-rpc-port: 8766                   # Go から Java API を呼ぶときのポート
```

| キー | 説明 | デフォルト |
|---|---|---|
| `go-server-url` | Go 側の HTTP サーバー URL（イベント通知先） | `http://localhost:8765` |
| `java-rpc-port` | Java 側が Listen するポート（Go → Java API コール受付） | `8766` |

---

## 通信仕様

GoAPI は **JSON-RPC 2.0** を使って Go と通信します。

### ポートと方向

| ポート | 方向 | 用途 |
|---|---|---|
| `8765` | Java → Go | イベント通知（Go 側の `/rpc` へ POST） |
| `8766` | Go → Java | API 逆コール受付（Java 側の HTTP サーバー） |

### メッセージ例

```json
// Java → Go: イベント通知
{"jsonrpc":"2.0","method":"event.PlayerJoinEvent","params":{"player_id":"<UUID>"},"id":"evt-1"}

// Go → Java: API 呼び出し
{"jsonrpc":"2.0","method":"api.player.sendMessage","params":{"player_id":"<UUID>","message":"こんにちは"},"id":"api-1"}
```

---

## 提供 API 一覧

### api.player.*

| メソッド | パラメータ | 戻り値 |
|---|---|---|
| `api.player.getName` | `player_id` | `{result: string}` |
| `api.player.getUUID` | `player_id` | `{result: string}` |
| `api.player.sendMessage` | `player_id, message` | `{ok: true}` |
| `api.player.setHealth` | `player_id, value` | `{ok: true}` |
| `api.player.getHealth` | `player_id` | `{result: double}` |
| `api.player.teleport` | `player_id, world, x, y, z` | `{ok: true}` |
| `api.player.getLocation` | `player_id` | `{world, x, y, z}` |
| `api.player.kick` | `player_id, reason` | `{ok: true}` |
| `api.player.giveItem` | `player_id, material, amount` | `{ok: true}` |
| `api.player.setGameMode` | `player_id, mode` | `{ok: true}` |
| `api.player.getGameMode` | `player_id` | `{result: string}` |

### api.world.*

| メソッド | パラメータ | 戻り値 |
|---|---|---|
| `api.world.getBlock` | `world, x, y, z` | `{material: string}` |
| `api.world.setBlock` | `world, x, y, z, material` | `{ok: true}` |
| `api.world.getSpawn` | `world` | `{x, y, z}` |
| `api.world.setSpawn` | `world, x, y, z` | `{ok: true}` |
| `api.world.getTime` | `world` | `{result: long}` |
| `api.world.setTime` | `world, time` | `{ok: true}` |
| `api.world.spawnEntity` | `world, x, y, z, type` | `{entity_id: string}` |

### api.command.*

| メソッド | パラメータ | 戻り値 |
|---|---|---|
| `api.command.register` | `name, description, usage` | `{ok: true}` |
| `api.command.unregister` | `name` | `{ok: true}` |

コマンド実行時: `event.command.{name}` イベントが Go に届く（`params: {sender_id, args:[]}`）

### api.scheduler.*

| メソッド | パラメータ | 戻り値 |
|---|---|---|
| `api.scheduler.runLater` | `ticks, callback_id` | `{ok: true}` |
| `api.scheduler.runTimer` | `ticks, interval, callback_id` | `{task_id: int}` |
| `api.scheduler.cancel` | `task_id` | `{ok: true}` |

コールバック発火時: `event.callback.{callback_id}` が Go に届く

---

## 起動確認

サーバー起動後、以下のログが出れば正常です。

```
[GoAPI] Running on Paper        ← Paper/Spigotの判定結果
[GoAPI] GoAPI enabled
```

GoLoader も起動している場合はさらに:

```
[GoLoader] Go server ready (pid=12345)
```

---

## よくある問題

### GoAPI が起動しない / プラグインが無効化される

- `plugins/GoAPI.jar` の配置場所を確認してください
- Java バージョンを確認してください（Java 21 必須）

### イベントが Go に届かない

- GoLoader の Go プロセスが起動しているか確認（`/goloader status`）
- `go-server-url` が正しいか確認（`plugins/GoAPI/config.yml`）

### `scrape_goapi.py` 実行後にビルドエラーが出る

- 自動生成された `plugin.yml` や `BridgePlugin.java` を手動編集していないか確認
- Python 3 のバージョンを確認

---

## 開発者向け補足

### 自動生成ファイルの再生成

イベント定義（`events.json`）を更新した場合は必ず再実行:

```bash
python3 scrape_goapi.py
cd GoAPI && ./gradlew --no-daemon jar
```

### GoLoader との依存関係

GoLoader は GoAPI の `GoAPI.class` を `compileOnly` で参照します。  
GoLoader をビルドする前に `GoAPI/build/libs/GoAPI.jar` が存在している必要があります。

### サービスとして他プラグインから使う

GoAPI は Bukkit ServiceManager に登録されます。他の Java プラグインから以下で取得可能:

```java
RegisteredServiceProvider<GoAPI> provider =
    getServer().getServicesManager().getRegistration(GoAPI.class);
GoAPI goapi = provider.getProvider();
```
