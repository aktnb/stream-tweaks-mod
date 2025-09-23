# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Fabric Loom ベースの Minecraft 1.21.8 クライアント専用 Mod として Stream Tweaks を初期化し、`StreamTweaks` / `StreamTweaksClient` エントリポイントを実装。
- Twitch OAuth 認証フローを構築し、ローカル HTTP コールバックサーバーと資格情報ストアでアクセストークンを安全に保持。
- Helix API クライアントを追加して、ユーザー検索および EventSub サブスクリプション (作成・一覧・削除) を操作可能に。
- EventSub WebSocket クライアントを実装し、指数バックオフ・Keepalive 監視・サブスクリプション同期を通じて接続を維持。
- `/twitch connect`・`/twitch disconnect` コマンドを追加し、Minecraft から TwitchService によるチャンネル接続と切断を実行可能に。
- Twitch チャットメッセージを Minecraft チャットへ `[Twitch]` プレフィックス付きで転送し、ユーザーカラーの可読性を補正する描画ロジックを追加。
- 共通ユーティリティ (`ChatMessages`, `ThreadPools`, `KeepaliveMonitor`, `ExponentialBackoffPolicy`) を追加し、チャット表示とバックグラウンド処理の安定性を向上。
- GitHub Actions の公開ワークフローと Discord 通知ステップを導入し、自動リリース準備を整備。

### Changed
- `/twitch connect` の接続手順とエラーメッセージを改善し、認証状態やチャンネル解決結果をプレイヤーへ詳細にフィードバック。
- `TwitchService#ensureAuthenticated` を非同期化し、`CompletableFuture` による例外処理とログ出力を整理。
- ロガー利用を統一し、開発時専用の `devLogger` を導入してデバッグ出力を分離。

### Fixed
- `.gitignore` を更新して Gradle キャッシュと VS Code 設定ファイルが誤ってコミットされないように修正。
