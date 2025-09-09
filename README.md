# 蔵書管理システム

Java Spring Boot + React TypeScriptで構築された個人向け蔵書管理アプリケーションです。JWT認証とセッション認証のハイブリッド方式を使用したセキュアなシステムで、ユーザーごとに書籍を管理できます。

## 機能

### 📚 書籍管理
- 書籍の追加・編集・削除・検索
- 著者情報の管理（多対多リレーション）
- 読書状況の管理（未読、読書中、読了、中断中）
- ISBN、出版社、出版日などの詳細情報管理
- ユーザーごとの書籍所有権管理

### 🔐 認証・セキュリティ
- **JWT + セッション ハイブリッド認証**（Spring Security）
- ユーザー登録・ログイン機能
- **自動トークンリフレッシュ機能**
- **React Context によるクライアント状態管理**
- ユーザー権限管理（USER/ADMIN）
- セキュアなパスワード暗号化（BCrypt）
- **ProtectedRoute コンポーネント**による認証ガード

### 🛡️ セキュリティ機能
- **強化されたCORS設定** (withCredentials対応)
- **CSRFプロテクション** (準備済み)
- セキュリティヘッダー設定
- アクセス制御（自分の書籍のみ編集・削除可能）
- **入力値サニタイゼーション** (XSS/SQLインジェクション対策)
- セキュリティ監査ログ
- **自動ログアウト機能** (トークン期限切れ時)
- **悪意のあるパターン検出**

### 🎨 UI/UX改善
- **モダンなグラスモーフィズムデザイン**
- **グラデーション背景とエフェクト**
- **レスポンシブデザイン** (モバイル対応)
- **リアルタイムバリデーション**
- **詳細なエラーメッセージ表示**
- **ローディング状態表示**
- **エラーバウンダリ**による例外処理

## 技術スタック

### バックエンド
- Java 17
- Spring Boot 3.1.0
- Spring Data JPA
- **Spring Security** (JWT + Session)
- **JWT (Access & Refresh Token)**
- **セキュリティ監査システム**
- PostgreSQL
- Maven
- **Logback** (構造化ログ)

### フロントエンド
- React 18
- TypeScript
- **React Context API** (状態管理)
- **React Router v6** (ProtectedRoute)
- Axios (認証インターセプター対応)
- **CSS3** (モダンUI, レスポンシブ)
- **エラーハンドリング** (ErrorBoundary)
- **入力値サニタイゼーション**

## セットアップ手順

### 前提条件
- Java 17以上
- Node.js 16以上
- PostgreSQL
- Maven

### データベース設定
データベース: `librarymanage`
以下のテーブルが作成済みであることを確認してください：

```sql
-- usersテーブル（ユーザー管理）
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role_id BIGINT REFERENCES roles(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- rolesテーブル（権限管理）
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

-- booksテーブル（ユーザー所有権追加）
CREATE TABLE books (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    publisher VARCHAR(255),
    published_date DATE,
    isbn VARCHAR(13) UNIQUE,
    read_status VARCHAR(50),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- authorsテーブル
CREATE TABLE authors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- book_authorsテーブル（多対多リレーション）
CREATE TABLE book_authors (
    book_id BIGINT REFERENCES books(id) ON DELETE CASCADE,
    author_id BIGINT REFERENCES authors(id) ON DELETE CASCADE,
    PRIMARY KEY (book_id, author_id)
);

-- 初期データ挿入
INSERT INTO roles (name) VALUES ('ADMIN'), ('USER');

-- インデックス作成（パフォーマンス向上）
CREATE INDEX idx_books_user_id ON books(user_id);
CREATE INDEX idx_books_isbn ON books(isbn);
CREATE INDEX idx_users_username ON users(username);
```

### バックエンドの起動

1. バックエンドディレクトリに移動
```bash
cd backend
```

2. Mavenで依存関係をインストール
```bash
mvn clean install
```

3. アプリケーションを起動
```bash
mvn spring-boot:run
```

バックエンドは http://localhost:8080 で起動します。

### フロントエンドの起動

1. フロントエンドディレクトリに移動
```bash
cd frontend
```

2. 依存関係をインストール
```bash
npm install
```

3. 開発サーバーを起動
```bash
npm start
```

フロントエンドは http://localhost:3000 で起動します。

## API エンドポイント

### 🔐 認証 API
- `POST /api/auth/login` - ログイン（JWT + セッション作成）
- `POST /api/auth/register` - ユーザー登録
- `POST /api/auth/logout` - ログアウト（JWT + セッション削除）
- `POST /api/auth/refresh` - **アクセストークン更新**

### 📚 Books API（認証必須）
- `GET /api/books` - 自分の書籍取得（検索・フィルタリング対応）
- `GET /api/books/{id}` - 書籍詳細取得
- `POST /api/books` - 新規書籍追加
- `PUT /api/books/{id}` - 書籍更新（所有者のみ）
- `DELETE /api/books/{id}` - 書籍削除（所有者のみ）
- `GET /api/books/isbn/{isbn}` - ISBN検索

