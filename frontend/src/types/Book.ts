export interface Author {
  id: number;
  name: string;
  createdAt: string;
}

export interface ReadStatus {
  id: number;
  name: string;
  description: string;
}

export interface Genre {
  id: number;
  name: string;
}

export interface BookAuthor {
  bookId: number;
  authorId: number;
  author: Author;
}

export interface Book {
  id: number;
  title: string;
  publisher?: string;
  publishedDate?: string;
  isbn?: string;
  readStatus?: ReadStatus;
  genre?: Genre;
  createdAt: string;
  bookAuthors: BookAuthor[];
}

export interface BookRequest {
  title: string;
  publisher?: string;
  publishedDate?: string;
  isbn?: string;
  readStatus?: string; // APIに送信する際は文字列として送信
  genreId?: number;
  authorNames: string[];
}

export const ReadStatusNames = {
  NOT_READ: '未読',
  READING: '読書中',
  FINISHED: '読了',
  ON_HOLD: '中断中'
} as const;

export type ReadStatusType = typeof ReadStatusNames[keyof typeof ReadStatusNames];