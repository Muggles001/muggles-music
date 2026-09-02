package top.boluofan.musictv.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class PlaylistLinkParserTest {
    @Test
    public void parsesFivePlatformLinksAndShareText() throws Exception {
        assertReference("wy", "123456",
                "分享歌单 https://music.163.com/#/playlist?id=123456 (来自网易云)");
        assertReference("wy", "2345678901",
                "分享歌单: 测试用户喜欢的音乐 "
                        + "https://music.163.com/m/playlist?id=2345678901&creatorId=1020304050");
        assertReference("tx", "998877",
                "https://y.qq.com/n/ryqq/playlist/998877");
        assertReference("tx", "2468013579",
                "https://i2.y.qq.com/n3/other/pages/details/playlist.html?platform=11"
                        + "&appshare=android_qq&hosteuin=test-share-token"
                        + "&id=2468013579&ADTAG=wxfshare");
        assertReference("kw", "556677",
                "https://www.kuwo.cn/playlist_detail/556677");
        assertReference("kw", "2736267853",
                "https://m.kuwo.cn/h5app/playlist/2736267853?t=qqfriend");
        assertReference("kw", "884422",
                "https://h5app.kuwo.cn/m/bodian/collection.html?uid=1"
                        + "&playlistId=884422&source=5&ownerId=1");
        assertReference("kg", "334455",
                "https://www.kugou.com/yy/special/single/334455.html");
        PlaylistLinkParser.Result kugou = PlaylistLinkParser.parse(
                "https://m.kugou.com/songlist/gcid_a1b2c3d4e5f678/"
                        + "?src_cid=a1b2c3d4e5f678&uid=1357924680&chl=wechat&iszlist=1",
                "auto");
        assertEquals("kg", kugou.getReference().getSource());
        assertEquals("gcid_a1b2c3d4e5f678", kugou.getReference().getSourceListId());
        assertTrue(kugou.getReference().getDetailRequestId().startsWith(
                "https://m.kugou.com/songlist/gcid_a1b2c3d4e5f678/"));
        assertReference("mg", "112233",
                "https://music.migu.cn/v3/music/playlist/112233");
        assertReference("mg", "184187437",
                "https://h5.nf.migu.cn/app/v4/p/share/playlist/index.html"
                        + "?id=184187437&channel=0146921");
    }

    @Test
    public void parsesWrappedEscapedAndEntityEncodedShareText() throws Exception {
        String kugou = "https://m.kugou.com/songlist/gcid_a1b2c3d4e5f678/"
                + "?src_cid=a1b2c3d4e5f678&uid=1357924680&chl=wechat&iszlist=1";
        PlaylistLinkParser.Result kugouResult = PlaylistLinkParser.parse(
                "[" + kugou + "](" + kugou.replace("&", "\\&") + ")", "auto");
        assertEquals("kg", kugouResult.getReference().getSource());
        assertEquals("gcid_a1b2c3d4e5f678",
                kugouResult.getReference().getSourceListId());
        assertEquals(kugou, kugouResult.getReference().getDetailRequestId());

        assertReference("tx", "2468013579",
                "QQ 音乐 https://i2.y.qq.com/n3/other/pages/details/playlist.html"
                        + "?platform=11&amp;appshare=android_qq&amp;id=2468013579"
                        + "&amp;ADTAG=wxfshare");
        assertReference("tx", "2468013579",
                "https://i2.y.qq.com/n3/other/pages/details/playlist.html"
                        + "?platform=11&#38;id=2468013579");
        assertReference("tx", "2468013579",
                "https://i2.y.qq.com/n3/other/pages/details/playlist.html"
                        + "?platform=11&#x26;id=2468013579");
    }

    @Test
    public void removesInvisibleFormattingFromSharedUrl() throws Exception {
        assertReference("tx", "2468013579",
                "https\u200b://i2.y\ufeff.qq.com/n3/other/pages/details/playlist.html"
                        + "?platform=11&i\u2060d=2468013\u200b579");
    }

    @Test
    public void triesSupportedPlaylistAfterUnrelatedUrl() throws Exception {
        assertReference("kg", "gcid_a1b2c3d4e5f678",
                "使用说明 https://example.com/help 歌单 "
                        + "https://m.kugou.com/songlist/gcid_a1b2c3d4e5f678/"
                        + "?src_cid=a1b2c3d4e5f678&uid=1357924680");
    }

    @Test
    public void markdownTargetCannotFallBackToVisibleLabel() {
        assertFailure(PlaylistLinkParser.Kind.UNSUPPORTED_HOST,
                "[https://music.163.com/playlist?id=123456]"
                        + "(https://attacker.test/playlist?id=9)", "auto");
    }

    @Test
    public void chinesePunctuationTerminatesUrlCandidate() throws Exception {
        String kugou = "https://m.kugou.com/songlist/gcid_a1b2c3d4e5f678/"
                + "?src_cid=a1b2c3d4e5f678&uid=1357924680";
        PlaylistReference reference = PlaylistLinkParser.parse(kugou + "，"
                + "https://i2.y.qq.com/n3/other/pages/details/playlist.html?id=2468013579",
                "auto").getReference();

        assertEquals("kg", reference.getSource());
        assertEquals("gcid_a1b2c3d4e5f678", reference.getSourceListId());
        assertEquals(kugou, reference.getDetailRequestId());
    }

    @Test
    public void explicitUrlPlatformOverridesStaleHint() throws Exception {
        PlaylistReference reference = PlaylistLinkParser.parse(
                "https://i2.y.qq.com/n3/other/pages/details/playlist.html?id=2468013579",
                "kg").getReference();

        assertEquals("tx", reference.getSource());
        assertEquals("2468013579", reference.getSourceListId());
    }

    @Test
    public void equivalentIdentifiersNormalizeToSameIdentity() throws Exception {
        PlaylistReference link = PlaylistLinkParser.parse(
                "https://music.163.com/playlist?id=123456", "auto").getReference();
        PlaylistReference prefixed = PlaylistLinkParser.parse("wy__123456", "auto")
                .getReference();
        PlaylistReference hinted = PlaylistLinkParser.parse("123456", "wy")
                .getReference();

        assertEquals(link.identityKey(), prefixed.identityKey());
        assertEquals(link.identityKey(), hinted.identityKey());
    }

    @Test
    public void shortLinksRequireControlledExpansion() throws Exception {
        PlaylistLinkParser.Result result = PlaylistLinkParser.parse(
                "https://t.kugou.com/AbCdEf", "auto");

        assertTrue(result.requiresExpansion());
        assertEquals("kg", result.getSource());
        assertEquals("https://t.kugou.com/AbCdEf", result.getExpansionUrl());
    }

    @Test
    public void rejectsUnsafeAmbiguousAndDeceptiveInput() throws Exception {
        assertFailure(PlaylistLinkParser.Kind.PLATFORM_REQUIRED, "123456", "auto");
        assertFailure(PlaylistLinkParser.Kind.UNSUPPORTED_HOST,
                "https://music.163.com.attacker.test/playlist?id=123456", "auto");
        assertFailure(PlaylistLinkParser.Kind.UNSAFE_URL,
                "https://user@music.163.com/playlist?id=123456", "auto");
        assertFailure(PlaylistLinkParser.Kind.UNSAFE_URL,
                "https://music.163.com:8443/playlist?id=123456", "auto");
    }

    private static void assertReference(String source, String id, String input) throws Exception {
        PlaylistLinkParser.Result result = PlaylistLinkParser.parse(input, "auto");
        assertEquals(false, result.requiresExpansion());
        assertEquals(source, result.getReference().getSource());
        assertEquals(id, result.getReference().getSourceListId());
    }

    private static void assertFailure(PlaylistLinkParser.Kind kind, String input, String source) {
        try {
            PlaylistLinkParser.parse(input, source);
            fail("Expected " + kind);
        } catch (PlaylistLinkParser.Failure failure) {
            assertEquals(kind, failure.getKind());
        }
    }
}
