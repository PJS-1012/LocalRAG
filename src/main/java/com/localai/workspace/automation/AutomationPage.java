package com.localai.workspace.automation;

import java.util.List;

public record AutomationPage<T>(List<T> content,long totalElements,int totalPages,int page,int size) { }
