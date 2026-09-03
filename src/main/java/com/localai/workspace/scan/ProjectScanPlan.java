package com.localai.workspace.scan;

import com.localai.workspace.discovery.DetectedProject;

import java.util.List;

public record ProjectScanPlan(
        DetectedProject project,
        ProjectScanResult summary,
        List<FileScanEntry> files
) {
}
