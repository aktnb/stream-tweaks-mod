# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 開発コマンド

### ビルド
```bash
# プロジェクトをビルドする
gradle build

# 依存関係を更新
gradle --refresh-dependencies build
```

### 実行とテスト
```bash
# Minecraft開発環境でテスト実行
gradle runClient

# サーバー環境でテスト実行
gradle runServer

# ソースを生成してリマップ
gradle genSources
```

## プロジェクト構造

### プロジェクト情報
- **グループID**: `org.etwas.streamtweaks`
- **モジュール名**: `stream-tweaks`
- **バージョン**: `1.0-SNAPSHOT`
- **環境**: `client` (クライアント専用モッド)

### Fabricモッドの基本構造
- **メインクラス**: `StreamTweaks` (`org.etwas.streamtweaks.StreamTweaks`)
- **クライアントクラス**: `StreamTweaksClient` (`org.etwas.streamtweaks.client.StreamTweaksClient`)
- **設定ファイル**: `fabric.mod.json` - モッドの定義とエントリーポイント
- **Mixinファイル**: `stream-tweaks.mixins.json` - コード注入の設定（現在は空）

### 認証システム (twitch.auth パッケージ)
- **TwitchOAuthClient**: Twitch OAuth2認証のメインクライアント
  - `getAccessToken(scopes, onRequiresUserInteraction)` - スコープ指定でアクセストークン取得
  - `authorize(scopes, onRequiresUserInteraction)` - OAuth2認証フロー（ローカルコールバックサーバー使用）
- **TwitchCredentialStore**: 認証情報の永続化 (Gson使用、設定ディレクトリに保存)
- **TwitchCredentials**: 認証情報を格納するデータクラス
- **AuthResult**: 認証結果を格納するクラス（トークンと認証タイプ）
- **LocalHttpCallbackServer**: OAuth2コールバック用のローカルHTTPサーバー

### EventSub WebSocket システム (twitch.eventsub パッケージ)
- **WebSocketClient**: Twitch EventSub WebSocketへの接続クライアント
  - `wss://eventsub.wss.twitch.tv/ws` への接続
  - Welcome、Keepalive、Notification、Reconnect、Revocationメッセージ処理
  - 自動再接続機能（reconnect_url対応）
  - Ping/Pong自動応答
- **EventSubManager**: EventSub購読とWebSocket管理の上位レベルAPI
  - `subscribeToChannelUpdate()` - チャンネル更新イベント購読
  - `subscribeToStreamOnline/Offline()` - 配信開始/終了イベント購読
  - `subscribeToChannelFollow()` - フォローイベント購読
  - サブスクリプション作成・削除・一覧取得
- **EventSubMessage**: EventSubメッセージの構造体とメタデータ
- **SessionInfo**: WebSocketセッション情報（ID、ステータス、keepalive設定など）

### 技術スタック
- **Fabric Mod** (Minecraft 1.21.8, Yarn mappings 1.21.8+build.1)
- **Fabric Loader**: 0.17.2
- **Fabric API**: 0.133.4+1.21.8
- **Java 21**
- **Gson**: 2.13.2 (JSON処理)
- **CompletableFuture**: 非同期処理
- **HttpClient**: HTTP通信 (Java標準)

### 設定とビルド
- **Gradle + Fabric Loom**: モッドビルドシステム (1.11-SNAPSHOT)
- **設定ディレクトリ**: `FabricLoader.getInstance().getConfigDir().resolve(MOD_ID)`
- **認証ファイル**: `twitch-credentials.json` (権限600で保存)
- **Mixinパッケージ**: `org.etwas.streamtweaks.mixin` (Java 21互換)

## 重要な設計パターン
- OAuth2認証は非同期で処理し、ユーザーインタラクション（ブラウザでの認証）が必要な場合はコールバックで通知
- 認証情報は暗号化なしでローカル保存（開発段階）
- AuthResultクラスでキャッシュされたトークンと新規認証を区別
- EventSub WebSocketは自動再接続機能付きで堅牢な接続を維持
- WebSocketとEventSub API両方を組み合わせたイベント購読システム
- Java 21の標準WebSocketクライアントとHTTPクライアントを使用
- Mixinシステム準備済み（現在は空の設定）

## 使用例
```java
// EventSub WebSocketクライアントの使用例
TwitchOAuthClient oauthClient = new TwitchOAuthClient();
EventSubManager eventSub = new EventSubManager(oauthClient);

eventSub.setMessageHandler(message -> {
    // イベント通知の処理
    StreamTweaks.LOGGER.info("Received event: {}", message.metadata.subscriptionType);
});

eventSub.connect().thenAccept(sessionInfo -> {
    StreamTweaks.LOGGER.info("Connected to EventSub: {}", sessionInfo.id);

    // 配信開始イベントを購読
    eventSub.subscribeToStreamOnline("broadcaster_id");
});
```