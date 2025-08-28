export interface Author {
  id: number;
  name: string;
  createdAt: string;
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
  readStatus?: string;
  createdAt: string;
  bookAuthors: BookAuthor[];
}

export interface BookRequest {
  title: string;
  publisher?: string;
  publishedDate?: string;
  isbn?: string;
  readStatus?: string;
  authorNames: string[];
}

export const ReadStatus = {
  NOT_READ: '未読',
  READING: '読書中',
  FINISHED: '読了',
  ON_HOLD: '中断中'
} as const;

export type ReadStatusType = typeof ReadStatus[keyof typeof ReadStatus];