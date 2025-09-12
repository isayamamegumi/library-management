package com.library.management.batch;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class BatchMonitoringService {
    
    @Autowired
    private JobExplorer jobExplorer;
    
    public List<String> getAllJobNames() {
        return jobExplorer.getJobNames();
    }
    
    public List<JobInstance> getJobInstances(String jobName, int start, int count) {
        return jobExplorer.getJobInstances(jobName, start, count);
    }
    
    public List<JobExecution> getJobExecutions(JobInstance jobInstance) {
        return jobExplorer.getJobExecutions(jobInstance);
    }
    
    public JobExecution getJobExecution(Long executionId) {
        return jobExplorer.getJobExecution(executionId);
    }
    
    public Set<JobExecution> getRunningExecutions(String jobName) {
        return jobExplorer.findRunningJobExecutions(jobName);
    }
    
    public boolean isJobRunning(String jobName) {
        return !getRunningExecutions(jobName).isEmpty();
    }
}