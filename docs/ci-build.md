# CI ビルドワークフロー

このプロジェクトでは、コードの品質を保つために GitHub Actions を使用した継続的インテグレーション（CI）を実装しています。

## ビルドワークフロー

### トリガー条件
- 全ブランチへのプッシュ
- 全ブランチへのプルリクエスト

### 実行内容
1. **マルチバージョンJavaテスト**: Java 21 と Java 22 で並列ビルドテスト
2. **Gradleビルド**: `./gradlew build --stacktrace` でプロジェクトをビルド
3. **成果物保存**: ビルド成功時にJARファイルを7日間保存

### 技術仕様
- **OS**: Ubuntu latest
- **Java**: Temurin distribution (Java 21, 22)
- **Gradle**: Actions setup-gradle@v4 で自動設定
- **ビルドツール**: Fabric Loom 1.11-SNAPSHOT

### 成果物
- `build/libs/*.jar` (sources, dev jarは除外)
- アーティファクト名: `build-artifacts-javaXX-COMMIT_HASH` (XX=Javaバージョン、COMMIT_HASH=コミットハッシュ)
- 保持期間: 7日間

## ローカルでの確認方法

CIと同じ方法でローカルでビルドを確認する場合:

```bash
./gradlew build --stacktrace
```

## トラブルシューティング

### よくある問題
1. **Fabric Loom SNAPSHOT版が見つからない**
   - 通常は一時的な問題です。時間をおいて再実行してください。
   
2. **Java バージョン互換性エラー**
   - プロジェクトはJava 21がメインターゲットです。
   - Java 22での動作は互換性確認用です。

### ログの確認
- GitHub ActionsのUI上で各ステップの詳細ログが確認できます
- `--stacktrace` オプションによりエラーの詳細が出力されます