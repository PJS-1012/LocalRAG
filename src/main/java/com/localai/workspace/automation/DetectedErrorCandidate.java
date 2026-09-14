package com.localai.workspace.automation;

import com.localai.workspace.errors.ErrorSimilarResult;
import java.util.List;

public record DetectedErrorCandidate(
        Long id,String fingerprint,String logTimestamp,String sourceFile,Long lineNumber,
        String level,String errorMessage,String similarLookupStatus,List<ErrorSimilarResult> similarErrors
) { }
