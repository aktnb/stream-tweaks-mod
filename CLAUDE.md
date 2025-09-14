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

### 認証システム (auth パッケージ)
- **TwitchOAuthClient**: Twitch OAuth2認証のメインクライアント
  - `getAccessToken()` - アクセストークンの取得（キャッシュ対応）
  - `authorize()` - OAuth2認証フロー（ローカルコールバックサーバー使用）
- **TwitchCredentialStore**: 認証情報の永続化 (Gson使用、設定ディレクトリに保存)
- **TwitchCredentials**: 認証情報を格納するデータクラス
- **AuthResult**: 認証結果を格納するクラス（トークンと認証タイプ）
- **LocalHttpCallbackServer**: OAuth2コールバック用のローカルHTTPサーバー

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
- Mixinシステム準備済み（現在は空の設定）