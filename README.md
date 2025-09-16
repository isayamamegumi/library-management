# 蔵書管理システム

Java Spring Boot + React TypeScriptで構築された高機能な個人向け蔵書管理アプリケーションです。最新のアーキテクチャとセキュリティ機能を備えた、エンタープライズレベルのシステムです。

## 🌟 主要機能

### 📚 書籍管理
- 書籍の追加・編集・削除・検索
- 著者情報の管理（多対多リレーション）
- 読書状況の管理（未読、読書中、読了、中断中）
- ISBN、出版社、出版日などの詳細情報管理
- ユーザーごとの書籍所有権管理
- **高速検索・フィルタリング機能**

### 🔐 認証・セキュリティ
- **JWT + セッション ハイブリッド認証**（Spring Security）
- ユーザー登録・ログイン機能
- **自動トークンリフレッシュ機能**
- **React Context によるクライアント状態管理**
- ユーザー権限管理（USER/ADMIN）
- セキュアなパスワード暗号化（BCrypt）
- **ProtectedRoute コンポーネント**による認証ガード

### ⚡ バッチ処理システム
- **統合バッチ管理サービス**による一元化
- **複合統計ジョブ**: ユーザー・ジャンル・読書ペース分析
- **並列パーティション処理**: 大量データの効率的処理
- **バッチチェーンフロー**: 条件分岐を含む連続処理
- **リアルタイム監視**: 実行中ジョブの進捗追跡
- **エラー回復機能**: 失敗ジョブの自動復旧
- **スケジュール管理**: Cron式による定期実行
- **通知システム**: 実行結果の自動通知

### 🛡️ セキュリティ機能
- **強化されたCORS設定** (withCredentials対応)
- **CSRFプロテクション** (準備済み)
- セキュリティヘッダー設定
- アクセス制御（自分の書籍のみ編集・削除可能）
- **入力値サニタイゼーション** (XSS/SQLインジェクション対策)
- **グローバル例外ハンドリング**
- **セキュリティ監査ログ**
- **自動ログアウト機能** (トークン期限切れ時)

### 🎨 UI/UX改善
- **モダンなグラスモーフィズムデザイン**
- **グラデーション背景とエフェクト**
- **レスポンシブデザイン** (モバイル対応)
- **リアルタイムバリデーション**
- **詳細なエラーメッセージ表示**
- **ローディング状態表示**
- **エラーバウンダリ**による例外処理
- **再利用可能UIコンポーネント**（Modal、LoadingSpinner等）

## 🏗️ 技術スタック

### バックエンド
- **Java 17**
- **Spring Boot 3.1.0**
- **Spring Batch 5** (バッチ処理)
- **Spring Data JPA**
- **Spring Security** (JWT + Session)
- **Jakarta Validation** (Bean Validation)
- **PostgreSQL**
- **Flyway** (データベースマイグレーション)
- **Quartz Scheduler**
- **Maven**
- **Logback** (構造化ログ)

### フロントエンド
- **React 18**
- **TypeScript**
- **React Context API** (状態管理)
- **React Router v6** (ProtectedRoute)
- **Custom Hooks** (再利用可能ロジック)
- **Axios** (認証インターセプター対応)
- **CSS3** (モダンUI, レスポンシブ)
- **エラーハンドリング** (ErrorBoundary)
- **型安全なサービス層**

### アーキテクチャ改善
- **抽象基底クラス**: 共通処理の標準化
- **統合管理サービス**: ビジネスロジックの一元化
- **レポジトリパターン**: データアクセス層の最適化
- **サービス層分離**: フロントエンドAPI呼び出しの整理
- **型安全性**: TypeScript + Jakarta Validation

## 🚀 セットアップ手順

### 前提条件
- **Java 17以上**
- **Node.js 16以上**
- **PostgreSQL 12以上**
- **Maven 3.6以上**

### データベース設定

```sql
-- データベース作成
CREATE DATABASE librarymanage;

-- 以下のテーブルがFlywayにより自動作成されます
-- - users (ユーザー管理)
-- - roles (権限管理)
-- - books (書籍管理)
-- - authors (著者管理)
-- - book_authors (書籍-著者関連)
-- - genres (ジャンル管理)
-- - read_statuses (読書状況管理)
-- - batch_execution_logs (バッチ実行ログ)
-- - batch_statistics (統計データ)
-- - system_logs (システムログ)
```

