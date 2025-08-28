# 蔵書管理システム

Java Spring Boot + React TypeScriptで構築された蔵書管理アプリケーションです。

## 機能

- 書籍の追加・編集・削除・検索
- 著者情報の管理
- 読書状況の管理（未読、読書中、読了、中断中）
- ISBN、出版社、出版日などの詳細情報管理

## 技術スタック

### バックエンド
- Java 17
- Spring Boot 3.1.0
- Spring Data JPA
- PostgreSQL
- Maven

### フロントエンド
- React 18
- TypeScript
- Axios
- CSS3

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
-- booksテーブル
CREATE TABLE books (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    publisher VARCHAR(255),
    published_date DATE,
    isbn VARCHAR(13) UNIQUE,
    read_status VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);

-- authorsテーブル
CREATE TABLE authors (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- book_authorsテーブル
CREATE TABLE book_authors (
    book_id INTEGER REFERENCES books(id),
    author_id INTEGER REFERENCES authors(id),
    PRIMARY KEY (book_id, author_id)
);
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

### Books API
- `GET /api/books` - 全書籍取得（検索・フィルタリング対応）
- `GET /api/books/{id}` - 書籍詳細取得
- `POST /api/books` - 新規書籍追加
- `PUT /api/books/{id}` - 書籍更新
- `DELETE /api/books/{id}` - 書籍削除
- `GET /api/books/isbn/{isbn}` - ISBN検索

### Authors API
- `GET /api/authors` - 全著者取得
- `GET /api/authors/{id}` - 著者詳細取得
- `POST /api/authors` - 新規著者追加
- `PUT /api/authors/{id}` - 著者更新
- `DELETE /api/authors/{id}` - 著者削除

## 使用方法

1. ブラウザで http://localhost:3000 にアクセス
2. 「新しい書籍を追加」ボタンから書籍を登録
3. 書籍一覧から編集・削除が可能
4. 検索欄でタイトル、出版社、著者で検索
5. ドロップダウンで読書状況によるフィルタリングが可能

## 読書状況の種類

- 未読
- 読書中
- 読了
- 中断中