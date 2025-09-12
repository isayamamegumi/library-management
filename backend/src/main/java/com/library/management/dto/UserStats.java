package com.library.management.dto;

import java.time.LocalDate;

public class UserStats {
    private Long userId;
    private String username;
    private Integer totalBooks;      // 総登録書籍数
    private Integer completedBooks;  // 読了書籍数
    private Integer readingBooks;    // 読書中書籍数
    private Double progressRate;     // 読了率
    private LocalDate targetMonth;   // 集計対象月
    private String favoriteGenre;    // 最も多く読むジャンル

    public UserStats() {}

    public UserStats(Long userId, String username, Integer totalBooks, Integer completedBooks, 
                    Integer readingBooks, Double progressRate, LocalDate targetMonth, String favoriteGenre) {
        this.userId = userId;
        this.username = username;
        this.totalBooks = totalBooks;
        this.completedBooks = completedBooks;
        this.readingBooks = readingBooks;
        this.progressRate = progressRate;
        this.targetMonth = targetMonth;
        this.favoriteGenre = favoriteGenre;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getTotalBooks() {
        return totalBooks;
    }

    public void setTotalBooks(Integer totalBooks) {
        this.totalBooks = totalBooks;
    }

    public Integer getCompletedBooks() {
        return completedBooks;
    }

    public void setCompletedBooks(Integer completedBooks) {
        this.completedBooks = completedBooks;
    }

    public Integer getReadingBooks() {
        return readingBooks;
    }

    public void setReadingBooks(Integer readingBooks) {
        this.readingBooks = readingBooks;
    }

    public Double getProgressRate() {
        return progressRate;
    }

    public void setProgressRate(Double progressRate) {
        this.progressRate = progressRate;
    }

    public LocalDate getTargetMonth() {
        return targetMonth;
    }

    public void setTargetMonth(LocalDate targetMonth) {
        this.targetMonth = targetMonth;
    }

    public String getFavoriteGenre() {
        return favoriteGenre;
    }

    public void setFavoriteGenre(String favoriteGenre) {
        this.favoriteGenre = favoriteGenre;
    }
}