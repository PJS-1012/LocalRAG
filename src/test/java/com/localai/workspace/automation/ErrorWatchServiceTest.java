package com.localai.workspace.automation;

import com.localai.workspace.agent.*;
import com.localai.workspace.errors.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ErrorWatchServiceTest {
    @Test void newErrorIsRedactedComparedAndDeduplicatedOnTheNextRun() {
        ErrorProjectScope scope=mock(ErrorProjectScope.class);
        LogReadOnlyService logs=mock(LogReadOnlyService.class);
        ErrorSimilarityService similarities=mock(ErrorSimilarityService.class);
        DetectedErrorEventRepository events=mock(DetectedErrorEventRepository.class);
        when(scope.require("P")).thenReturn("P");
        var entry=new LogEntry("2026-09-14T00:00:00Z","ERROR",
                "IllegalStateException token=super-secret","logs/app.log",42L);
        when(logs.getRecentErrors("P",50)).thenReturn(new LogInspectionResult("P",LogToolStatus.SUCCESS,
                1,1,0,1,false,50,1,1,List.of(entry),null));
        when(events.existsByProjectIdAndFingerprint(eq("P"),any())).thenReturn(false,true);
        when(events.saveAndFlush(any())).thenAnswer(invocation->{
            DetectedErrorEvent value=invocation.getArgument(0);value.id=7L;return value;
        });
        var match=new ErrorSimilarResult(9L,ErrorStatus.RESOLVED,"VERIFIED",0.81,
                "IllegalStateException","old","cause","fix",Instant.EPOCH,List.of(),List.of());
        when(similarities.find(any())).thenReturn(new ErrorSimilarityResponse("SUCCESS","P",5,0.45,1,
                1,1,2,null,List.of(match)));
        var service=new ErrorWatchService(scope,logs,similarities,events,new LogSecretRedactor(),
                new AutomationProperties(Duration.ofSeconds(30),Duration.ofMinutes(1),50));

        ErrorWatchResult first=service.watch("P");
        ErrorWatchResult second=service.watch("P");

        assertThat(first.newErrorCount()).isEqualTo(1);
        assertThat(first.newErrors().get(0).errorMessage()).doesNotContain("super-secret");
        assertThat(first.newErrors().get(0).similarErrors()).hasSize(1);
        assertThat(second.newErrorCount()).isZero();
        assertThat(second.duplicateCount()).isEqualTo(1);
        verify(similarities,times(1)).find(any());
        verify(events,times(1)).saveAndFlush(any());
    }
}