### バックエンドの起動

1. **設定ファイル更新**
```yaml
# backend/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/librarymanage
    username: postgres
    password: your-password
```

2. **依存関係のインストールとビルド**
```bash
cd backend
mvn clean compile
```

3. **アプリケーション起動**
```bash
mvn spring-boot:run
```

バックエンドは http://localhost:8080 で起動します。

### フロントエンドの起動

1. **フロントエンドディレクトリに移動**
```bash
cd frontend
```

2. **依存関係をインストール**
```bash
npm install
```

3. **開発サーバーを起動**
```bash
npm start
```

フロントエンドは http://localhost:3000 で起動します。

## 📡 API エンドポイント

### 🔐 認証 API
- `POST /api/auth/login` - ログイン（JWT + セッション作成）
- `POST /api/auth/register` - ユーザー登録
- `POST /api/auth/logout` - ログアウト（JWT + セッション削除）
- `POST /api/auth/refresh` - **アクセストークン更新**

### 📚 書籍管理 API（認証必須）
- `GET /api/books` - 書籍一覧取得（検索・フィルタリング対応）
- `GET /api/books/{id}` - 書籍詳細取得
- `POST /api/books` - 新規書籍追加
- `PUT /api/books/{id}` - 書籍更新（所有者のみ）
- `DELETE /api/books/{id}` - 書籍削除（所有者のみ）
- `GET /api/books/isbn/{isbn}` - ISBN検索

### 👥 ユーザー管理 API（認証必須）
- `GET /api/users/me` - 自分の情報取得
- `PUT /api/users/me` - 自分の情報更新
- `GET /api/users` - ユーザー一覧（管理者のみ）
- `DELETE /api/users/{id}` - ユーザー削除（管理者のみ）

### 📊 バッチ管理 API（管理者のみ）
- `GET /api/batch/statistics` - バッチ統計情報取得
- `GET /api/batch/running` - 実行中ジョブ一覧
- `POST /api/batch/jobs/{jobName}/execute` - ジョブ手動実行
- `POST /api/batch/executions/{id}/stop` - ジョブ停止
- `GET /api/batch/parameters` - パラメータ管理
- `GET /api/batch/schedules` - スケジュール管理
- `GET /api/batch/notifications` - 通知設定
- `POST /api/batch/recovery/restart/{id}` - 失敗ジョブ再実行

## 🎯 使用方法

### 初回セットアップ
1. ブラウザで http://localhost:3000 にアクセス
2. 「新規登録」からアカウントを作成
3. **モダンなログイン画面**でログイン
4. **自動的に認証状態が管理**されます

### 書籍管理
1. ダッシュボードで「新しい書籍を追加」から書籍を登録
2. 著者名は複数入力可能（カンマ区切り）
3. 自分が登録した書籍のみ編集・削除可能
4. 検索欄でタイトル、出版社、著者で検索
5. ドロップダウンで読書状況によるフィルタリングが可能

### バッチ管理（管理者機能）
1. **バッチ管理画面**にアクセス
2. **統計情報**でシステム全体の実行状況を確認
3. **ジョブ手動実行**で各種分析処理を開始
4. **実行中ジョブ**でリアルタイム進捗を監視
5. **スケジュール管理**で定期実行を設定
6. **エラー回復**で失敗したジョブを再実行

## 📈 バッチ処理詳細

### 利用可能なジョブ

1. **複合統計ジョブ** (`complexStatsJob`)
   - ユーザー統計、ジャンル分析、読書ペース分析の統合処理
   - 推定実行時間: 中程度

2. **バッチチェーンフロージョブ** (`batchChainFlowJob`)
   - データ検証→システムヘルスチェック→条件分岐処理→通知
   - 推定実行時間: 長時間

3. **並列パーティションジョブ** (`parallelPartitionedJob`)
   - ユーザーデータの分割並列処理とデータ変換
   - 推定実行時間: 中程度

