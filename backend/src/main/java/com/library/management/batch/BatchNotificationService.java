package com.library.management.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Service;

@Service
public class BatchNotificationService implements JobExecutionListener {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchNotificationService.class);
    
    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("バッチジョブ開始: {} - 実行ID: {}", 
                   jobExecution.getJobInstance().getJobName(), 
                   jobExecution.getId());
    }
    
    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        Long executionId = jobExecution.getId();
        BatchStatus status = jobExecution.getStatus();
        
        String message = String.format("ジョブ名: %s, 実行ID: %d, ステータス: %s", 
                                      jobName, executionId, status);
        
        if (status == BatchStatus.COMPLETED) {
            logger.info("バッチジョブ正常終了: {}", message);
        } else if (status == BatchStatus.FAILED) {
            logger.error("バッチジョブ異常終了: {}", message);
        } else {
            logger.warn("バッチジョブ異常状態で終了: {}", message);
        }
    }
    
    public void sendCustomNotification(String title, String content) {
        logger.info("=== カスタム通知 ===");
        logger.info("タイトル: {}", title);
        logger.info("内容: {}", content);
    }
}