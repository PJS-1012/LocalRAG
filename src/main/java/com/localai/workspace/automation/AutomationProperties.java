package com.localai.workspace.automation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix="localrag.automation")
public record AutomationProperties(Duration pollDelay,Duration initialDelay,int maxLogResults) {
    public AutomationProperties {
        if(pollDelay==null || pollDelay.isNegative() || pollDelay.isZero()) pollDelay=Duration.ofSeconds(30);
        if(initialDelay==null || initialDelay.isNegative()) initialDelay=Duration.ofSeconds(60);
        if(maxLogResults<1 || maxLogResults>100) maxLogResults=50;
    }
}
