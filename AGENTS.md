# Repository Guidelines

## 言語
- 回答は必ず日本語で行ってください。

## プロジェクト概要
- Stream Tweaks は Fabric Loom を用いた Minecraft 1.21.8 向けクライアント専用 Mod です。
- Twitch の OAuth・Helix API・EventSub WebSocket を組み合わせて、Minecraft 内チャットに Twitch コメントを転送します。
- 主な依存ライブラリは Fabric API と Gson、Java 21 標準ライブラリ (java.net.http など) です。

## ソース構成
- `src/main/java/org/etwas/streamtweaks/StreamTweaks.java`: 共通エントリポイント。ロガーと開発時専用の `devLogger` を提供します。
- `src/main/java/org/etwas/streamtweaks/client/StreamTweaksClient.java`: クライアント初期化処理。`ClientCommandRegistrationCallback` で Twitch コマンドを登録します。
- `src/main/java/org/etwas/streamtweaks/client/commands/TwitchCommand.java`: `/twitch connect [login]` と `/twitch disconnect` を実装。Minecraft から `TwitchService` を呼び出します。
- `src/main/java/org/etwas/streamtweaks/twitch/service/TwitchService.java`: シングルトンとして Twitch 連携の中心を担い、
  - OAuth 認証 (`ensureAuthenticated`)
  - 配信者ログイン名の解決 (`resolveTargetLogin`)
  - Helix API を用いたユーザー検索 (`connectResolvedLogin`)
  - EventSub サブスクリプションの追加・削除 (`subscribeToChat`、`disconnect`)
  - Twitch チャット通知の Minecraft への反映 (`handleEventSubNotification`)
  を管理します。
- `src/main/java/org/etwas/streamtweaks/twitch/auth`: OAuth フロー周辺。
  - `TwitchOAuthClient` はアクセストークン取得を司り、ポート 7654 の `LocalHttpCallbackServer` でリダイレクトを受けます。
  - `TwitchCredentialStore` が `config/stream-tweaks/twitch-credentials.json` にトークンを保存します (POSIX 環境では 600 権限に調整)。
- `src/main/java/org/etwas/streamtweaks/twitch/api/HelixClient.java`: Helix REST API ラッパー。EventSub 購読作成/一覧/削除とユーザー情報取得 (現在ユーザー・ログイン名検索) を提供します。
- `src/main/java/org/etwas/streamtweaks/twitch/eventsub`: EventSub の WebSocket 実装群。
  - `EventSubManager` が購読の希望リストを管理し、`channel.chat.message` を中心にサブスクリプションを同期します。
  - `TwitchWebSocketClient` は java.net.http.WebSocket を用いて接続を維持し、指数バックオフで再接続します。
  - `SubscriptionSpec`、`SessionInfo` などのレコードでメタデータを表現します。
- `src/main/java/org/etwas/streamtweaks/utils`: 汎用ユーティリティ。
  - `ChatMessages` は `[StreamTweaks]` プレフィックス付きのチャットメッセージを生成します。
  - `KeepaliveMonitor` と `ThreadPools` は EventSub の死活監視タイマーを扱います。
  - `BackoffPolicy` / `ExponentialBackoffPolicy` が WebSocket の再接続遅延を計算します。
- リソース: `src/main/resources/fabric.mod.json` (エントリポイント定義)、`stream-tweaks.mixins.json` (現在は空のミックスイン設定)。

## Twitch コマンドの挙動
- `/twitch connect` : 認証済みユーザーのチャンネルに接続。未認証時はクリック可能な案内をチャットに表示して OAuth を促します。
- `/twitch connect <login>` : 明示したログイン名のチャンネルに接続。成功時はカラー付きメッセージで通知します。
- `/twitch disconnect` : アクティブな購読を解除し、切断メッセージを表示します。
- 例外発生時は `StreamTweaks.LOGGER` に詳細を出力しつつ、Minecraft チャットへ日本語のエラーメッセージを送信します。

## 認証と資格情報
- アクセストークンは `TwitchCredentialStore` が Fabric の config ディレクトリ配下に保存します。リポジトリへは絶対に含めないでください。
- OAuth コールバックは `http://localhost:7654/callback` 固定。ブラウザから返るフラグメント (`#access_token=...`) はローカルサーバーが再リクエストで取得します。
- 新規認証とキャッシュ済みトークンを区別するため `AuthResult.AuthType` を利用し、新規認証完了時のみ成功メッセージを表示します。

## EventSub / チャット処理
- `EventSubManager` は購読希望セットを監視し、接続完了時に Helix API へ購読作成リクエストを送信します。不要になった購読は削除します。
- 受信した `channel.chat.message` は `TwitchService` が JSON を解析し、Minecraft チャットへ `[Twitch]` プレフィックスで出力します。
- ユーザーのカラーコードは読みやすさを確保するため `adjustForReadability` で明度を補正します。`/me` 相当のアクションメッセージやイタリック表示にも対応しています。
- Keepalive が途絶した場合は `KeepaliveMonitor` がタイムアウトを検知し、WebSocket を再接続させます。

## ビルド / 実行
- Gradle の主要タスク:
  - `gradle build` : コンパイル・チェックと `build/libs/` への JAR 出力。
  - `gradle runClient` : 開発用クライアントを起動。
  - `gradle runServer` : 開発用サーバーを起動 (現在の機能はクライアント専用)。
  - `gradle genSources` : IDE 向けにソースを再マッピング。
- ビルド成果物: `build/` 以下。開発時の実行環境データは `run/` に配置されます。

## コーディングスタイル
- Java コードは 4 スペースインデント・UTF-8・目安 120 文字以内。
- パッケージは小文字ドメイン (`org.etwas.streamtweaks.*`)、クラスは PascalCase、フィールド/メソッドは camelCase、定数は UPPER_SNAKE。
- エントリポイント (`StreamTweaks`, `StreamTweaksClient`) は軽量に保ち、初期化ロジックはサービス層に寄せます。
- 使わないミックスインは `stream-tweaks.mixins.json` に追加しないこと。

## テスト
- 現状ユニットテストは未整備。追加する場合は JUnit 5 を `src/test/java` に配置し、対象クラス名に対応するテスト名を付けてください (例: `TwitchOAuthClientTest`)。
- PR や大きな変更前には必ず `gradle build` で検証してください。
- CI では全ブランチへのプッシュ・プルリクエスト時に自動的にビルドテストが実行されます（Java 21, 22 で並列実行）。詳細は `docs/ci-build.md` を参照してください。

## セキュリティ / ログ方針
- アクセストークンやユーザー ID などの秘密情報はログに直接出力しないでください (`devLogger` は開発環境のみ有効)。
- 設定ファイルや新規データファイルを追加する場合は `config/stream-tweaks/` 以下にまとめ、Git には含めないよう `.gitignore` を確認してください。

## 開発メモ
- `TwitchService` と `EventSubManager` はシングルトン/長寿命オブジェクトのため、シャットダウン処理やスレッドの扱いに注意してください。
- WebSocket 再接続ポリシーやチャット描画ロジックを変更する際は、Minecraft チャットスレッド (`MinecraftClient#execute`) に戻す点を守ってください。
- 既存の日本語チャットメッセージやユーザー向け文言は、トーンを統一するため可能な限り再利用してください。
