package com.library.management.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class BookRanking {
    private String rankingType;           // "POPULARITY", "COMPLETION_RATE", "AUTHOR", "GENRE"
    private LocalDateTime generatedAt;
    private List<RankingItem> rankings;
    private RankingMetadata metadata;

    public BookRanking() {}

    public String getRankingType() {
        return rankingType;
    }

    public void setRankingType(String rankingType) {
        this.rankingType = rankingType;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<RankingItem> getRankings() {
        return rankings;
    }

    public void setRankings(List<RankingItem> rankings) {
        this.rankings = rankings;
    }

    public RankingMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(RankingMetadata metadata) {
        this.metadata = metadata;
    }

    public static class RankingItem {
        private Integer rank;
        private String title;
        private String publisher;
        private String authorName;
        private String genre;
        private Integer registrationCount;    // 登録数
        private Integer completionCount;      // 読了数
        private Double completionRate;        // 読了率
        private Double popularityScore;       // 人気スコア

        public RankingItem() {}

        public Integer getRank() {
            return rank;
        }

        public void setRank(Integer rank) {
            this.rank = rank;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getPublisher() {
            return publisher;
        }

        public void setPublisher(String publisher) {
            this.publisher = publisher;
        }

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        public Integer getRegistrationCount() {
            return registrationCount;
        }

        public void setRegistrationCount(Integer registrationCount) {
            this.registrationCount = registrationCount;
        }

        public Integer getCompletionCount() {
            return completionCount;
        }

        public void setCompletionCount(Integer completionCount) {
            this.completionCount = completionCount;
        }

        public Double getCompletionRate() {
            return completionRate;
        }

        public void setCompletionRate(Double completionRate) {
            this.completionRate = completionRate;
        }

        public Double getPopularityScore() {
            return popularityScore;
        }

        public void setPopularityScore(Double popularityScore) {
            this.popularityScore = popularityScore;
        }
    }

    public static class RankingMetadata {
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private Integer totalBooksAnalyzed;
        private Integer totalUsersInvolved;
        private String rankingCriteria;

        public RankingMetadata() {}

        public LocalDate getPeriodStart() {
            return periodStart;
        }

        public void setPeriodStart(LocalDate periodStart) {
            this.periodStart = periodStart;
        }

        public LocalDate getPeriodEnd() {
            return periodEnd;
        }

        public void setPeriodEnd(LocalDate periodEnd) {
            this.periodEnd = periodEnd;
        }

        public Integer getTotalBooksAnalyzed() {
            return totalBooksAnalyzed;
        }

        public void setTotalBooksAnalyzed(Integer totalBooksAnalyzed) {
            this.totalBooksAnalyzed = totalBooksAnalyzed;
        }

        public Integer getTotalUsersInvolved() {
            return totalUsersInvolved;
        }

        public void setTotalUsersInvolved(Integer totalUsersInvolved) {
            this.totalUsersInvolved = totalUsersInvolved;
        }

        public String getRankingCriteria() {
            return rankingCriteria;
        }

        public void setRankingCriteria(String rankingCriteria) {
            this.rankingCriteria = rankingCriteria;
        }
    }
}