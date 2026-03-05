# GoAPI（日本語ガイド）

GoAPI は **Minecraft(Paper/Spigot) と Go 実装を JSON-RPC で接続するブリッジプラグイン**です。  
この README は、初めて触る人が **これだけ読めば導入〜起動確認までできる** ことを目的にしています。

---

## 1. できること

- Java(サーバー) → Go へイベント通知（`event.*`）
- Go → Java へ API 逆コール（`api.player.*`, `api.world.*` など）
- Go 側からコマンド動的登録（`api.command.register`）
- Go 側から scheduler 実行（`api.scheduler.*`）

---

## 2. 前提

- Java 21
- Paper/Spigot 1.21.8 系
- Python 3（`scrape_goapi.py` 実行用）
- GoLoader と連携して使う（Go 実行プロセス管理は GoLoader 側）

> このリポジトリはルート直下に実装があり、プラグイン本体は `GoAPI/` ディレクトリです。

---

## 3. ディレクトリ構成（重要箇所）

```text
GoAPI/
├── scrape_goapi.py                # BridgePlugin / plugin.yml 自動生成
├── events.json                    # 自動生成元イベント定義
└── GoAPI/
    ├── build.gradle
    └── src/main/
        ├── java/com/example/goapi/
        └── resources/
```

---

## 4. ビルド手順（最短）

リポジトリルートで実行:

```bash
# 1) 自動生成ファイルを更新（必要時）
python3 scrape_goapi.py

# 2) Jar ビルド
cd GoAPI
./gradlew --no-daemon jar
```

生成物:

- `GoAPI/build/libs/GoAPI.jar`

---

## 5. サーバーへの配置

Minecraft サーバー直下（`server-root`）を前提:

```text
server-root/
└── plugins/
    ├── GoAPI.jar
    └── GoLoader.jar   # 別リポジトリ側でビルド
```

`GoAPI.jar` を `plugins/` に配置して起動します。

---

## 6. 設定ファイル

初回起動で `plugins/GoAPI/config.yml` が生成されます（デフォルト値）:

```yaml
go-server-url: http://localhost:8765
java-rpc-port: 8766
```

- `go-server-url`: Go サーバー（イベント受け口）
- `java-rpc-port`: Go から Java API を呼び戻すポート

---

## 7. 通信ポートと方向

- `8765`: Java → Go（イベント通知）
- `8766`: Go → Java（API逆コール）

JSON-RPC 2.0 例:

```json
{"jsonrpc":"2.0","method":"event.PlayerJoinEvent","params":{"player_id":"uuid"},"id":"evt-1"}
```

---

## 8. Go から呼べる主な API

- `api.player.*`
  - `getName`, `sendMessage`, `setHealth`, `getHealth`, `teleport`, `getLocation`, `kick`, `giveItem`, `setGameMode`, `getGameMode`
- `api.world.*`
  - `getBlock`, `setBlock`, `getSpawn`, `setSpawn`, `getTime`, `setTime`, `spawnEntity`
- `api.scheduler.*`
  - `runLater`, `runTimer`, `cancel`
- `api.command.*`
  - `register`, `unregister`

---

## 9. サーバー起動時の確認ポイント

起動ログに以下が出れば正常です。

- `Running on Paper` または `Running on Spigot/Bukkit`
- GoLoader 側の Go サーバー起動ログ（連携時）

---

## 10. よくある詰まりポイント

1. **Jar の置き場所が違う**  
   `plugins/GoAPI.jar` になっているか確認。

2. **Go 側が起動していない**  
   `go-server-url` 先が疎通可能か確認（通常は GoLoader が起動管理）。

3. **自動生成ファイルを直接編集した**  
   `BridgePlugin.java` / `plugin.yml` は `scrape_goapi.py` で再生成する前提。

---

## 11. 開発時の再生成ルール

イベント定義を変更した場合は次を実行:

```bash
python3 scrape_goapi.py
cd GoAPI && ./gradlew --no-daemon jar
```

これで `BridgePlugin.java` と `plugin.yml` が最新化されます。