### 👥 Users API（認証必須）
- `GET /api/users/me` - 自分の情報取得
- `PUT /api/users/me` - 自分の情報更新
- `GET /api/users` - ユーザー一覧（管理者のみ）
- `DELETE /api/users/{id}` - ユーザー削除（管理者のみ）

### Authors API（認証必須）
- `GET /api/authors` - 全著者取得
- `GET /api/authors/{id}` - 著者詳細取得
- `POST /api/authors` - 新規著者追加
- `PUT /api/authors/{id}` - 著者更新
- `DELETE /api/authors/{id}` - 著者削除

## 使用方法

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

### セキュリティ機能
- **JWT + セッション ハイブリッド認証**
- **自動トークンリフレッシュ**により継続的な認証
- **ProtectedRoute**による未認証ユーザーの自動リダイレクト
- **入力値の自動サニタイゼーション**
- **悪意のあるパターンの自動検出・ブロック**
- ログアウト時に全ての認証情報が無効化
- 他のユーザーの書籍は閲覧可能ですが編集不可

### UI/UX 特徴
- **レスポンシブデザイン** - スマートフォン・タブレット対応
- **グラスモーフィズム効果** - モダンな半透明デザイン
- **リアルタイムバリデーション** - 入力エラーの即座表示
- **詳細なエラーメッセージ** - 具体的な問題内容を表示
- **ローディング状態表示** - 処理中の視覚的フィードバック

## 読書状況の種類

- **未読**: まだ読んでいない
- **読書中**: 現在読んでいる
- **読了**: 読み終わった
- **中断中**: 途中で読むのを止めている

## 設定ファイル

### セッション設定（application.yml）
```yaml
server:
  servlet:
    session:
      timeout: 30m    # セッションタイムアウト30分
```

### データベース接続（application.yml）
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/librarymanage
    username: your-username
    password: your-password
```

## セキュリティ機能詳細

### 🔐 認証システム
- **ハイブリッド認証**: JWT + セッション認証
- **自動トークンリフレッシュ**: シームレスな認証継続
- **パスワード暗号化**: BCrypt（strength=12）
- **多層防御**: フロントエンド + バックエンド検証

### 🛡️ セキュリティ対策
- **CORS設定**: クロスオリジン対応 + Credentials
- **入力値サニタイゼーション**: XSS/SQLインジェクション防止
- **CSRFプロテクション**: トークンベース検証
- **セキュリティヘッダー**: XSS、フレーム攻撃対策
- **アクセス制御**: 所有者ベースの認可
- **悪意のあるパターン検出**: 自動ブロック機能

### 📊 監査・ログ
- **セキュリティ監査ログ**: 全認証イベントの記録
- **構造化ログ**: Logback による詳細ログ
- **リアルタイム監視**: 不正アクセス検出

## トラブルシューティング

### よくある問題
1. **認証エラー**: 
   - ブラウザのCookieとLocalStorageをクリア
   - 自動トークンリフレッシュの確認
   - JWTトークンの有効期限チェック
2. **CORS エラー**: 
   - バックエンドのCORS設定を確認
   - withCredentials設定の確認
3. **データベース接続エラー**: PostgreSQLが起動しているか確認
4. **コンパイルエラー**: `mvn clean install` で依存関係を再インストール
5. **UI表示エラー**: 
   - ブラウザのキャッシュクリア
   - React開発サーバーの再起動

### ログ確認
- **バックエンド**: 
  - コンソールまたは `backend/logs/application.log`
  - セキュリティ監査ログ
- **フロントエンド**: 
  - ブラウザの開発者ツール
  - React DevTools (認証状態確認)
  - Network タブ (API通信確認)

## 開発者向け情報

### アーキテクチャ
- **バックエンド**: Spring Boot MVC + Spring Security + JWT + Session
- **フロントエンド**: React SPA + Context API + Axios Interceptors
- **データベース**: PostgreSQL with JPA/Hibernate
- **認証**: **Hybrid Authentication** (JWT + Session)
- **セキュリティ**: 多層防御 + 監査ログ

### 主要コンポーネント

#### バックエンド
- `SecurityConfig`: Spring Security + JWT設定
- `JwtAuthenticationFilter`: JWT認証フィルター
- `AuthController`: 認証エンドポイント (login/logout/refresh)
- `SecurityLogService`: セキュリティ監査ログサービス
- `UserDetailsServiceImpl`: ユーザー認証サービス
- `BookService`: 書籍ビジネスロジック

#### フロントエンド
- `AuthContext`: React認証状態管理
- `ProtectedRoute`: 認証ガードコンポーネント
- `AuthService`: JWT管理・自動リフレッシュ
- `sanitization.ts`: 入力値サニタイゼーション
- `ErrorBoundary`: 例外処理コンポーネント

### 新機能・改善点
- **🔄 自動トークンリフレッシュ**: ユーザー体験の向上
- **🎨 モダンUI**: グラスモーフィズム + レスポンシブ
- **🛡️ セキュリティ強化**: 入力値サニタイゼーション + 監査ログ  
- **⚡ パフォーマンス向上**: Context API + 効率的な状態管理
- **📱 モバイル対応**: 完全レスポンシブデザイン