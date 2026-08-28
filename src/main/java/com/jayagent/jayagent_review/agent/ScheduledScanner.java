package com.jayagent.jayagent_review.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledScanner {

    // 标准 SLF4J 日志，记录定时任务启停、风险告警；
    private static final Logger log = LoggerFactory.getLogger(ScheduledScanner.class);

    // 构造器注入 JayAgentScanner：复用已经封装好的 RAG+LLM 扫描能力
    // 对象实例一旦创建不可修改，线程安全
    private final JayAgentScanner scanner;

    public ScheduledScanner(JayAgentScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * Cron 表达式含义：秒 分 时 日 月 星期
     * 0 0 10 * * ? -> 每天上午 10 点自动巡检一次
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void dailyScan() {
        log.info("定时巡检开始");

        // 实际项目中，这里会：
        // 1. 调用 GitHub/GitLab API，获取仓库最新代码
        // 2. 遍历所有文件，对关键文件进行扫描
        // 3. 发现风险后发送企业微信告警
        // 当前 Demo：模拟一次扫描
        JayAgentReport report = scanner.scan(
                "模拟的代码变更内容",
                "定时巡检",
                "金融"
        );

        if (report.isHighRisk()) {
            log.warn("巡检发现高风险项，评分：{}", report.getScore());
        } else {
            log.info("巡检完成，未发现风险");
        }
    }
}
