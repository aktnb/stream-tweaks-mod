# GitHub Actions を用いたリリース運用ガイド

このドキュメントは、`publish-minotaur-simple-tags.yml` ワークフローを用いて Modrinth および GitHub リリースを公開する際の手順と注意点をまとめたものです。

## 概要
- トリガータグ `alpha` / `beta` / `release` をリモートに push すると GitHub Actions が起動します。
- ワークフローは `gradle.properties` の `mod_version`（安定版の値）とコミットが属する Minecraft バージョン用のリモートブランチ名 (`1.21.8` など) を基に恒久タグを作成します。
- `alpha` / `beta` チャンネルでは、同じ `mod_version` を保ったまま `1.2.3-alpha.3` のような連番付きバージョンを自動生成し、恒久タグ `v<mod_version>-<channel>.<n>+mc<Minecraft>` を push します。
- `release` チャンネルでは `1.2.3` をそのまま用い、恒久タグ `v<mod_version>+mc<Minecraft>` を作成します。
- ビルド済み JAR を Modrinth と GitHub Releases に公開し、Discord Webhook へ通知します。
- 実行後はトリガータグが自動削除されるため、次回の同名タグ付与が可能です。

## 事前準備
- 対象コミットが Minecraft バージョン用のブランチ (`origin/1.21.8` など) に含まれていることを確認してください。
- `gradle.properties` の `mod_version` を公開したい安定版バージョンに更新し、必要に応じてコミットします（プレリリースでも同じ値を使います）。
- 変更内容を `CHANGELOG.md` に追記します（`release` チャンネル時はワークフローが自動で安定版セクションを更新します）。
- ローカルで `./gradlew build` を実行してビルドが通ることを確認しておくと安全です。

## タグ付けと push 手順
1. リリースしたいコミットをチェックアウトし、リモートへ push 済みであることを確認します。
2. 既存のローカルトリガータグが残っている場合は削除します。
   ```sh
   git tag -d alpha    # beta/release も同様に
   git fetch --tags
   ```
3. 対象チャンネルのタグを付与して push します。
   ```sh
   git tag alpha            # beta や release に置き換え
   git push origin alpha
   ```
   - `release` を付与した場合は安定版扱いになり、`CHANGELOG.md` が更新されることがあります。
   - `alpha` / `beta` の場合はプレリリースとして公開されます。

## ワークフロー実行時の挙動
- `Parse channel` ステップでタグ名が `alpha` / `beta` / `release` 以外の場合は失敗します。
- `Detect Minecraft version` ステップが、コミットを含むリモートブランチ名（数値だけの名前）から対象バージョンを決定します。該当ブランチが無い場合は処理が中断されます。
- `Create permanent tag` ステップでプレリリースの場合は `v1.2.3-alpha.3+mc1.21.8` のような恒久タグを、安定版の場合は `v1.2.3+mc1.21.8` を自動作成し push します。GitHub Release もこのタグ名で作成されます。
- `release` チャンネルで同じ恒久タグが既に存在する場合は上書きせずにワークフローが失敗します。新しい安定版を公開する場合は `mod_version` を更新してください。
- `Publish to Modrinth & GitHub` ステップで、Gradle ビルド成果物（sources/dev 付き以外の JAR）が配布先へアップロードされます。
- ジョブ完了後に `Delete trigger tag` が走り、リモートの `alpha` / `beta` / `release` タグは削除されます。ローカルでは手動で削除する必要があります。

## トラブルシューティング
- **Minecraft バージョンが検出できない**: 対象コミットを含む数値のみのブランチ（例: `origin/1.21.8`）が存在するか確認し、必要なら `git push origin HEAD:1.21.8` でブランチを更新してください。
- **`mod_version` が取得できない**: `gradle.properties` に `mod_version=<バージョン>` の形式で設定されているか確認します。設定ミスがあるとワークフローが失敗します。
- **ローカルにトリガータグが残っている**: リモートでは自動削除されますが、ローカルでも `git tag -d alpha` のように削除してから再実行してください。
- **release で恒久タグが重複する**: `v1.2.3+mc1.21.8` のようなタグが既に存在するとジョブが失敗します。`mod_version` を次の安定版に更新してから再実行します。

## リリース後の確認
- GitHub Releases と Modrinth に恒久タグ名でバージョンが公開され、添付ファイルが期待通りか確認します。
- Discord 通知が届いているか、リンク先が正しいかチェックしてください。
- `CHANGELOG.md` に不要な差分が無いかレビューし、必要であれば手動調整を行います。
