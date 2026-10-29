package com.glowdeck.tv;

import org.json.JSONObject;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class WledCommandVerifierTest {
    private static JSONObject observed(String json) throws Exception {
        return new JSONObject().put("state", new JSONObject(json)).put("info", new JSONObject());
    }

    @Test public void powerAndBrightnessMustMatchReportedState() throws Exception {
        JSONObject patch = new JSONObject("{\"on\":true,\"bri\":128}");
        assertTrue(WledCommandVerifier.matches(patch, observed("{\"on\":true,\"bri\":128}")));
        assertFalse(WledCommandVerifier.matches(patch, observed("{\"on\":false,\"bri\":128}")));
        assertFalse(WledCommandVerifier.matches(patch, observed("{\"on\":true,\"bri\":110}")));
    }

    @Test public void checkEffectPaletteAndSegmentColors() throws Exception {
        JSONObject patch = new JSONObject("{\"on\":true,\"seg\":[{\"id\":1,\"fx\":3,\"pal\":9,\"sx\":140,\"ix\":90,\"col\":[[255,100,20]]}]}");
        JSONObject observed = observed("{\"on\":true,\"seg\":[{\"id\":0,\"fx\":0},{\"id\":1,\"fx\":3,\"pal\":9,\"sx\":140,\"ix\":90,\"col\":[[255,100,20,0]]}]}");
        assertTrue(WledCommandVerifier.matches(patch, observed));
        observed.getJSONObject("state").getJSONArray("seg").getJSONObject(1).put("pal", 10);
        assertFalse(WledCommandVerifier.matches(patch, observed));
    }

    @Test public void metadataNotExpectedInState() throws Exception {
        JSONObject patch = new JSONObject("{\"on\":true,\"bri\":80,\"tt\":12,\"udpn\":{\"nn\":true}}");
        assertTrue(WledCommandVerifier.matches(patch, observed("{\"on\":true,\"bri\":80}")));
    }

    @Test public void presetMustHaveCorrectActiveSlot() throws Exception {
        assertTrue(WledCommandVerifier.matches(new JSONObject("{\"ps\":31}"), observed("{\"ps\":31}")));
        assertFalse(WledCommandVerifier.matches(new JSONObject("{\"ps\":31}"), observed("{\"ps\":22}")));
    }

    @Test public void savedPresetVerifiedBySlotAndName() throws Exception {
        JSONObject patch = new JSONObject("{\"psave\":12,\"n\":\"Evening\"}");
        assertTrue(WledCommandVerifier.matchesSavedPreset(patch, new JSONObject("{\"12\":{\"n\":\"Evening\"}}")));
        assertFalse(WledCommandVerifier.matchesSavedPreset(patch, new JSONObject("{\"12\":{\"n\":\"Different\"}}")));
        assertFalse(WledCommandVerifier.matchesSavedPreset(patch, new JSONObject()));
    }

    @Test public void acceptedPostAloneCannotBeConfirmed() throws Exception {
        AtomicInteger posts = new AtomicInteger(), reads = new AtomicInteger(), acks = new AtomicInteger();
        try {
            WledCommandVerifier.sendAndVerify("fireplace", new JSONObject("{\"on\":true}"),
                    (host,patch) -> posts.incrementAndGet(),
                    host -> { reads.incrementAndGet(); return observed("{\"on\":false}"); },
                    host -> new JSONObject(), host -> acks.incrementAndGet());
            fail("Successful POST should not be confirmed without a matching state read");
        } catch (WledCommandVerifier.UnconfirmedException ex) {
            assertTrue(ex.getMessage().contains("different state"));
        }
        assertEquals(1, posts.get());
        assertEquals(4, reads.get());
        assertEquals(1, acks.get());
    }

    @Test public void readRetryNeverResendsMutation() throws Exception {
        AtomicInteger posts = new AtomicInteger(), reads = new AtomicInteger();
        WledCommandVerifier.sendAndVerify("fireplace", new JSONObject("{\"on\":true}"),
                (host,patch) -> posts.incrementAndGet(),
                host -> observed(reads.incrementAndGet() < 3 ? "{\"on\":false}" : "{\"on\":true}"),
                host -> new JSONObject(), host -> {});
        assertEquals(1, posts.get());
        assertEquals(3, reads.get());
    }

    @Test public void postNetworkFailurePropagates() throws Exception {
        try {
            WledCommandVerifier.sendAndVerify("offline", new JSONObject("{\"on\":true}"),
                    (host,patch) -> { throw new IllegalStateException("connection refused"); },
                    host -> observed("{\"on\":true}"),
                    host -> new JSONObject(), host -> fail("Should not acknowledge failed POST"));
            fail("Failed POST must throw");
        } catch (WledCommandVerifier.UnconfirmedException wrongKind) {
            fail("Failed POST must not be mislabeled as sent but unconfirmed");
        } catch (IllegalStateException ex) {
            assertEquals("connection refused", ex.getMessage());
        }
    }
}