### 監視・管理機能
- **リアルタイム進捗表示**: 実行中ジョブの詳細情報
- **実行履歴**: 過去のジョブ実行結果と統計
- **エラー分析**: 失敗原因の詳細解析
- **パフォーマンス監視**: 実行時間・メモリ使用量の追跡

## 🛡️ セキュリティ機能詳細

### 認証システム
- **ハイブリッド認証**: JWT + セッション認証
- **自動トークンリフレッシュ**: シームレスな認証継続
- **パスワード暗号化**: BCrypt（strength=12）
- **多層防御**: フロントエンド + バックエンド検証

### セキュリティ対策
- **CORS設定**: クロスオリジン対応 + Credentials
- **入力値サニタイゼーション**: XSS/SQLインジェクション防止
- **グローバル例外ハンドリング**: 統一されたエラーレスポンス
- **セキュリティヘッダー**: XSS、フレーム攻撃対策
- **アクセス制御**: 所有者ベースの認可
- **バリデーション**: Jakarta Validation による厳密な検証

### 監査・ログ
- **セキュリティ監査ログ**: 全認証イベントの記録
- **構造化ログ**: Logback による詳細ログ
- **バッチ実行ログ**: 処理結果と統計の自動記録

## 🔧 トラブルシューティング

### 起動時の問題
1. **Flywayマイグレーションエラー**:
   - 自動修復機能が有効
   - `FlywayRepairRunner`による自動復旧
2. **データベース接続エラー**:
   - PostgreSQLサービスの確認
   - 接続設定の確認
3. **コンパイルエラー**:
   - `mvn clean compile` で再ビルド

### 実行時の問題
1. **認証エラー**:
   - ブラウザのCookieとLocalStorageをクリア
   - JWT自動リフレッシュの確認
2. **バッチ実行エラー**:
   - エラー回復機能の使用
   - ログファイルの確認
3. **API通信エラー**:
   - ネットワークタブでリクエスト確認
   - CORS設定の確認

### ログ確認
- **アプリケーションログ**: コンソール出力
- **バッチ実行ログ**: `batch_execution_logs`テーブル
- **セキュリティログ**: `system_logs`テーブル
- **統計データ**: `batch_statistics`テーブル

## 👨‍💻 開発者向け情報

### アーキテクチャ概要
- **レイヤードアーキテクチャ**: Controller → Service → Repository
- **抽象基底クラス**: 共通処理の標準化
- **統合管理サービス**: ビジネスロジックの一元化
- **型安全設計**: TypeScript + Jakarta Validation

### 主要コンポーネント

#### バックエンド
- `BatchManagementService`: 統合バッチ管理サービス
- `AbstractBatchJobConfig`: バッチジョブ基底クラス
- `AbstractItemProcessor`: アイテム処理基底クラス
- `AbstractStatisticsWriter`: 統計ライター基底クラス
- `GlobalExceptionHandler`: グローバル例外ハンドリング
- `BatchExecutionRepository`: 最適化されたデータアクセス

#### フロントエンド
- `useBatch.ts`: バッチ処理カスタムフック
- `batchService.ts`: API呼び出しサービス層
- `Modal.tsx`: 再利用可能モーダルコンポーネント
- `LoadingSpinner.tsx`: ローディング表示コンポーネント
- 型安全なインターフェース定義

### 新機能・改善点
- **🔄 統合バッチ管理**: 全バッチ処理の一元化
- **⚡ パフォーマンス最適化**: データベースアクセスの効率化
- **🛡️ セキュリティ強化**: グローバル例外ハンドリング
- **📊 監視・分析**: リアルタイム進捗表示
- **🔧 保守性向上**: 抽象基底クラスによる標準化
- **🎨 UI/UX改善**: 再利用可能コンポーネント

### 開発環境
- **Java 17**: 最新のJava機能を活用
- **Spring Boot 3.1**: 最新のSpringエコシステム
- **React 18**: 最新のReact機能
- **TypeScript**: 完全な型安全性

## 📝 ライセンス

このプロジェクトはMITライセンスの下で公開されています。

---

**蔵書管理システム v2.0** - エンタープライズレベルの機能とセキュリティを備えた最新の蔵書管理アプリケーション