package ru.dot.clipshot;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ReleaseInfoTest {
    public static void main(String[] args) throws Exception {
        check(ReleaseInfo.compare("1.0", "v1.0.0") == 0);
        check(ReleaseInfo.compare("1.9", "1.10") < 0);
        check(ReleaseInfo.compare("1.0.1", "v1.0") > 0);
        check(ReleaseInfo.compare("1.0.0_b.1", "v1.0") > 0);
        check(ReleaseInfo.compare("1.0.0_b.2", "1.0.0_b.1") > 0);
        check(ReleaseInfo.compare("1.0.0_b.10", "1.0.0_b.2") > 0);
        check(ReleaseInfo.compare("1.0.0_b.10", "1.0.1") < 0);
        check(ReleaseInfo.compare("1.0.1_b.1", "1.0.0_b.10") > 0);
        try {
            ReleaseInfo.compare("1.1-beta", "1.0");
            throw new AssertionError("unsupported tag");
        } catch (IllegalArgumentException expected) {}

        JSONObject release = new JSONObject().put("tag_name", "v1.10");
        release.put("prerelease", false).put("draft", false);
        check(ReleaseInfo.isStableRelease(release));
        check(!ReleaseInfo.isStableRelease(new JSONObject(release.toString()).put("draft", true)));
        check(!ReleaseInfo.isStableRelease(new JSONObject(release.toString()).put("prerelease", true)));
        check(!ReleaseInfo.isStableRelease(new JSONObject(release.toString()).put("tag_name", "v1.10_b.1")));
        check(ReleaseInfo.compare("1.0.0_b.1", "1.0.1") < 0);
        JSONArray assets = new JSONArray();
        release.put("assets", assets);
        check(ReleaseInfo.parse(release).download == null);
        String url = ReleaseInfo.REPO + "/releases/download/v1.10/SkriNim-1.10.apk";
        JSONObject apk = new JSONObject().put("name", "SkriNim-1.10.apk").put("size", 123)
                .put("state", "uploaded").put("browser_download_url", url);
        assets.put(apk);
        check(ReleaseInfo.parse(release).download == null);
        apk.put("digest", "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        ReleaseInfo candidate = ReleaseInfo.parse(release);
        check(candidate.download.equals(url) && candidate.version.equals("1.10") && candidate.size == 123);
        apk.put("browser_download_url", "https://example.org/SkriNim-1.10.apk");
        check(ReleaseInfo.parse(release).download == null);
        System.out.println("ReleaseInfoTest passed");
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError();
    }
}
