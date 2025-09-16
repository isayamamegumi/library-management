package com.library.management.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * アプリケーション起動時にFlywayの修復処理を実行
 */
// @Component  // 修復完了後は無効化
public class FlywayRepairRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(FlywayRepairRunner.class);

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            logger.info("Executing Flyway repair before migration...");

            Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(false)
                .outOfOrder(true)
                .load();

            // 修復処理を実行
            flyway.repair();
            logger.info("Flyway repair completed successfully");

            // 情報を出力
            var info = flyway.info();
            logger.info("Migration info after repair:");
            for (var migration : info.all()) {
                logger.info("  Version: {}, Description: {}, State: {}",
                    migration.getVersion(),
                    migration.getDescription(),
                    migration.getState());
            }

        } catch (Exception e) {
            logger.error("Failed to repair Flyway: {}", e.getMessage(), e);
            // 修復に失敗した場合はベースラインを試行
            try {
                logger.info("Attempting flyway baseline...");
                Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .validateOnMigrate(false)
                    .load();

                flyway.baseline();
                logger.info("Flyway baseline completed successfully");
            } catch (Exception baselineException) {
                logger.error("Flyway baseline also failed: {}", baselineException.getMessage(), baselineException);
            }
        }
    }
}