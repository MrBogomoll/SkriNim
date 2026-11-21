package ru.dot.clipshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigInteger;

final class ReleaseInfo {
    static final String REPO = "https://github.com/MrBogomoll/SkriNim";
    final String version, page, download, digest;
    final long size;

    private ReleaseInfo(String tag, String download, String digest, long size) {
        this.version = number(tag);
        this.page = REPO + "/releases/tag/" + tag;
        this.download = download;
        this.digest = digest;
        this.size = size;
    }

    private static String number(String label) {
        if (label == null || !label.matches("[vV]?[0-9]+(\\.[0-9]+)*(_b\\.[1-9][0-9]*)?"))
            throw new IllegalArgumentException("Неизвестный формат версии");
        return label.replaceFirst("^[vV]", "");
    }

    static boolean isBeta(String version) {
        return version != null && version.contains("_b.");
    }

    static boolean isStableRelease(JSONObject release) throws JSONException {
        return !release.getBoolean("draft") && !release.getBoolean("prerelease")
                && !isBeta(release.getString("tag_name"));
    }

    static int compare(String a, String b) {
        String[] leftVersion = number(a).split("_b\\.");
        String[] rightVersion = number(b).split("_b\\.");
        String[] left = leftVersion[0].split("\\."), right = rightVersion[0].split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            BigInteger x = i < left.length ? new BigInteger(left[i]) : BigInteger.ZERO;
            BigInteger y = i < right.length ? new BigInteger(right[i]) : BigInteger.ZERO;
            int order = x.compareTo(y);
            if (order != 0) return order;
        }
        // A beta is built after its stable base and before the next stable version.
        BigInteger leftBeta = leftVersion.length == 2 ? new BigInteger(leftVersion[1]) : BigInteger.ZERO;
        BigInteger rightBeta = rightVersion.length == 2 ? new BigInteger(rightVersion[1]) : BigInteger.ZERO;
        return leftBeta.compareTo(rightBeta);
    }

    static ReleaseInfo parse(JSONObject release) throws JSONException {
        String tag = release.getString("tag_name");
        String name = "SkriNim-" + number(tag) + ".apk";
        String url = REPO + "/releases/download/" + tag + "/" + name;
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String hash = asset.optString("digest");
            long size = asset.optLong("size");
            if (name.equals(asset.optString("name")) && url.equals(asset.optString("browser_download_url"))
                    && "uploaded".equals(asset.optString("state")) && size > 0
                    && hash.matches("sha256:[0-9a-fA-F]{64}")) {
                return new ReleaseInfo(tag, url, hash.substring(7), size);
            }
        }
        return new ReleaseInfo(tag, null, null, 0);
    }
}
