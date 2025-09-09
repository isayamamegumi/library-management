/**
 * 入力値サニタイゼーション用のユーティリティ関数
 */

/**
 * HTMLタグと危険な文字を除去
 * @param input 入力文字列
 * @returns サニタイズされた文字列
 */
export const sanitizeHtml = (input: string): string => {
  return input
    .replace(/[<>]/g, '') // HTMLタグの除去
    .replace(/javascript:/gi, '') // javascript:スキームの除去
    .replace(/on\w+=/gi, '') // イベントハンドラーの除去
    .trim();
};

/**
 * 基本的な入力値のサニタイゼーション
 * @param input 入力文字列
 * @param maxLength 最大長（デフォルト: 255）
 * @returns サニタイズされた文字列
 */
export const sanitizeInput = (input: string, maxLength: number = 255): string => {
  return sanitizeHtml(input)
    .slice(0, maxLength);
};

/**
 * 検索クエリのサニタイゼーション
 * @param query 検索クエリ
 * @returns サニタイズされたクエリ
 */
export const sanitizeSearchQuery = (query: string): string => {
  return query
    .replace(/[<>]/g, '')
    .replace(/['"]/g, '')
    .replace(/;/g, '')
    .trim()
    .slice(0, 100);
};

/**
 * ファイル名のサニタイゼーション
 * @param filename ファイル名
 * @returns サニタイズされたファイル名
 */
export const sanitizeFilename = (filename: string): string => {
  return filename
    .replace(/[<>:"/\\|?*]/g, '')
    .replace(/\s+/g, '_')
    .trim()
    .slice(0, 255);
};

/**
 * URLパラメータのサニタイゼーション
 * @param param URLパラメータ
 * @returns サニタイズされたパラメータ
 */
export const sanitizeUrlParam = (param: string): string => {
  return encodeURIComponent(
    param
      .replace(/[<>]/g, '')
      .trim()
      .slice(0, 100)
  );
};

/**
 * XSS攻撃を防ぐための文字列エスケープ
 * @param str エスケープする文字列
 * @returns エスケープされた文字列
 */
export const escapeHtml = (str: string): string => {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
};

/**
 * SQLインジェクション対策用の文字列サニタイゼーション
 * @param input 入力文字列
 * @returns サニタイズされた文字列
 */
export const sanitizeSqlInput = (input: string): string => {
  return input
    .replace(/['"]/g, '') // クォートの除去
    .replace(/;/g, '') // セミコロンの除去
    .replace(/--/g, '') // SQLコメントの除去
    .replace(/\/\*/g, '') // マルチラインコメント開始の除去
    .replace(/\*\//g, '') // マルチラインコメント終了の除去
    .replace(/union/gi, '') // UNION攻撃対策
    .replace(/select/gi, '') // SELECT攻撃対策
    .replace(/insert/gi, '') // INSERT攻撃対策
    .replace(/update/gi, '') // UPDATE攻撃対策
    .replace(/delete/gi, '') // DELETE攻撃対策
    .replace(/drop/gi, '') // DROP攻撃対策
    .trim();
};

/**
 * CSRFトークンの検証（ダミー実装）
 * 実際の実装ではサーバーサイドでの検証が必要
 * @param token CSRFトークン
 * @returns 検証結果（常にtrue - 実装例として）
 */
export const validateCsrfToken = (token: string): boolean => {
  // 実際の実装では、サーバーから取得したトークンと照合
  // ここではクライアントサイドでの基本的な検証例
  return !!(token && token.length >= 32 && /^[a-zA-Z0-9]+$/.test(token));
};

/**
 * 入力値の長さ制限チェック
 * @param input 入力値
 * @param maxLength 最大長
 * @returns チェック結果
 */
export const validateInputLength = (input: string, maxLength: number): boolean => {
  return input.length <= maxLength;
};

/**
 * 危険なパターンの検出
 * @param input 入力値
 * @returns 危険なパターンが検出された場合true
 */
export const containsMaliciousPattern = (input: string): boolean => {
  const maliciousPatterns = [
    /javascript:/i,
    /data:text\/html/i,
    /vbscript:/i,
    /on\w+\s*=/i,
    /<script/i,
    /<iframe/i,
    /<object/i,
    /<embed/i,
    /eval\(/i,
    /setTimeout\(/i,
    /setInterval\(/i,
    /function\s*\(/i,
  ];

  return maliciousPatterns.some(pattern => pattern.test(input));
};