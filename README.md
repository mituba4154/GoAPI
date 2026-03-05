# GoAPI

> **Minecraft(Paper/Spigot) サーバーで Go コードを動かすための JSON-RPC ブリッジプラグイン**

GoAPI は Java プラグインとして動作し、Minecraft のイベントや API を **Go プロセスから透過的に呼び出せる** 仕組みを提供します。  
これにより、Java を書かなくても **Go だけでプラグインロジックを実装** することができます。

---

## 目次

1. [仕組みの概要](#1-仕組みの概要)
2. [必要環境](#2-必要環境)
3. [ディレクトリ構成](#3-ディレクトリ構成)
4. [ビルド手順](#4-ビルド手順)
5. [Minecraft サーバーへの導入](#5-minecraft-サーバーへの導入)
6. [設定ファイル詳細](#6-設定ファイル詳細)
7. [通信プロトコルとポート](#7-通信プロトコルとポート)
8. [提供 API 一覧](#8-提供-api-一覧)
9. [起動ログの見方](#9-起動ログの見方)
10. [BridgePlugin の自動生成](#10-bridgeplugin-の自動生成)
11. [トラブルシューティング](#11-トラブルシューティング)

---

## 1. 仕組みの概要

```
Minecraft サーバー
  ┌────────────────────────────────────────┐
  │  GoAPI.jar                             │
  │    ・Minecraft イベントを受け取る        │
  │    ・JSON-RPC でイベントを Go へ送信     │  ─── port 8765 ──▶  Go プロセス
  │    ・Go からの API 呼び出しを受け付ける   │  ◀── port 8766 ───  (GoLoader が管理)
  └────────────────────────────────────────┘
```

| 方向 | ポート | 内容 |
|------|--------|------|
| Java → Go | 8765 | イベント通知（PlayerJoinEvent など） |
| Go → Java | 8766 | API 逆コール（sendMessage, teleport など） |

GoAPI 単体では動作しません。**Go プロセス自体の管理は GoLoader が行います。**  
GoLoader のセットアップは [GoLoader リポジトリ](https://github.com/mituba4154/GoLoader) を参照してください。

---

## 2. 必要環境

| ソフトウェア | バージョン |
|-------------|-----------|
| Java        | **21 以上** |
| Paper / Spigot | 1.21.8 系 |
| Python      | 3.x（`scrape_goapi.py` 実行時のみ） |

---

## 3. ディレクトリ構成

```
(リポジトリルート)/
├── scrape_goapi.py          # BridgePlugin.java / plugin.yml を自動生成するスクリプト
├── events.json              # 登録するイベント一覧（scrape_goapi.py が読み込む）
└── GoAPI/                   # Gradle プロジェクト本体
    ├── build.gradle
    ├── gradlew
    └── src/main/
        ├── java/com/example/goapi/
        │   ├── GoAPI.java              # 公開インターフェース
        │   ├── GoAPIPlugin.java        # プラグイン本体
        │   ├── transport/
        │   │   ├── JsonRpcMessage.java # JSON-RPC メッセージ POJO
        │   │   ├── JsonRpcClient.java  # Java→Go 送信クライアント
        │   │   └── JsonRpcServer.java  # Go→Java 受信サーバー
        │   ├── api/
        │   │   ├── PlayerAPI.java      # api.player.* ハンドラ
        │   │   ├── WorldAPI.java       # api.world.* ハンドラ
        │   │   ├── SchedulerAPI.java   # api.scheduler.* ハンドラ
        │   │   └── CommandRegistry.java# api.command.* ハンドラ
        │   └── bridge/
        │       ├── BridgePlugin.java   # ⚠ 自動生成 - 直接編集禁止
        │       └── EventDispatcher.java
        └── resources/
            ├── plugin.yml              # ⚠ 自動生成 - 直接編集禁止
            └── config.yml
```

---

## 4. ビルド手順

### ステップ 1: イベント登録ファイルの生成（初回 or イベント変更時のみ）

```bash
# リポジトリルートで実行
python3 scrape_goapi.py
```

これにより以下が生成されます：
- `GoAPI/src/main/java/com/example/goapi/bridge/BridgePlugin.java`
- `GoAPI/src/main/resources/plugin.yml`

### ステップ 2: Jar のビルド

```bash
cd GoAPI
./gradlew --no-daemon jar
```

**成功すると** `GoAPI/build/libs/GoAPI.jar` が生成されます。

---

## 5. Minecraft サーバーへの導入

### 配置場所

```
minecraft-server/
└── plugins/
    ├── GoAPI.jar      ← こちらをビルドして配置
    └── GoLoader.jar   ← GoLoader リポジトリからビルドして配置
```

### 起動順序

`GoAPI` は `GoLoader` より**先に**起動する必要があります。  
`GoLoader` の `plugin.yml` に `depend: [GoAPI]` が設定されているため、Minecraft が自動的に順序を制御します。手動での並べ替えは不要です。

---

## 6. 設定ファイル詳細

初回サーバー起動後、`plugins/GoAPI/config.yml` が自動生成されます。

```yaml
# Go サーバーの URL（GoLoader が起動する go-server が待ち受けるアドレス）
go-server-url: http://localhost:8765

# Go → Java API 逆コールを受け付けるポート
java-rpc-port: 8766
```

| キー | デフォルト | 説明 |
|------|-----------|------|
| `go-server-url` | `http://localhost:8765` | イベント送信先の Go HTTP サーバー URL |
| `java-rpc-port` | `8766` | Go からの API 呼び出しを受け付けるポート番号 |

> ポートを変更した場合は、GoLoader 側の `config.yml` も同じ値に揃える必要があります。

---

## 7. 通信プロトコルとポート

通信は **JSON-RPC 2.0** を使用しています。

### Java → Go（イベント通知）

```json
{
  "jsonrpc": "2.0",
  "method": "event.PlayerJoinEvent",
  "params": {
    "player_id": "550e8400-e29b-41d4-a716-446655440000",
    "_api": "bukkit"
  },
  "id": "evt-uuid"
}
```

### Go → Java（API 逆コール）

```json
{
  "jsonrpc": "2.0",
  "method": "api.player.sendMessage",
  "params": {
    "player_id": "550e8400-e29b-41d4-a716-446655440000",
    "message": "ようこそ！"
  },
  "id": "api-uuid"
}
```

---

## 8. 提供 API 一覧

### api.player.*（プレイヤー操作）

| メソッド | パラメータ | 戻り値 | 説明 |
|---------|-----------|--------|------|
| `api.player.getName` | `player_id` | `{result: string}` | プレイヤー名を取得 |
| `api.player.getUUID` | `player_id` | `{result: string}` | UUID を取得 |
| `api.player.sendMessage` | `player_id`, `message` | `{ok: true}` | メッセージ送信 |
| `api.player.setHealth` | `player_id`, `value` | `{ok: true}` | 体力を設定 |
| `api.player.getHealth` | `player_id` | `{result: double}` | 体力を取得 |
| `api.player.teleport` | `player_id`, `world`, `x`, `y`, `z` | `{ok: true}` | テレポート |
| `api.player.getLocation` | `player_id` | `{world, x, y, z}` | 現在地を取得 |
| `api.player.kick` | `player_id`, `reason` | `{ok: true}` | キック |
| `api.player.giveItem` | `player_id`, `material`, `amount` | `{ok: true}` | アイテムを付与 |
| `api.player.setGameMode` | `player_id`, `mode` | `{ok: true}` | ゲームモードを設定（`SURVIVAL`/`CREATIVE`/`ADVENTURE`/`SPECTATOR`） |
| `api.player.getGameMode` | `player_id` | `{result: string}` | ゲームモードを取得 |

> プレイヤーがオフラインの場合は `{ok: false, error: "player offline"}` が返ります。

### api.world.*（ワールド操作）

| メソッド | パラメータ | 戻り値 | 説明 |
|---------|-----------|--------|------|
| `api.world.getBlock` | `world`, `x`, `y`, `z` | `{material: string}` | ブロックの種類を取得 |
| `api.world.setBlock` | `world`, `x`, `y`, `z`, `material` | `{ok: true}` | ブロックを設置 |
| `api.world.getSpawn` | `world` | `{x, y, z}` | スポーン地点を取得 |
| `api.world.setSpawn` | `world`, `x`, `y`, `z` | `{ok: true}` | スポーン地点を設定 |
| `api.world.getTime` | `world` | `{result: long}` | ワールド時刻を取得 |
| `api.world.setTime` | `world`, `time` | `{ok: true}` | ワールド時刻を設定 |
| `api.world.spawnEntity` | `world`, `x`, `y`, `z`, `type` | `{entity_id: string}` | エンティティをスポーン |

### api.scheduler.*（タスクスケジューラ）

| メソッド | パラメータ | 戻り値 | 説明 |
|---------|-----------|--------|------|
| `api.scheduler.runLater` | `ticks`, `callback_id` | `{ok: true}` | 指定 tick 後にコールバック発火 |
| `api.scheduler.runTimer` | `ticks`, `interval`, `callback_id` | `{task_id: int}` | 定期的にコールバック発火 |
| `api.scheduler.cancel` | `task_id` | `{ok: true}` | タスクをキャンセル |

コールバック発火時: Java → Go に `event.callback.{callback_id}` が届きます。

### api.command.*（コマンド管理）

| メソッド | パラメータ | 戻り値 | 説明 |
|---------|-----------|--------|------|
| `api.command.register` | `name`, `description`, `usage` | `{ok: true}` | コマンドを動的登録 |
| `api.command.unregister` | `name` | `{ok: true}` | コマンドを削除 |

コマンド実行時: Java → Go に `event.command.{name}` が届きます（`params: {sender_id, args:[]}`）。

---

## 9. 起動ログの見方

正常起動時のログ例：

```
[GoAPI] Running on Paper          ← Paper 環境を検出
[GoAPI] JsonRpcServer started on port 8766
```

GoLoader と連携成功時:

```
[GoLoader] Go server ready (pid=12345)
[GoLoader] RUNNING pid=12345 uptime=0m5s
```

---

## 10. BridgePlugin の自動生成

`BridgePlugin.java` と `plugin.yml` は **直接編集禁止** です。  
イベント一覧を変更したい場合は `events.json` を編集してから以下を実行してください。

```bash
# リポジトリルートで実行
python3 scrape_goapi.py
cd GoAPI && ./gradlew --no-daemon jar
```

---

## 11. トラブルシューティング

### GoAPI が有効にならない

- `plugins/GoAPI.jar` が正しい場所に配置されているか確認してください。
- Java 21 以上で実行しているか確認してください。

### Go との通信が失敗する

- GoLoader が起動し、`go-server` バイナリが動作しているか確認してください。
- `config.yml` の `go-server-url` と GoLoader の `config.yml` のポート設定が一致しているか確認してください。
- ファイアウォールでポート 8765/8766 が開いているか確認してください。

### `BridgePlugin.java` をうっかり編集してしまった

```bash
# 再生成で上書きできます
python3 scrape_goapi.py
```

### GoLoader に GoAPI が見つからないと言われる

`plugins/` に `GoAPI.jar` が存在し、GoAPI が正常にロードされていることを確認してください。  
GoLoader の `plugin.yml` に `depend: [GoAPI]` が設定されているため、GoAPI が先にロードされる必要があります。
