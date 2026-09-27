package com.localai.workspace.overview;

import java.util.*;

/** Path metadata classification, not a read authorization policy or semantic intent router. */
public final class ProjectEvidencePolicy {
    public enum Origin { PROJECT, THIRD_PARTY, GENERATED, DOCUMENTATION }
    private static final Set<String> GENERATED=Set.of("library","temp","logs","build","target","dist",
            ".gradle",".git","obj","bin","generated","coverage");
    private static final Set<String> EXTERNAL=Set.of("node_modules","vendor","third-party","third_party",
            "thirdparty","external","packagecache","textmesh pro","textmeshpro");
    private static final Set<String> BUILDS=Set.of("build.gradle","build.gradle.kts","pom.xml","package.json",
            "cargo.toml","projectversion.txt","manifest.json");
    private ProjectEvidencePolicy() {}
    public static String normalize(String path) {
        return path.replace('\\','/').toLowerCase(Locale.ROOT).replaceAll("^\\./","");
    }
    public static Origin origin(String path) {
        String p=normalize(path),name=p.substring(p.lastIndexOf('/')+1);
        List<String> parts=Arrays.asList(p.split("/"));
        if(parts.stream().anyMatch(GENERATED::contains) || name.endsWith(".lock")
                || Set.of("package-lock.json","yarn.lock","pnpm-lock.yaml","packages-lock.json").contains(name)
                || name.endsWith(".g.cs") || name.endsWith(".generated.cs")
                || p.contains("/performance/")&&p.contains("/results/")
                || p.startsWith("performance/")&&p.contains("/results/"))return Origin.GENERATED;
        if(parts.stream().anyMatch(EXTERNAL::contains) || p.startsWith("assets/plugins/")
                || p.startsWith("assets/fonts/") || p.contains("/emoji/") || name.startsWith("emojione"))
            return Origin.THIRD_PARTY;
        if(name.endsWith(".md") || p.startsWith("docs/") || name.startsWith("license")
                || name.contains("attribution") || name.equals("ofl.txt"))return Origin.DOCUMENTATION;
        return Origin.PROJECT;
    }
    public static boolean allowed(String path,String originalQuery) {
        Origin origin=origin(path);
        if(origin==Origin.GENERATED)return false;
        String p=normalize(path),q=originalQuery.toLowerCase(Locale.ROOT),name=p.substring(p.lastIndexOf('/')+1);
        if(origin==Origin.THIRD_PARTY) {
            // Only an explicitly named dependency/path grants access to its optional evidence.
            if(p.contains("textmesh pro")||p.contains("textmeshpro"))
                return q.contains("textmesh pro")||q.contains("textmeshpro");
            for(String part:p.split("/")) {
                if(part.length()>3&&!part.contains(".")&&!Set.of("assets","plugins","vendor","external","node_modules",
                        "third-party","third_party","fonts","resources","scripts","readme","documentation","docs").contains(part)
                        && q.contains(part))return true;
            }
            return false;
        }
        if(name.equals("agents.md")||name.equals("claude.md")||name.startsWith("license")
                ||name.contains("attribution")||name.equals("ofl.txt"))
            return q.contains(name);
        return true;
    }
    public static int priority(String path,String query) {
        String p=normalize(path),name=p.substring(p.lastIndexOf('/')+1);
        String stem=name.replaceFirst("\\.[^.]+$","");
        if(stem.length()>3&&query.toLowerCase(Locale.ROOT).contains(stem))return 0;
        if(origin(p)==Origin.THIRD_PARTY)return allowed(p,query)?0:20;
        if(name.startsWith("readme")&&!p.contains("/"))return 1;
        if(BUILDS.contains(name))return 2;
        if(p.contains("/editor/")||p.contains("/test")||name.endsWith("test.java")||name.endsWith("tests.cs"))return 8;
        if(name.endsWith("application.java")||name.equals("program.cs")||name.endsWith("bootstrap.cs"))return 3;
        if(p.startsWith("assets/_project/scripts/")||p.startsWith("assets/scripts/"))return 4;
        if(name.endsWith("controller.java")||name.endsWith("service.java"))return 4;
        if(p.contains("/domain/")||p.contains("/config/"))return 5;
        if(p.startsWith("projectsettings/"))return 5;
        if(p.startsWith("docs/decisions/"))return 6;
        if(origin(p)==Origin.DOCUMENTATION)return 7;
        if(p.contains("/migration/"))return 9;
        return 10;
    }
    public static String module(String path) {
        String p=normalize(path);
        for(String prefix:List.of("assets/_project/scripts/","assets/scripts/","src/main/java/")) {
            if(p.startsWith(prefix)) {
                String tail=p.substring(prefix.length());
                if(prefix.startsWith("assets"))return prefix+tail.split("/")[0];
                int slash=tail.lastIndexOf('/');
                return slash<0?tail:tail.substring(0,slash);
            }
        }
        return p;
    }
}
