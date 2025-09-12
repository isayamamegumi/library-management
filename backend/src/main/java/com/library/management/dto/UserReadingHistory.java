package com.library.management.dto;

import java.time.LocalDate;
import java.util.List;

public class UserReadingHistory {
    private Long userId;
    private String username;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer totalBooksRegistered;    // 登録総数
    private Integer totalBooksCompleted;     // 読了総数
    private Integer booksInProgress;         // 読書中
    private Integer booksOnHold;            // 中断中
    private Double completionRate;          // 読了率
    private List<String> favoriteGenres;   // 好みジャンル（上位3つ）
    private List<String> favoriteAuthors;  // 好み著者（上位3つ）
    private Double averageReadingDays;      // 平均読書日数
    private Integer readingStreak;          // 連続読書日数
    private String readingPattern;          // 読書パターン（朝型/夜型など）

    public UserReadingHistory() {}

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

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Integer getTotalBooksRegistered() {
        return totalBooksRegistered;
    }

    public void setTotalBooksRegistered(Integer totalBooksRegistered) {
        this.totalBooksRegistered = totalBooksRegistered;
    }

    public Integer getTotalBooksCompleted() {
        return totalBooksCompleted;
    }

    public void setTotalBooksCompleted(Integer totalBooksCompleted) {
        this.totalBooksCompleted = totalBooksCompleted;
    }

    public Integer getBooksInProgress() {
        return booksInProgress;
    }

    public void setBooksInProgress(Integer booksInProgress) {
        this.booksInProgress = booksInProgress;
    }

    public Integer getBooksOnHold() {
        return booksOnHold;
    }

    public void setBooksOnHold(Integer booksOnHold) {
        this.booksOnHold = booksOnHold;
    }

    public Double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(Double completionRate) {
        this.completionRate = completionRate;
    }

    public List<String> getFavoriteGenres() {
        return favoriteGenres;
    }

    public void setFavoriteGenres(List<String> favoriteGenres) {
        this.favoriteGenres = favoriteGenres;
    }

    public List<String> getFavoriteAuthors() {
        return favoriteAuthors;
    }

    public void setFavoriteAuthors(List<String> favoriteAuthors) {
        this.favoriteAuthors = favoriteAuthors;
    }

    public Double getAverageReadingDays() {
        return averageReadingDays;
    }

    public void setAverageReadingDays(Double averageReadingDays) {
        this.averageReadingDays = averageReadingDays;
    }

    public Integer getReadingStreak() {
        return readingStreak;
    }

    public void setReadingStreak(Integer readingStreak) {
        this.readingStreak = readingStreak;
    }

    public String getReadingPattern() {
        return readingPattern;
    }

    public void setReadingPattern(String readingPattern) {
        this.readingPattern = readingPattern;
    }
}