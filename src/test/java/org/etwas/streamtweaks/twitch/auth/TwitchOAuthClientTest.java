package org.etwas.streamtweaks.twitch.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TwitchOAuthClientTest {

    private TwitchOAuthClient sut;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testHasValidTokenWithNoCredentialStore() {
        try (MockedConstruction<TwitchCredentialStore> mockedConstruction = mockConstruction(
                TwitchCredentialStore.class, (mock, context) -> {
                    TwitchCredentials emptyCredentials = new TwitchCredentials(null, null);
                    when(mock.loadOrCreate()).thenReturn(emptyCredentials);
                })) {

            sut = new TwitchOAuthClient();

            CompletableFuture<Boolean> result = sut.hasValidToken();

            assertFalse(result.join(), "Should return false when there is no access token.");

            TwitchCredentialStore mockStore = mockedConstruction.constructed().get(0);
            verify(mockStore, times(1)).loadOrCreate();
        }
    }
}
