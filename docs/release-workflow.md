# GitHub Actions を用いたリリース運用ガイド

このドキュメントは、手動実行可能な `publish-minotaur-simple-tags.yml` ワークフローを用いて Modrinth および GitHub リリースを公開する際の手順と注意点をまとめたものです。

## 概要
- GitHub Actions のワークフローを手動実行し、リリースチャンネル (`alpha` / `beta` / `release`) を選択してリリースを行います。
- ワークフローは `gradle.properties` の `mod_version`（安定版の値）とコミットが属する Minecraft バージョン用のリモートブランチ名 (`1.21.8` など) を基に恒久タグを作成します。
- `alpha` / `beta` チャンネルでは、同じ `mod_version` を保ったまま `1.2.3-alpha.3` のような連番付きバージョンを自動生成し、恒久タグ `v<mod_version>-<channel>.<n>+mc<Minecraft>` を push します。
- `release` チャンネルでは `1.2.3` をそのまま用い、恒久タグ `v<mod_version>+mc<Minecraft>` を作成します。
- ビルド済み JAR を Modrinth と GitHub Releases に公開し、Discord Webhook へ通知します。

## 手動実行手順
1. リリースしたいコミットが Minecraft バージョン用のブランチ (`origin/1.21.8` など) に含まれていることを確認してください。
2. `gradle.properties` の `mod_version` を公開したい安定版バージョンに更新し、必要に応じてコミットします（プレリリースでも同じ値を使います）。
3. 変更内容を `CHANGELOG.md` に追記します（`release` チャンネル時はワークフローが自動で安定版セクションを更新します）。
4. ローカルで `./gradlew build` を実行してビルドが通ることを確認しておくと安全です。
5. GitHub の Actions タブからワークフローを手動実行します：
   - リポジトリページの「Actions」タブをクリック
   - 「Manual Release」ワークフローを選択
   - 「Run workflow」ボタンをクリック
   - リリースチャンネル (`alpha` / `beta` / `release`) を選択
   - 「Run workflow」で実行開始

## ワークフロー実行時の挙動
- `Parse channel` ステップで選択されたチャンネルを設定します。
- `Detect Minecraft version` ステップが、コミットを含むリモートブランチ名（数値だけの名前）から対象バージョンを決定します。該当ブランチが無い場合は処理が中断されます。
- `Create permanent tag` ステップでプレリリースの場合は `v1.2.3-alpha.3+mc1.21.8` のような恒久タグを、安定版の場合は `v1.2.3+mc1.21.8` を自動作成し push します。GitHub Release もこのタグ名で作成されます。
- `release` チャンネルで同じ恒久タグが既に存在する場合は上書きせずにワークフローが失敗します。新しい安定版を公開する場合は `mod_version` を更新してください。
- `Publish to Modrinth & GitHub` ステップで、Gradle ビルド成果物（sources/dev 付き以外の JAR）が配布先へアップロードされます。

## トラブルシューティング
- **Minecraft バージョンが検出できない**: 対象コミットを含む数値のみのブランチ（例: `origin/1.21.8`）が存在するか確認し、必要なら `git push origin HEAD:1.21.8` でブランチを更新してください。
- **`mod_version` が取得できない**: `gradle.properties` に `mod_version=<バージョン>` の形式で設定されているか確認します。設定ミスがあるとワークフローが失敗します。
- **release で恒久タグが重複する**: `v1.2.3+mc1.21.8` のようなタグが既に存在するとジョブが失敗します。`mod_version` を次の安定版に更新してから再実行します。
- **ワークフローが見つからない**: GitHub の Actions タブで「Manual Release」ワークフローが表示されない場合は、ワークフローファイルが正しく push されているか確認してください。

## リリース後の確認
- GitHub Releases と Modrinth に恒久タグ名でバージョンが公開され、添付ファイルが期待通りか確認します。
- Discord 通知が届いているか、リンク先が正しいかチェックしてください。
- `CHANGELOG.md` に不要な差分が無いかレビューし、必要であれば手動調整を行います。
